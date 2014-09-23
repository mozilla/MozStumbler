/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.mapview;

import android.content.Context;
import android.graphics.Canvas;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.mozstumbler.BuildConfig;
import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.ClientPrefs;
import org.mozilla.mozstumbler.client.ClientStumblerService;
import org.mozilla.mozstumbler.client.MainApp;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.tileprovider.BitmapPool;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;

public final class MapActivity extends android.support.v4.app.Fragment {
    private static final String LOG_TAG = AppGlobals.LOG_PREFIX + MapActivity.class.getSimpleName();

    private static final String COVERAGE_REDIRECT_URL = "https://location.services.mozilla.com/map.json";
    private static String sCoverageUrl = null;
    private static int sGPSColor;
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

    private View mRootView;
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        mRootView = inflater.inflate(R.layout.activity_map, container, false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            View v = mRootView.findViewById(R.id.status_toolbar_layout);
            v.setAlpha(0.5f);
        }

        ImageButton centerMe = (ImageButton) mRootView.findViewById(R.id.my_location_button);
        centerMe.setVisibility(View.INVISIBLE);
        centerMe.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mAccuracyOverlay == null || mAccuracyOverlay.getLocation() == null)
                    return;
                mMap.getController().animateTo((mAccuracyOverlay.getLocation()));
                mUserPanning = false;
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

        mMap = (MapView) mRootView.findViewById(R.id.map);
        final OnlineTileSourceBase tileSource = getTileSource();
        showCopyright(tileSource);
        mMap.setTileSource(tileSource);
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
            mCoverageTilesOverlay = new CoverageOverlay(mRootView.getContext(), sCoverageUrl, mMap);
            mMap.getOverlays().add(mCoverageTilesOverlay);
        }

        mAccuracyOverlay = new AccuracyCircleOverlay(mRootView.getContext(), sGPSColor);
        mMap.getOverlays().add(mAccuracyOverlay);

        mObservationPointsOverlay = new ObservationPointsOverlay(mRootView.getContext(), mMap);
        mMap.getOverlays().add(mObservationPointsOverlay);

        ObservedLocationsReceiver observer = ObservedLocationsReceiver.getInstance(mRootView.getContext());
        observer.setMapActivity(this);
        observer.putAllPointsOnMap();

        return mRootView;
    }

    MainApp getApplication() {
        return (MainApp) getActivity().getApplication();
    }

    private void setStartStopMenuState(MenuItem menuItem, boolean scanning) {
        View statusIcons = mRootView.findViewById(R.id.status_toolbar_layout);
        float alpha = 1.0f;
        if (scanning) {
            menuItem.setIcon(android.R.drawable.ic_media_pause);
            menuItem.setTitle(R.string.stop_scanning);
        } else {
            alpha = 0.5f;
            menuItem.setIcon(android.R.drawable.ic_media_play);
            menuItem.setTitle(R.string.start_scanning);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB){
            statusIcons.setAlpha(alpha);
        }
    }

    public void toggleScanning(MenuItem menuItem) {
        MainApp app = getApplication();
        boolean isScanning = app.getService().isScanning();
        if (isScanning) {
            app.stopScanning();
        } else {
            app.startScanning();
        }
        setStartStopMenuState(menuItem, !isScanning);
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

    private void showCopyright(OnlineTileSourceBase tileSource) {
        TextView copyrightArea = (TextView) mRootView.findViewById(R.id.copyright_area);
        if (TileSourceFactory.MAPQUESTOSM.equals(tileSource)) {
            copyrightArea.setText("Tiles Courtesy of MapQuest\n© OpenStreetMap contributors");
        }
        else {
            copyrightArea.setText("© MapBox © OpenStreetMap contributors");
        }
    }

    void setUserPositionAt(Location location) {
        if (mAccuracyOverlay.getLocation() == null) {
            ImageButton centerMe = (ImageButton) mRootView.findViewById(R.id.my_location_button);
            centerMe.setVisibility(View.VISIBLE);
        }

        mAccuracyOverlay.setLocation(location);

        if (mFirstLocationFix) {
            mMap.getController().setZoom(DEFAULT_ZOOM_AFTER_FIX);
            mFirstLocationFix = false;
            mMap.getController().setCenter(new GeoPoint(location));
            mUserPanning = false;
        } else if (!mUserPanning) {
            mMap.getController().animateTo((mAccuracyOverlay.getLocation()));
        }

    }

    void updateGPSInfo(int satellites, int fixes) {
        // @TODO Move this code to an appropriate place
        if  (mCoverageTilesOverlay == null && sCoverageUrl != null) {
            mCoverageTilesOverlay = new CoverageOverlay(getActivity().getApplicationContext(), sCoverageUrl, mMap);
            mMap.getOverlays().add(mMap.getOverlays().indexOf(mAccuracyOverlay), mCoverageTilesOverlay);
        }

        formatTextView(R.id.text_satellites_used, "%d", fixes);
        int icon = fixes > 0 ? R.drawable.ic_gps_receiving : R.drawable.ic_gps_no_signal_black;
        ((ImageView) mRootView.findViewById(R.id.fix_indicator)).setImageResource(icon);

        final ClientStumblerService service = getApplication().getService();
        updateUI(service);
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
    public void onStart() {
        super.onStart();
        Log.d(LOG_TAG, "onStart");

        mGPSListener = new GPSListener(this);

        ObservedLocationsReceiver observer = ObservedLocationsReceiver.getInstance(getActivity().getApplicationContext());
        observer.setMapActivity(this);
        observer.putMissedPointsOnMap();
    }

    private void saveStateToPrefs() {
        IGeoPoint center = mMap.getMapCenter();
        ClientPrefs.getInstance().setLastMapCenter(center);
    }
    
    @Override
    public void onStop() {
        super.onStop();

        Log.d(LOG_TAG, "onStop");

        mGPSListener.removeListener();
        ObservedLocationsReceiver observer = ObservedLocationsReceiver.getInstance(getActivity().getApplicationContext());
        observer.removeMapActivity();
    }

    @Override
    public void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
        bundle.putInt(ZOOM_KEY, mMap.getZoomLevel());
        IGeoPoint center = mMap.getMapCenter();
        bundle.putDouble(LON_KEY, center.getLongitude());
        bundle.putDouble(LAT_KEY, center.getLatitude());
        saveStateToPrefs();
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(LOG_TAG, "onPause");
        saveStateToPrefs();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(LOG_TAG, "onDestroy");

        mMap.getTileProvider().clearTileCache();
        BitmapPool.getInstance().clearBitmapPool();
    }


    private void updateUI(ClientStumblerService service) {
//        formatTextView(R.id.cell_info_text, R.string.cells_info, service.getCurrentCellInfoCount(),
//                service.getCellInfoCount());
//        formatTextView(R.id.wifi_info_text, R.string.wifi_info, service.getVisibleAPCount(),
//                service.getAPCount());
    }

    private void listenForPanning(MapView map) {
        map.getOverlays().add(new SwipeListeningOverlay(getActivity().getApplicationContext(), new SwipeListeningOverlay.OnSwipeListener() {
            @Override
            public void onSwipe() {
                mUserPanning = true;
            }
        }));
    }

    private void formatTextView(int textViewId, int stringId, Object... args) {
        String str = getResources().getString(stringId);
        formatTextView(textViewId, str, args);
    }

    private void formatTextView(int textViewId, String str, Object... args) {
        TextView textView = (TextView) mRootView.findViewById(textViewId);
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
