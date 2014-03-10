package org.mozilla.mozstumbler;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.wifi.ScanResult;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.widget.Toast;

import java.lang.Void;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.mozilla.mozstumbler.communicator.Searcher;
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
import org.osmdroid.views.overlay.TilesOverlay;

public final class MapActivity extends Activity {
    private static final String LOGTAG = MapActivity.class.getName();

    private static final String STATUS_OK           = "ok";
    private static final String STATUS_NOT_FOUND    = "not_found";
    private static final String STATUS_FAILED       = "failed";
    private static final String COVERAGE_URL        = "https://location.services.mozilla.com/tiles/";

    private MapView mMap;

    private ReporterBroadcastReceiver mReceiver;

    // TODO add cell data
    private List<ScanResult> mWifiData;

    private class ReporterBroadcastReceiver extends BroadcastReceiver {
        private boolean mDone;

        @Override
        public void onReceive(Context context, Intent intent) {
            if (mDone) {
                return;
            }

            String action = intent.getAction();
            if (!action.equals(ScannerService.MESSAGE_TOPIC)) {
                Log.e(LOGTAG, "Received an unknown intent: " + action);
                return;
            }

            String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);
            if (!WifiScanner.WIFI_SCANNER_EXTRA_SUBJECT.equals(subject)) {
                // might be another scanner
                return;
            }

            mWifiData = intent.getParcelableArrayListExtra(WifiScanner.WIFI_SCANNER_ARG_SCAN_RESULTS);
            new GetLocationAndMapItTask().execute("");
            mDone = true;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        mWifiData = Collections.emptyList();

        mMap = (MapView) this.findViewById(R.id.map);
        mMap.setTileSource(getTileSource());
        mMap.setBuiltInZoomControls(true);
        mMap.setMultiTouchControls(true);

        TilesOverlay coverageTilesOverlay = CoverageTilesOverlay(this);
        mMap.getOverlays().add(coverageTilesOverlay);

        mReceiver = new ReporterBroadcastReceiver();
        registerReceiver(mReceiver, new IntentFilter(ScannerService.MESSAGE_TOPIC));

        mMap.getController().setZoom(2);

        Log.d(LOGTAG, "onCreate");
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

    private void positionMapAt(float lat, float lon) {
        GeoPoint point = new GeoPoint(lat, lon);
        mMap.getController().setZoom(16);
        mMap.getController().animateTo(point);
        mMap.getOverlays().add(getMapMarker(point)); // You are here!
        mMap.invalidate();
    }

    private ItemizedOverlay<OverlayItem> getMapMarker(GeoPoint point) {
        ArrayList<OverlayItem> items = new ArrayList<OverlayItem>();
        items.add(new OverlayItem(null, null, point));
        return new ItemizedOverlayWithFocus<OverlayItem>(
            getApplicationContext(),
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
        Intent i = new Intent(ScannerService.MESSAGE_TOPIC);
        i.putExtra(Intent.EXTRA_SUBJECT, "Scanner");
        i.putExtra("enable", 1);
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return false;
    }

    private final class GetLocationAndMapItTask extends AsyncTask<String, Void, String> {
        private String mStatus="";
        private float mLat=0;
        private float mLon=0;

        @Override
        public String doInBackground(String... params) {
            Log.d(LOGTAG, "requesting location...");

            JSONObject wrapper;
            try {
                wrapper = new JSONObject("{}");
                JSONArray wifiData = new JSONArray();
                for (ScanResult result : mWifiData) {
                    JSONObject item = new JSONObject();
                    item.put("key", BSSIDBlockList.canonicalizeBSSID(result.BSSID));
                    item.put("frequency", result.frequency);
                    item.put("signal", result.level);
                    wifiData.put(item);
                }
                wrapper.put("wifi", wifiData);
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
                positionMapAt(mLat, mLon);
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
        }
    }
}
