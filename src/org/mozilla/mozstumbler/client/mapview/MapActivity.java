/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.mapview;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.mozstumbler.client.ClientPrefs;
import org.mozilla.mozstumbler.client.ClientStumblerService;
import org.mozilla.mozstumbler.client.MainApp;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.BuildConfig;
import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.MainActivity;
import org.osmdroid.api.IGeoPoint;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.GPSScanner;
import org.osmdroid.tileprovider.BitmapPool;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;

public final class MapActivity extends Activity {
    private static final String LOG_TAG = AppGlobals.LOG_PREFIX + MapActivity.class.getSimpleName();

    private static final String COVERAGE_REDIRECT_URL = "https://location.services.mozilla.com/map.json";
    private static String sCoverageUrl = null;
    private static int sGPSColor;
    private static final int MENU_REFRESH = 1;
    private static final int MENU_START_STOP = 2;
    private static final String ZOOM_KEY = "zoom";
    private static final int DEFAULT_ZOOM = 8;
    private static final int DEFAULT_ZOOM_AFTER_FIX = 16;
    private static final String LAT_KEY = "latitude";
    private static final String LON_KEY = "longitude";

    private MapView mMap;
    private AccuracyCircleOverlay mAccuracyOverlay;
    private boolean mFirstLocationFix;
    private boolean mUserPanning = false;
    Timer mGetUrl = new Timer();
    Overlay mCoverageTilesOverlay = null;
    ObservationPointsOverlay mObservationPointsOverlay;
    private GPSListener mGPSListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (ClientPrefs.getInstance().getIsHardwareAccelerated() &&
            Build.VERSION.SDK_INT > 10) {
                getWindow().setFlags(
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        }

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_map);

