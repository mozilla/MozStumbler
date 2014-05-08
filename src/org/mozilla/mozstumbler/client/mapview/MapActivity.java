package org.mozilla.mozstumbler.client.mapview;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.net.wifi.ScanResult;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.mozstumbler.blocklist.BSSIDBlockList;
import org.mozilla.mozstumbler.BuildConfig;
import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.MainActivity;
import org.mozilla.mozstumbler.service.scanners.WifiScanner;
import org.mozilla.mozstumbler.service.scanners.cellscanner.CellInfo;
import org.mozilla.mozstumbler.service.scanners.cellscanner.CellScanner;
import org.osmdroid.tileprovider.MapTileProviderBasic;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.ItemizedOverlay;
import org.osmdroid.views.overlay.ItemizedOverlayWithFocus;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.overlay.SafeDrawOverlay;
import org.osmdroid.views.overlay.TilesOverlay;
import org.osmdroid.views.safecanvas.ISafeCanvas;
import org.osmdroid.views.safecanvas.SafePaint;

public final class MapActivity extends Activity {
    private static final String LOGTAG = MapActivity.class.getName();

    private static final String STATUS_OK           = "ok";
    private static final String STATUS_NOT_FOUND    = "not_found";
    private static final String STATUS_FAILED       = "failed";
    private static final String COVERAGE_URL        = "https://location.services.mozilla.com/tiles/";
    private static final int MENU_REFRESH           = 1;
    private static final int MAX_ZOOM_NEEDED        = 17;
    private static final float ZOOM_SCALE_FACT      = -0.0002f;

    private MapView mMap;
    private AccuracyCircleOverlay mAccuracyOverlay;
    private ItemizedOverlay<OverlayItem> mPointOverlay;

    private ReporterBroadcastReceiver mReceiver;

    private List<ScanResult> mWifiData;
    private List<CellInfo> mCellData;

    private class ReporterBroadcastReceiver extends BroadcastReceiver {
        private boolean mDone;

        public void reset()
        {
            mMap.getOverlays().remove(mAccuracyOverlay);
            mMap.getOverlays().remove(mPointOverlay);
            mDone = false;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (mDone) {
                return;
            }

            String action = intent.getAction();

            if (action.equals(WifiScanner.ACTION_WIFIS_SCANNED)) {
                mWifiData = intent.getParcelableArrayListExtra(WifiScanner.ACTION_WIFIS_SCANNED_ARG_RESULTS);
            } else if (action.equals(CellScanner.ACTION_CELLS_SCANNED)) {
                mCellData = intent.getParcelableArrayListExtra(CellScanner.ACTION_CELLS_SCANNED_ARG_CELLS);
            }

            new GetLocationAndMapItTask().execute("");
            mDone = true;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_map);

        mWifiData = Collections.emptyList();

        mMap = (MapView) this.findViewById(R.id.map);
        mMap.setTileSource(getTileSource());
        mMap.setBuiltInZoomControls(true);
        mMap.setMultiTouchControls(true);

        TilesOverlay coverageTilesOverlay = CoverageTilesOverlay(this);
        mMap.getOverlays().add(coverageTilesOverlay);

        mReceiver = new ReporterBroadcastReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiScanner.ACTION_WIFIS_SCANNED);
        intentFilter.addAction(CellScanner.ACTION_CELLS_SCANNED);
        registerReceiver(mReceiver, intentFilter);

        mMap.getController().setZoom(2);

