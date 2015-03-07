/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.mapview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;

import org.mozilla.mozstumbler.client.ObservedLocationsReceiver;
import org.mozilla.mozstumbler.service.core.logging.ClientLog;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;
import org.mozilla.osmdroid.util.GeoPoint;
import org.mozilla.osmdroid.views.MapView;
import org.mozilla.osmdroid.views.Projection;
import org.mozilla.osmdroid.views.overlay.Overlay;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;

class ObservationPointsOverlay extends Overlay {
    private static final String LOG_TAG = LoggerUtil.makeLogTag(ObservationPointsOverlay.class);
    private static final long DRAW_TIME_MILLIS = 30; // Abort drawing after this time
    private static final int TIME_CHECK_MULTIPLE = 100; // Check the time after drawing this many
    final DevicePixelConverter mConvertPx;
    private final Paint mRedPaint = new Paint();
    private final Paint mGreenPaint = new Paint();
    private final Paint mCellPaint = new Paint();
    private final Paint mWifiPaint = new Paint();
    private final Paint mBlackStrokePaint = new Paint();
    private final Paint mBlackStrokePaintThin = new Paint();
    private final Paint mBlackMLSLinePaint = new Paint();
    private final int mSize3px;
    public boolean mOnMapShowMLS;
    LinkedHashMap<Integer, ObservationPoint> mHashedGrid;
    private Point mHashedGridAnchorPoint = new Point(0, 0);

    ObservationPointsOverlay(Context ctx) {
        super(ctx);
        mConvertPx = new DevicePixelConverter(ctx);

        mGreenPaint.setColor(Color.GREEN);
        mGreenPaint.setStyle(Paint.Style.FILL);

        mRedPaint.setColor(Color.RED);
        mRedPaint.setStyle(Paint.Style.FILL);

        mBlackStrokePaint.setColor(Color.BLACK);
        mBlackStrokePaint.setStyle(Paint.Style.STROKE);
        mBlackStrokePaint.setStrokeWidth(mConvertPx.pxToDp(2));

        mBlackMLSLinePaint.setARGB(160, 0, 0, 0);
        mBlackMLSLinePaint.setStyle(Paint.Style.STROKE);
        mBlackMLSLinePaint.setStrokeWidth(mConvertPx.pxToDp(1));

        mBlackStrokePaintThin.setColor(Color.BLACK);
        mBlackStrokePaintThin.setStyle(Paint.Style.STROKE);

        mCellPaint.setColor(Color.BLUE);
        mCellPaint.setStyle(Paint.Style.STROKE);
        mCellPaint.setStrokeWidth(mConvertPx.pxToDp(2.5f));

        mWifiPaint.setARGB(255, 160, 0, 180);
        mWifiPaint.setStyle(Paint.Style.STROKE);
        mWifiPaint.setStrokeWidth(mConvertPx.pxToDp(2.5f));

        mSize3px = mConvertPx.pxToDp(3f);
    }

    void update(ObservationPoint obsPoint, MapView mapView, boolean isMlsPointUpdate) {
        final Projection pj = mapView.getProjection();
        GeoPoint geoPoint = (isMlsPointUpdate) ? obsPoint.pointMLS : obsPoint.pointGPS;
        if (geoPoint == null) {
            ClientLog.w(LOG_TAG, "Caller error: geoPoint is null");
            return;
        }
        final Point point = pj.toPixels(geoPoint, null);

        if (!isMlsPointUpdate) {
            // add to hashed grid
            addToGridHash(obsPoint, point, new Point(mapView.getScrollX(), mapView.getScrollY()));
        }

        mapView.postInvalidate();
    }

    private void drawDot(Canvas c, Point p, float radiusInnerRing, Paint fillPaint, Paint strokePaint) {
        c.drawCircle(p.x, p.y, radiusInnerRing, fillPaint);
        c.drawCircle(p.x, p.y, radiusInnerRing, strokePaint);
    }

    private void drawCellScan(Canvas c, Point p) {
        final int size = mSize3px;
        RectF r = new RectF(p.x - size, p.y - size, p.x + size, p.y + size);
        c.drawRoundRect(r, 1f, 1f, mCellPaint);
    }