        ImageButton centerMe = (ImageButton)this.findViewById(R.id.my_location_button);
        centerMe.setVisibility(View.INVISIBLE);
        centerMe.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mAccuracyOverlay == null || mAccuracyOverlay.getLocation() == null)
                    return;
                mMap.getController().animateTo((mAccuracyOverlay.getLocation()));
            }
        });

        centerMe.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN ) {
                    //@TODO fill this in
                    //.setImageResource(R.drawable.modeitempressed);

                }
                else if (event.getAction() == MotionEvent.ACTION_UP ) {
                    //.setImageResource(R.drawable.modeitemnormal);
                }
                return false;
            }
        });

        mMap = (MapView) this.findViewById(R.id.map);
        mMap.setTileSource(getTileSource());
        mMap.setBuiltInZoomControls(true);
        mMap.setMultiTouchControls(true);

        listenForPanning(mMap);

        sGPSColor = getResources().getColor(R.color.gps_track);

        mFirstLocationFix = true;
        int zoomLevel = DEFAULT_ZOOM; // Default to seeing the world, until we get a fix
        GeoPoint lastLoc = null;
        if (savedInstanceState != null) {
            mFirstLocationFix = false;
            zoomLevel = savedInstanceState.getInt(ZOOM_KEY, DEFAULT_ZOOM);
            if (savedInstanceState.containsKey(LAT_KEY) && savedInstanceState.containsKey(LON_KEY)) {
                final double latitude = savedInstanceState.getDouble(LAT_KEY);
                final double longitude = savedInstanceState.getDouble(LON_KEY);
                lastLoc = new GeoPoint(latitude, longitude);
            }
        } else {
            lastLoc = ClientPrefs.getInstance().getLastMapCenter();
            if (lastLoc != null) {
                zoomLevel = DEFAULT_ZOOM_AFTER_FIX;
            }
        }

        final GeoPoint loc = lastLoc;
        final int zoom = zoomLevel;
        mMap.getController().setZoom(zoom);
        mMap.getController().setCenter(loc);

        mMap.post(new Runnable() {
            @Override
            public void run() {
                // https://github.com/osmdroid/osmdroid/issues/22
                // These need a fully constructed map, which on first load seems to take a while.
                // Post with no delay does not work for me, adding an arbitrary
                // delay of 300 ms should be plenty.
                Log.d(LOG_TAG, "ZOOM " + zoom);
                mMap.getController().setZoom(zoom);
                mMap.getController().setCenter(loc);
            }
        });

        Log.d(LOG_TAG, "onCreate");

        // @TODO: we do a similar "read from URL" in Updater, AbstractCommunicator, make one function for this
        if (sCoverageUrl == null) {
            mGetUrl.schedule(new TimerTask() {
                @Override
                public void run() {
                    java.util.Scanner scanner;
                    try {
                        scanner = new java.util.Scanner(new URL(COVERAGE_REDIRECT_URL).openStream(), "UTF-8");
                    } catch (Exception ex) {
                        Log.d(LOG_TAG, ex.toString());
                        AppGlobals.guiLogInfo("Failed to get coverage url:" + ex.toString());
                        return;
                    }
                    scanner.useDelimiter("\\A");
                    String result = scanner.next();
                    try {
                        sCoverageUrl = new JSONObject(result).getString("tiles_url");
                    } catch (JSONException ex) {
                        AppGlobals.guiLogInfo("Failed to get coverage url:" + ex.toString());
                    }
                    scanner.close();
                }
            }, 0);
        } else {
            mCoverageTilesOverlay = new CoverageOverlay(this.getApplicationContext(), sCoverageUrl, mMap);
            mMap.getOverlays().add(mCoverageTilesOverlay);
        }

        mAccuracyOverlay = new AccuracyCircleOverlay(this.getApplicationContext(), sGPSColor);
        mMap.getOverlays().add(mAccuracyOverlay);

        mObservationPointsOverlay = new ObservationPointsOverlay(this, mMap);
        mMap.getOverlays().add(mObservationPointsOverlay);

        ObservedLocationsReceiver r = ObservedLocationsReceiver.getInstance(this);
        for (ObservationPoint p : r.getPoints()) {
            newObservationPoint(p);
        }
    }

    @TargetApi(11)
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem item = menu.add(Menu.NONE,MENU_REFRESH,Menu.NONE,R.string.refresh_map)
                .setIcon(R.drawable.ic_action_refresh);
        if (Build.VERSION.SDK_INT >= 11) {
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }
        MenuItem startStop = menu.add(Menu.NONE, MENU_START_STOP, Menu.NONE, R.string.start_scanning);
        if (Build.VERSION.SDK_INT >= 11) {
            startStop.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }
        boolean isScanning = ((MainApp) getApplication()).getService().isScanning();
        setStartStopMenuState(startStop, isScanning);
        return true;
    }

    private void setStartStopMenuState(MenuItem menuItem, boolean scanning) {
        if (scanning) {
            menuItem.setIcon(android.R.drawable.ic_media_pause);
            menuItem.setTitle(R.string.stop_scanning);
        } else {
            menuItem.setIcon(android.R.drawable.ic_media_play);
            menuItem.setTitle(R.string.start_scanning);
        }
    }

    private void toggleScanning(MenuItem menuItem) {
        MainApp app = ((MainApp) getApplication());
        boolean isScanning = app.getService().isScanning();
        if (isScanning) {
            app.stopScanning();
        } else {
            app.startScanning();
        }
        setStartStopMenuState(menuItem, !isScanning);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_REFRESH:
                mUserPanning = false;
                return true;
            case MENU_START_STOP:
                toggleScanning(item);
                return true;
            default:
                return false;
        }
    }

    @SuppressWarnings("ConstantConditions")
    private static OnlineTileSourceBase getTileSource() {
        if (BuildConfig.TILE_SERVER_URL == null) {
            return TileSourceFactory.MAPQUESTOSM;
        }
        return new XYTileSource("MozStumbler Tile Store",
                                null,
                                1, 20, 256,
                                ".png",
                                new String[] { BuildConfig.TILE_SERVER_URL });
    }

    void setUserPositionAt(Location location) {
        if (mAccuracyOverlay.getLocation() == null) {
            ImageButton centerMe = (ImageButton)this.findViewById(R.id.my_location_button);
            centerMe.setVisibility(View.VISIBLE);
        }

        mAccuracyOverlay.setLocation(location);

        if (mFirstLocationFix) {
            mMap.getController().setZoom(DEFAULT_ZOOM_AFTER_FIX);
            mFirstLocationFix = false;
            mMap.getController().setCenter(new GeoPoint(location));
            mUserPanning = false;
        }

        formatTextView(R.id.latitude_text, "%1$.4f", location.getLatitude());
        formatTextView(R.id.longitude_text, "%1$.4f", location.getLongitude());
    }

    void updateGPSInfo(Intent intent) {
        if  (mCoverageTilesOverlay == null && sCoverageUrl != null) {
            mCoverageTilesOverlay = new CoverageOverlay(this.getApplicationContext(), sCoverageUrl, mMap);
            mMap.getOverlays().add(mMap.getOverlays().indexOf(mAccuracyOverlay), mCoverageTilesOverlay);
        }

        final ClientStumblerService service = ((MainApp) getApplication()).getService();
        updateUI(service);
        if (GPSScanner.SUBJECT_NEW_STATUS.equals(intent.getStringExtra(Intent.EXTRA_SUBJECT))) {
            final int fixes = intent.getIntExtra(GPSScanner.NEW_STATUS_ARG_FIXES, 0);
            final int sats = intent.getIntExtra(GPSScanner.NEW_STATUS_ARG_SATS, 0);
            formatTextView(R.id.satellites_used, R.string.num_used, fixes);
            formatTextView(R.id.satellites_visible, R.string.num_visible, sats);
            int icon = fixes > 0 ? R.drawable.ic_gps_receiving : R.drawable.ic_gps;
            ((ImageView) findViewById(R.id.fix_indicator)).setImageResource(icon);
        }
    }

    // An overlay for the sole purpose of reporting a user swiping on the map
    private static class SwipeListeningOverlay extends Overlay {
        private static interface OnSwipeListener {
            public void onSwipe();
        }

        OnSwipeListener mOnSwipe;
        SwipeListeningOverlay(Context ctx, OnSwipeListener onSwipe) {
            super(ctx);
            mOnSwipe = onSwipe;
        }

        @Override
        protected void draw(Canvas c, MapView osmv, boolean shadow) {
            // Nothing to draw
        }

        @Override
        public boolean onTouchEvent(final MotionEvent event, final MapView mapView) {
            if (mOnSwipe != null && event.getAction() == MotionEvent.ACTION_MOVE) {
                mOnSwipe.onSwipe();
            }
            return false;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        mGPSListener = new GPSListener(this);
        ObservedLocationsReceiver.getInstance(this);

        Log.d(LOG_TAG, "onStart");
    }

    private void saveStateToPrefs() {
        IGeoPoint center = mMap.getMapCenter();
        ClientPrefs.getInstance().setLastMapCenter(center);
    }
    
    @Override
    protected void onStop() {
        super.onStop();

        Log.d(LOG_TAG, "onStop");

        mGPSListener.removeListener();
        ObservedLocationsReceiver.getInstance(this).removeMapActivity();
    }

    @Override
    protected void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
        bundle.putInt(ZOOM_KEY, mMap.getZoomLevel());
        IGeoPoint center = mMap.getMapCenter();
        bundle.putDouble(LON_KEY, center.getLongitude());
        bundle.putDouble(LAT_KEY, center.getLatitude());
        saveStateToPrefs();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(LOG_TAG, "onPause");
        saveStateToPrefs();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(LOG_TAG, "onDestroy");

        mMap.getTileProvider().clearTileCache();
        BitmapPool.getInstance().clearBitmapPool();
    }


    private void updateUI(ClientStumblerService service) {
        formatTextView(R.id.cell_info_text, R.string.cells_info, service.getCurrentCellInfoCount(),
                service.getCellInfoCount());
        formatTextView(R.id.wifi_info_text, R.string.wifi_info, service.getVisibleAPCount(),
                service.getAPCount());
    }

    private void listenForPanning(MapView map) {
        map.getOverlays().add(new SwipeListeningOverlay(this.getApplicationContext(), new SwipeListeningOverlay.OnSwipeListener() {
            @Override
            public void onSwipe() {
                mUserPanning = true;
            }
        }));
    }

    boolean isValidLocation(double latitude, double longitude) {
        return Math.abs(latitude) > 0.0001 && Math.abs(longitude) > 0.0001;
    }

    boolean isValidLocation(Location location) {
        return isValidLocation(location.getLatitude(), location.getLongitude());
    }

    private void formatTextView(int textViewId, int stringId, Object... args) {
        String str = getResources().getString(stringId);
        formatTextView(textViewId, str, args);
    }

    private void formatTextView(int textViewId, String str, Object... args) {
        TextView textView = (TextView) findViewById(textViewId);
        str = String.format(str, args);
        textView.setText(str);
    }

    public void newMLSPoint(ObservationPoint point) {
        mObservationPointsOverlay.update();
    }

    public void newObservationPoint(ObservationPoint point) {
        mObservationPointsOverlay.add(point);
        mObservationPointsOverlay.update();
    }
}
