/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.mapview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.mozstumbler.BuildConfig;
import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.ClientPrefs;
import org.mozilla.mozstumbler.client.ClientStumblerService;
import org.mozilla.mozstumbler.client.MainApp;
import org.mozilla.mozstumbler.client.ObservedLocationsReceiver;
import org.mozilla.mozstumbler.client.mapview.tiles.AbstractMapOverlay;
import org.mozilla.mozstumbler.client.mapview.tiles.CoverageOverlay;
import org.mozilla.mozstumbler.client.mapview.tiles.LowResMapOverlay;
import org.mozilla.mozstumbler.client.navdrawer.MetricsView;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.core.http.HttpUtil;
import org.mozilla.mozstumbler.service.core.http.IHttpUtil;
import org.osmdroid.ResourceProxy;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.events.DelayedMapListener;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.BitmapPool;
import org.osmdroid.tileprovider.MapTile;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public final class MapFragment extends android.support.v4.app.Fragment
    implements MetricsView.IMapLayerToggleListener {

    public enum NoMapAvailableMessage { eHideNoMapMessage, eNoMapDueToNoAccessibleStorage, eNoMapDueToNoInternet }

    private static final String LOG_TAG = AppGlobals.LOG_PREFIX + MapFragment.class.getSimpleName();

    private static final String COVERAGE_REDIRECT_URL = "https://location.services.mozilla.com/map.json";
    private static String sCoverageUrl = null;
    private static int sGPSColor;
    private static final String ZOOM_KEY = "zoom";
    private static final int DEFAULT_ZOOM = 13;
    private static final int DEFAULT_ZOOM_AFTER_FIX = 16;
    private static final String LAT_KEY = "latitude";
    private static final String LON_KEY = "longitude";
    private static final int HIGH_ZOOM_THRESHOLD = 14;

    private MapView mMap;
    private AccuracyCircleOverlay mAccuracyOverlay;
    private boolean mFirstLocationFix;
    private boolean mUserPanning = false;
    private final Timer mGetUrl = new Timer();
    private ObservationPointsOverlay mObservationPointsOverlay;
    private GPSListener mGPSListener;
    private LowResMapOverlay mLowResMapOverlayHighZoom;
    private LowResMapOverlay mLowResMapOverlayLowZoom;
    private Overlay mCoverageTilesOverlayLowZoom;
    private Overlay mCoverageTilesOverlayHighZoom;
    private ITileSource mHighResMapSource;
    private View mRootView;
    private HighLowBandwidthReceiver mHighLowBandwidthChecker;

    // Used to blank the high-res tile source when adding a low-res overlay
    private class BlankTileSource extends OnlineTileSourceBase {
        BlankTileSource() {
            super("fake", ResourceProxy.string.mapquest_aerial /* arbitrary value */,
                    AbstractMapOverlay.MIN_ZOOM_LEVEL_OF_MAP,
                    AbstractMapOverlay.MAX_ZOOM_LEVEL_OF_MAP, AbstractMapOverlay.TILE_PIXEL_SIZE,
                    "", new String[] {""});
        }
        @Override
        public String getTileURLString(MapTile aTile) {
            return null;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        mRootView = inflater.inflate(R.layout.activity_map, container, false);

        showMapNotAvailableMessage(NoMapAvailableMessage.eHideNoMapMessage);

        final ImageButton centerMe = (ImageButton) mRootView.findViewById(R.id.my_location_button);
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
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    centerMe.setBackgroundResource(R.drawable.ic_mylocation_click_android_assets);

                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    v.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            centerMe.setBackgroundResource(R.drawable.ic_mylocation_android_assets);
                        }
                    }, 200);
                }
                return false;
            }
        });

        mMap = (MapView) mRootView.findViewById(R.id.map);
        mMap.setBuiltInZoomControls(true);
        mMap.setMultiTouchControls(true);

        listenForPanning(mMap);

        sGPSColor = getResources().getColor(R.color.gps_track);

        mFirstLocationFix = true;
        int zoomLevel = DEFAULT_ZOOM;
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
        mMap.setMinZoomLevel(AbstractMapOverlay.MIN_ZOOM_LEVEL_OF_MAP);

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

        mAccuracyOverlay = new AccuracyCircleOverlay(mRootView.getContext(), sGPSColor);
        mMap.getOverlays().add(mAccuracyOverlay);

        mObservationPointsOverlay = new ObservationPointsOverlay(mRootView.getContext(), mMap);
        mMap.getOverlays().add(mObservationPointsOverlay);

        ObservedLocationsReceiver observer = ObservedLocationsReceiver.getInstance();
        observer.setMapActivity(this);

        initTextView(R.id.text_cells_visible);
        initTextView(R.id.text_wifis_visible);
        initTextView(R.id.text_observation_count);

        showCopyright();

        mMap.setMapListener(new DelayedMapListener(new MapListener() {
            @Override
            public boolean onZoom(final ZoomEvent e) {
                // This is key to no-wifi (low-res) tile functions, that
                // when the zoom level changes, we check to see if the
                // low-zoom or high-zoom should be shown
                int z = e.getZoomLevel();
                updateOverlayBaseLayer(z);
                updateOverlayCoverageLayer(z);
                return true;
            }

            @Override
            public boolean onScroll(final ScrollEvent e) {
                return true;
            }
        }, 0));

        return mRootView;
    }

    MainApp getApplication() {
        return (MainApp) getActivity().getApplication();
    }

    private Runnable mCoverageUrlQuery = new Runnable() {
        @Override
        public void run() {
            // @TODO: we do a similar "read from URL" in Updater, AbstractCommunicator, make one function for this
            if (sCoverageUrl == null) {
                mGetUrl.schedule(new TimerTask() {
                    @Override
                    public void run() {

                        IHttpUtil httpUtil = new HttpUtil();

                        java.util.Scanner scanner;
                        try {
                            scanner = new java.util.Scanner(httpUtil.getUrlAsStream(COVERAGE_REDIRECT_URL), "UTF-8");
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
            } else if (mCoverageTilesOverlayLowZoom == null) {
                initCoverageTiles(sCoverageUrl);
                updateOverlayCoverageLayer(mMap.getZoomLevel());
            }
        }
    };

    private void initCoverageTiles(String coverageUrl) {
        mCoverageTilesOverlayLowZoom = new CoverageOverlay(CoverageOverlay.LOW_ZOOM,
                mRootView.getContext(), coverageUrl, mMap);
        mCoverageTilesOverlayHighZoom = new CoverageOverlay(CoverageOverlay.HIGH_ZOOM,
                mRootView.getContext(), coverageUrl, mMap);
    }

    //
    // This determines which level of detail of tile layer is shown.
    //
    private boolean isHighZoom(int zoomLevel) {
        return zoomLevel > HIGH_ZOOM_THRESHOLD;
    }

    // If the map is not in low res mode, return.
    // Otherwise, set the low res overlay based on the current zoom level, and set it to a
    // lower resolution than the zoom level of the map (trick it into showing lower resolution
    // tiles than the map normally would at a given zoom level)
    private void updateOverlayBaseLayer(int zoomLevel) {
        if (mLowResMapOverlayHighZoom == null || mLowResMapOverlayLowZoom == null) {
            return;
        }
        final List<Overlay> overlays = mMap.getOverlays();
        final Overlay overlayRemoved = (!isHighZoom(zoomLevel))? mLowResMapOverlayHighZoom : mLowResMapOverlayLowZoom;
        final Overlay overlayAdded = (isHighZoom(zoomLevel))? mLowResMapOverlayHighZoom : mLowResMapOverlayLowZoom;
        if (overlays.indexOf(overlayRemoved) > -1) {
            overlays.remove(overlayRemoved);
        }
        if (overlays.indexOf(overlayAdded) == -1) {
            overlays.add(0, overlayAdded);
            mMap.invalidate();
        }
    }

    // The MLS coverage follows the same logic as the lower resolution map overlay, in that
    // when at low zoom level, show even lower resolution tiles. The MLS coverage is not
    // dependant on the isHighBandwidth for this behaviour, it always does this.
    private void updateOverlayCoverageLayer(int zoomLevel) {
        if (mCoverageTilesOverlayLowZoom == null || mCoverageTilesOverlayHighZoom == null) {
            return;
        }

        final List<Overlay> overlays = mMap.getOverlays();
        int idx = 0;
        if (overlays.indexOf(mLowResMapOverlayHighZoom) > -1 || overlays.indexOf(mLowResMapOverlayLowZoom) > -1 ) {
           idx = 1;
        }

        final Overlay overlayRemoved = (!isHighZoom(zoomLevel))? mCoverageTilesOverlayHighZoom : mCoverageTilesOverlayLowZoom;
        final Overlay overlayAdded = (isHighZoom(zoomLevel))? mCoverageTilesOverlayHighZoom : mCoverageTilesOverlayLowZoom;
        if (overlays.indexOf(overlayRemoved) > -1) {
            overlays.remove(overlayRemoved);
        }
        if (overlays.indexOf(overlayAdded) == -1) {
            overlays.add(idx, overlayAdded);
            mMap.invalidate();
        }
    }



    // use this to track whether to show a toast
    private static Boolean sIsHighBandwidth;

    // Unfortunately, just showing low/high detail isn't enough data reduction.
    // To handle the case where the user zooms out to show a large area when in low bandwidth mode,
    // we need an additional "LowZoom" overlay. So in low bandwidth mode, you will see
    // that based on the current zoom level of the map, we show "HighZoom" or "LowZoom" overlays.
    private void setHighBandwidthMap(boolean isHighBandwidth) {
        if (ClientPrefs.getInstance().isForcedLowBandwidthTiles()) {
            isHighBandwidth = false;
        }

        boolean isMLSTileStore = (BuildConfig.TILE_SERVER_URL != null);

        boolean showToast = false;
        if (sIsHighBandwidth != null) {
            showToast = sIsHighBandwidth.booleanValue() != isHighBandwidth;
        }
        sIsHighBandwidth = new Boolean(isHighBandwidth);

        if (isHighBandwidth) {
            if (mLowResMapOverlayHighZoom == null && mMap.getTileProvider().getTileSource() == mHighResMapSource) {
                // already have set this tile source
                return;
            } else  {
                if (mLowResMapOverlayHighZoom != null) {
                    removeLayer(mLowResMapOverlayHighZoom);
                    removeLayer(mLowResMapOverlayLowZoom);

                    mLowResMapOverlayLowZoom = null;
                    mLowResMapOverlayHighZoom = null;

                    if (showToast) {
                        Toast.makeText(this.getActivity(), R.string.switch_to_high_res_tile, Toast.LENGTH_SHORT).show();
                    }
                }
            }

            if (!isMLSTileStore) {
                mHighResMapSource = TileSourceFactory.MAPQUESTOSM;
            } else {
                mHighResMapSource = new XYTileSource("MozStumbler Tile Store",
                        null, 1, AbstractMapOverlay.MAX_ZOOM_LEVEL_OF_MAP,
                        AbstractMapOverlay.TILE_PIXEL_SIZE,
                        AbstractMapOverlay.FILE_TYPE_SUFFIX_PNG,
                        new String[]{BuildConfig.TILE_SERVER_URL});
            }
            mMap.setTileSource(mHighResMapSource);
        } else {
            if (mLowResMapOverlayHighZoom != null) {
                // already set this
                return;
            }
            mMap.setTileSource(new BlankTileSource());
            if (mLowResMapOverlayHighZoom == null) {
                mLowResMapOverlayLowZoom = new LowResMapOverlay(LowResMapOverlay.LOW_ZOOM,
                        this.getActivity(), isMLSTileStore, mMap);
                mLowResMapOverlayHighZoom = new LowResMapOverlay(LowResMapOverlay.HIGH_ZOOM,
                        this.getActivity(), isMLSTileStore, mMap);

                updateOverlayBaseLayer(mMap.getZoomLevel());
            }

            if (showToast) {
                Toast.makeText(this.getActivity(), R.string.switch_to_low_res_tile, Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void mapNetworkConnectionChanged() {

        FragmentActivity activity = getActivity();

        // Amazingly, getActivity() while you're in a fragment
        // can return NULL.  Lesson learned - never trust android framework.
        // https://github.com/mozilla/MozStumbler/issues/981
        if (activity == null) {
            return;
        }

        if (activity.getFilesDir() == null) {
            // Not the ideal spot for this check perhaps, but there is no point in checking
            // the network when storage is not available.
            showMapNotAvailableMessage(NoMapAvailableMessage.eNoMapDueToNoAccessibleStorage);
            return;
        }

        mMap.post(mCoverageUrlQuery);

        final ConnectivityManager cm = (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo info = cm.getActiveNetworkInfo();
        final boolean hasNetwork = (info != null) && info.isConnected();
        final boolean hasWifi = (info != null) && (info.getType() == ConnectivityManager.TYPE_WIFI);

        if (!hasNetwork) {
            showMapNotAvailableMessage(NoMapAvailableMessage.eNoMapDueToNoInternet);
            return;
        }

        showMapNotAvailableMessage(NoMapAvailableMessage.eHideNoMapMessage);
        setHighBandwidthMap(hasWifi);
    }

    private void setStartStopMenuState(MenuItem menuItem, boolean scanning) {
        if (scanning) {
            menuItem.setIcon(android.R.drawable.ic_media_pause);
            menuItem.setTitle(R.string.stop_scanning);
        } else {
            menuItem.setIcon(android.R.drawable.ic_media_play);
            menuItem.setTitle(R.string.start_scanning);
        }

        dimToolbar();
    }

    @SuppressLint("NewApi")
    public void dimToolbar() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            return;
        }
        View v = mRootView.findViewById(R.id.status_toolbar_layout);

        final ClientStumblerService service = getApplication().getService();
        float alpha = 0.5f;
        if (service != null && service.isScanning()) {
            alpha = 1.0f;
        }
        v.setAlpha(alpha);
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

    private void showCopyright() {
        TextView copyrightArea = (TextView) mRootView.findViewById(R.id.copyright_area);
        if (BuildConfig.TILE_SERVER_URL == null) {
            copyrightArea.setText("Tiles Courtesy of MapQuest\n© OpenStreetMap contributors");
        } else {
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
        if (mCoverageTilesOverlayLowZoom == null && sCoverageUrl != null) {
            initCoverageTiles(sCoverageUrl);
            updateOverlayCoverageLayer(mMap.getZoomLevel());
        }

        formatTextView(R.id.text_satellites_used, "%d", fixes);
        int icon = fixes > 0 ? R.drawable.ic_gps_receiving_flaticondotcom : R.drawable.ic_gps_no_signal_flaticondotcom;
        ((ImageView) mRootView.findViewById(R.id.fix_indicator)).setImageResource(icon);
    }

    // An overlay for the sole purpose of reporting a user swiping on the map
    private static class SwipeListeningOverlay extends Overlay {
        private static interface OnSwipeListener {
            public void onSwipe();
        }

        final OnSwipeListener mOnSwipe;

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

        ObservedLocationsReceiver observer = ObservedLocationsReceiver.getInstance();
        observer.setMapActivity(this);

        dimToolbar();

        mapNetworkConnectionChanged();

        mHighLowBandwidthChecker = new HighLowBandwidthReceiver(this);
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
        ObservedLocationsReceiver observer = ObservedLocationsReceiver.getInstance();
        observer.removeMapActivity();

        mHighLowBandwidthChecker.unregister(this.getApplication());
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

    private void removeLayer(Overlay layer) {
        if (layer == null) {
            return;
        }
        mMap.getOverlays().remove(layer);
        layer.onDetach(mMap);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(LOG_TAG, "onDestroy");

        removeLayer(mLowResMapOverlayHighZoom);
        removeLayer(mLowResMapOverlayLowZoom);
        removeLayer(mCoverageTilesOverlayHighZoom);
        removeLayer(mCoverageTilesOverlayLowZoom);

        mMap.getTileProvider().clearTileCache();
        BitmapPool.getInstance().clearBitmapPool();
    }

    private void listenForPanning(MapView map) {
        map.getOverlays().add(new SwipeListeningOverlay(getActivity().getApplicationContext(), new SwipeListeningOverlay.OnSwipeListener() {
            @Override
            public void onSwipe() {
                mUserPanning = true;
            }
        }));
    }

    public void formatTextView(int textViewId, int stringId, Object... args) {
        String str = getResources().getString(stringId);
        formatTextView(textViewId, str, args);
    }

    public void formatTextView(int textViewId, String str, Object... args) {
        TextView textView = (TextView) mRootView.findViewById(textViewId);
        str = String.format(str, args);
        textView.setText(str);
    }

    private void initTextView(int textViewId) {
        TextView textView = (TextView) mRootView.findViewById(textViewId);
        Rect bounds = new Rect();
        Paint textPaint = textView.getPaint();
        textPaint.getTextBounds("00000", 0, "00000".length(), bounds);
        int width = bounds.width();
        textView.setWidth(width);
        android.widget.LinearLayout.LayoutParams params =
                new android.widget.LinearLayout.LayoutParams(width, android.widget.LinearLayout.LayoutParams.MATCH_PARENT);
        textView.setLayoutParams(params);
        textView.setText("0");
    }

    public void newMLSPoint(ObservationPoint point) {
        mObservationPointsOverlay.update();
    }

    public void newObservationPoint(ObservationPoint point) {
        mObservationPointsOverlay.update();
    }

    @Override
    public void setShowMLS(boolean isOn) {
        mObservationPointsOverlay.mOnMapShowMLS = isOn;
        mMap.invalidate();
    }

    public void showMapNotAvailableMessage(NoMapAvailableMessage noMapAvailableMessage) {
        TextView noMapMessage = (TextView) mRootView.findViewById(R.id.message_area);
        if (noMapAvailableMessage == NoMapAvailableMessage.eHideNoMapMessage) {
            noMapMessage.setVisibility(View.INVISIBLE);
        } else {
            noMapMessage.setVisibility(View.VISIBLE);
            int resId = (noMapAvailableMessage == NoMapAvailableMessage.eNoMapDueToNoInternet)?
                    R.string.map_offline_mode : R.string.map_unavailable;
            noMapMessage.setText(resId);
        }
    }

}
