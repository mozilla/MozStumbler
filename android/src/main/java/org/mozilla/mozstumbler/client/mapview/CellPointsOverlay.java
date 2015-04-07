package org.mozilla.mozstumbler.client.mapview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
import android.util.Log;
import android.view.MotionEvent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.mozstumbler.client.util.CellDataRequester;
import org.mozilla.mozstumbler.service.utils.LocationAdapter;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;
import org.mozilla.osmdroid.util.GeoPoint;
import org.mozilla.osmdroid.views.MapView;
import org.mozilla.osmdroid.views.Projection;
import org.mozilla.osmdroid.views.overlay.Overlay;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class CellPointsOverlay extends Overlay implements CellDataRequester.Callback {
    private static final String LOG_TAG = LoggerUtil.makeLogTag(CellPointsOverlay.class);
    private final Paint mBlackStrokePaint = new Paint();
    final DevicePixelConverter mConvertPx;

    final CellDataRequester mCellDataRequester;
    private final MapView mMapView;

    Map<String, float[]> cachedPixels = new HashMap<String, float[]>();
    GeoPoint shiftReferenceGeoPoint;
    PointF shiftReferenceScreenPoint;

    CellPointsOverlay(Context ctx, MapView mapView) {
        super(ctx);

        mMapView = mapView;

        mCellDataRequester = new CellDataRequester(ctx, this);
        mConvertPx = new DevicePixelConverter(ctx);

        mBlackStrokePaint.setColor(Color.BLACK);
        mBlackStrokePaint.setStyle(Paint.Style.STROKE);
        mBlackStrokePaint.setStrokeWidth(mConvertPx.pxToDp(2));
    }

    public void gotData() {
        mMapView.invalidate();
    }

    public void zoomChanged(MapView mapView) {
        cachedPixels.clear();
        mCellDataRequester.get(mapView);
        shiftReferenceGeoPoint = null;
    }

    // Draw will handle the scrolling. Because the MapView has new info about its
    // viewport, we can use that to scroll the cachedPixels to a new location
    protected void draw(Canvas c, MapView osmv, boolean shadow) {
        final Projection pj = osmv.getProjection();

        PointF shift = null;
        if (shiftReferenceGeoPoint != null) {
            Point newPoint = pj.toPixels(shiftReferenceGeoPoint, null);
            shift = new PointF(newPoint.x - shiftReferenceScreenPoint.x,
                    newPoint.y - shiftReferenceScreenPoint.y);
            shiftReferenceScreenPoint = new PointF(newPoint);
        }

        // Scroll the cached pixels
        Iterator<String> it = cachedPixels.keySet().iterator();
        while (shift != null && it.hasNext()) {
            float[] f = cachedPixels.get(it.next());
            for (int i = 0; i < f.length; i += 2) {
                f[i] += shift.x;
                f[i + 1] += shift.y;
            }
        }

        Map<String, JSONArray> gridsToDraw = mCellDataRequester.getData(osmv);
        if (gridsToDraw == null) {
            return;
        }

        for (String key : gridsToDraw.keySet()) {
            JSONArray json = gridsToDraw.get(key);
            if (cachedPixels.containsKey(key)) {
                continue;
            }

            float[] floats = new float[json.length() * 2];
            cachedPixels.put(key, floats);

            GeoPoint geoPoint = new GeoPoint(0, 0);
            Point screenPoint = new Point(0, 0);

            for(int i = 0; i < json.length(); i++){
                float lat, lng;
                try {
                    JSONObject object = json.getJSONObject(i);

                    // this is slow!
                    lat = LocationAdapter.getFloat(object, "lat", false);
                    lng = Float.valueOf(object.get(object.names().getString(0)).toString());
                } catch (JSONException ex) {
                    Log.d(LOG_TAG, ex.toString());
                    break;
                }

                geoPoint.setCoordsE6((int)(lat * 1E6), (int)(lng * 1E6));
                pj.toPixels(geoPoint, screenPoint);
                floats[i * 2] = screenPoint.x;
                floats[i * 2 + 1] = screenPoint.y;
            }
        }

        boolean firstTimeInitAnchor = true;
        for (String key : gridsToDraw.keySet()) {
            if (firstTimeInitAnchor) {
                firstTimeInitAnchor = false;
                float[] floats = cachedPixels.get(key);
                if (floats.length > 1) {
                    shiftReferenceScreenPoint = new PointF(floats[0], floats[1]);
                    shiftReferenceGeoPoint = new GeoPoint(0, 0);
                    pj.fromPixels((int)floats[0], (int)floats[1], shiftReferenceGeoPoint);
                }
            }
            c.drawPoints(cachedPixels.get(key), mBlackStrokePaint);
        }


    }
}