    private void drawWifiScan(Canvas c, Point p) {
        final int size = mSize3px;
        c.drawCircle(p.x, p.y, size, mWifiPaint);
    }

    private void addToGridHash(ObservationPoint obsPoint, Point screenPoint, Point currentScroll) {
        int hash = hashedGridPoint(screenPoint.x, screenPoint.y, currentScroll.x, currentScroll.y);
        ObservationPoint gp = mHashedGrid.get(hash);
        if (gp == null || toTypeBitField(obsPoint) > toTypeBitField(gp)) {
            mHashedGrid.put(hash, obsPoint);
        }
    }

    public void zoomChanged(MapView mapView) {
        mHashedGrid = new LinkedHashMap<Integer, ObservationPoint>();
        mHashedGridAnchorPoint = new Point(mapView.getScrollX(), mapView.getScrollY());
        final Projection pj = mapView.getProjection();
        LinkedList<ObservationPoint> points = ObservedLocationsReceiver.getInstance().getObservationPoints();
        final Iterator<ObservationPoint> i = points.iterator();
        final Point gps = new Point();
        ObservationPoint point;
        Point zero = new Point(0, 0);
        while (i.hasNext()) {
            point = i.next();
            pj.toPixels(point.pointGPS, gps);
            addToGridHash(point, gps, zero);
        }
    }

    private int hashedGridPoint(int x, int y, int scrollX, int scrollY) {
        x = (int) Math.round((x - mHashedGridAnchorPoint.x + scrollX) / (mSize3px * 2.0));
        y = (int) Math.round((y - mHashedGridAnchorPoint.y + scrollY) / (mSize3px * 2.0));
        return x * 10000 + y;
    }

    private int toTypeBitField(ObservationPoint point) {
        int wifiBit = point.mWifiCount > 0 ? 2 : 0;
        int cellBit = point.mCellCount > 0 ? 1 : 0;
        return cellBit | wifiBit;
    }

    protected void draw(Canvas c, MapView osmv, boolean shadow) {
        final long endTime = SystemClock.uptimeMillis() + DRAW_TIME_MILLIS;
        LinkedList<ObservationPoint> points = ObservedLocationsReceiver.getInstance().getObservationPoints();
        if (shadow || points.size() < 1) {
            return;
        }

        final Projection pj = osmv.getProjection();
        final float radiusInnerRing = mSize3px;

        int count = 0;
        // The overlay occupies the entire screen, so this returns the screen (0,0,w,h).
        Rect clip = c.getClipBounds();

        if (mHashedGrid == null || mHashedGrid.size() < 1) {
            return;
        }

        final Point gps = new Point();

        for (HashMap.Entry<Integer, ObservationPoint> entry : mHashedGrid.entrySet()) {
            ObservationPoint point = entry.getValue();
            pj.toPixels(point.pointGPS, gps);

            if (!clip.contains(gps.x, gps.y)) {
                continue;
            }

            boolean hasWifiScan = point.mWifiCount > 0;
            boolean hasCellScan = point.mCellCount > 0;

            if (hasWifiScan && !hasCellScan) {
                drawWifiScan(c, gps);
            } else if (hasCellScan && !hasWifiScan) {
                drawCellScan(c, gps);
            } else {
                drawDot(c, gps, radiusInnerRing, mGreenPaint, mBlackStrokePaint);
            }

            if ((++count % TIME_CHECK_MULTIPLE == 0) && (SystemClock.uptimeMillis() > endTime)) {
                break;
            }
        }

        if (!mOnMapShowMLS) {
            return;
        }

        // Draw as a 2nd layer over the observation points
        final Point mls = new Point();
        for (HashMap.Entry<Integer, ObservationPoint> entry : mHashedGrid.entrySet()) {
            ObservationPoint point = entry.getValue();
            if (point.pointMLS != null) {
                pj.toPixels(point.pointGPS, gps);
                pj.toPixels(point.pointMLS, mls);
                drawDot(c, mls, radiusInnerRing - 1, mRedPaint, mBlackStrokePaintThin);
                c.drawLine(gps.x, gps.y, mls.x, mls.y, mBlackMLSLinePaint);
            }

            if ((++count % TIME_CHECK_MULTIPLE == 0) && (SystemClock.uptimeMillis() > endTime)) {
                break;
            }
        }
    }
}