        Log.d(LOGTAG, "onCreate");
    }

    @TargetApi(11)
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem item = menu.add(Menu.NONE,MENU_REFRESH,Menu.NONE,R.string.refresh_map)
                .setIcon(R.drawable.ic_action_refresh);
        if (Build.VERSION.SDK_INT >= 11) {
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_REFRESH:
                if (mReceiver != null) {
                    mReceiver.reset();
                    setProgressBarIndeterminateVisibility(true);
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    @SuppressWarnings("ConstantConditions")
    private static OnlineTileSourceBase getTileSource() {
        if (BuildConfig.TILE_SERVER_URL == null) {
            return TileSourceFactory.DEFAULT_TILE_SOURCE;
        }
        return new XYTileSource("MozStumbler Tile Store",
                                null,
                                1, 20, 256,
                                ".png",
                                BuildConfig.TILE_SERVER_URL);
    }

    private static TilesOverlay CoverageTilesOverlay(Context context) {
        final MapTileProviderBasic coverageTileProvider = new MapTileProviderBasic(context);
        final ITileSource coverageTileSource = new XYTileSource("Mozilla Location Service Coverage Map",
                null,
                1, 13, 256,
                ".png",
                COVERAGE_URL);
        coverageTileProvider.setTileSource(coverageTileSource);
        final TilesOverlay coverageTileOverlay = new TilesOverlay(coverageTileProvider,context);
        coverageTileOverlay.setLoadingBackgroundColor(Color.TRANSPARENT);
        return coverageTileOverlay;
    }

    private void positionMapAt(float lat, float lon, float accuracy) {
        GeoPoint point = new GeoPoint(lat, lon);
        int zoomLevel = Math.round(accuracy*ZOOM_SCALE_FACT+MAX_ZOOM_NEEDED);
        if (zoomLevel < 0) {
            zoomLevel = 0;
        } else if (zoomLevel > MAX_ZOOM_NEEDED) {
            zoomLevel = MAX_ZOOM_NEEDED; // this code will only execute if accuracy is negative, which "should" never happen...
        }
        mMap.getController().setZoom(zoomLevel);
        mMap.getController().animateTo(point);
        mPointOverlay = getMapMarker(point);
        mAccuracyOverlay = new AccuracyCircleOverlay(MapActivity.this, point, accuracy);
        mMap.getOverlays().add(mPointOverlay); // You are here!
        mMap.getOverlays().add(mAccuracyOverlay);
        mMap.invalidate();
    }

    private static class AccuracyCircleOverlay extends SafeDrawOverlay {
        private GeoPoint mPoint;
        private float mAccuracy;

        public AccuracyCircleOverlay(Context ctx, GeoPoint point, float accuracy) {
            super(ctx);
            //this.mPoint = (GeoPoint) point.clone();
            this.mPoint = point;
            this.mAccuracy = accuracy;
        }

        protected void drawSafe(ISafeCanvas c, MapView osmv, boolean shadow) {
            if (shadow || mPoint == null) {
                return;
            }
            MapView.Projection pj = osmv.getProjection();
            Point center = pj.toPixels(mPoint, null);
            float radius = pj.metersToEquatorPixels(mAccuracy);
            SafePaint circle = new SafePaint();
            circle.setARGB(0, 100, 100, 255);

            // Fill
            circle.setAlpha(40);
            circle.setStyle(Paint.Style.FILL);
            c.drawCircle(center.x, center.y, radius, circle);

            // Border
            circle.setAlpha(165);
            circle.setStyle(Paint.Style.STROKE);
            c.drawCircle(center.x, center.y, radius, circle);
        }
    }

    private ItemizedOverlay<OverlayItem> getMapMarker(GeoPoint point) {
        ArrayList<OverlayItem> items = new ArrayList<OverlayItem>();
        items.add(new OverlayItem(null, null, point));
        return new ItemizedOverlayWithFocus<OverlayItem>(
            MapActivity.this,
            items,
            new ItemizedIconOverlay.OnItemGestureListener<OverlayItem>() {
                @Override
                public boolean onItemSingleTapUp(int index, OverlayItem item) { return false; }
                @Override
                public boolean onItemLongPress(int index, OverlayItem item) { return false; }
            });
    }

    @Override
    protected void onStart() {
        super.onStart();

        Context context = getApplicationContext();
        Intent i = new Intent(MainActivity.ACTION_UNPAUSE_SCANNING);
        context.sendBroadcast(i);
        Log.d(LOGTAG, "onStart");
    }

    @Override
    protected void onStop() {
        super.onStop();

        Log.d(LOGTAG, "onStop");
        mMap.getTileProvider().clearTileCache();
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
            mReceiver = null;
        }
    }

    private final class GetLocationAndMapItTask extends AsyncTask<String, Void, String> {
        private String mStatus="";
        private float mLat = 0;
        private float mLon = 0;
        private float mAccuracy = 0;

        @Override
        public String doInBackground(String... params) {
            Log.d(LOGTAG, "requesting location...");

            JSONObject wrapper;
            try {
                wrapper = new JSONObject("{}");
                if (mCellData != null) {
                    wrapper.put("radio", mCellData.get(0).getRadio());
                    JSONArray cellData = new JSONArray();
                    for (CellInfo info : mCellData) {
                        JSONObject item = info.toJSONObject();
                        cellData.put(item);
                    }
                    wrapper.put("cell", cellData);
                }
                if (mWifiData != null) {
                    JSONArray wifiData = new JSONArray();
                    for (ScanResult result : mWifiData) {
                        JSONObject item = new JSONObject();
                        item.put("key", BSSIDBlockList.canonicalizeBSSID(result.BSSID));
                        item.put("frequency", result.frequency);
                        item.put("signal", result.level);
                        wifiData.put(item);
                    }
                    wrapper.put("wifi", wifiData);
                }
            } catch (JSONException jsonex) {
                Log.w(LOGTAG, "json exception", jsonex);
                return "";
            }
            String data = wrapper.toString();
            byte[] bytes = data.getBytes();
            Searcher searcher = new Searcher(MapActivity.this);
            if (searcher.cleanSend(bytes)) {
                mStatus = searcher.getStatus();
                mLat = searcher.getLat();
                mLon = searcher.getLon();
                mAccuracy = searcher.getAccuracy();
            } else {
                mStatus = STATUS_FAILED;
            }
            searcher.close();
            Log.d(LOGTAG, "Upload status: " + mStatus);
            return mStatus;
        }

        @Override
        protected void onPostExecute(String result) {
            if (STATUS_OK.equals(mStatus)) {
                positionMapAt(mLat, mLon, mAccuracy);
            } else if (STATUS_NOT_FOUND.equals(mStatus)) {
                Toast.makeText(MapActivity.this,
                        getResources().getString(R.string.location_not_found),
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(MapActivity.this,
                        getResources().getString(R.string.location_lookup_error),
                        Toast.LENGTH_LONG).show();
                Log.e(LOGTAG, "", new IllegalStateException("mStatus=" + mStatus));
            }
            setProgressBarIndeterminateVisibility(false);
        }
    }
}
