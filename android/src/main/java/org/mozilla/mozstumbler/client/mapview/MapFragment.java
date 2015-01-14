/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.mapview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

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
import org.mozilla.mozstumbler.service.core.logging.Log;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.GPSScanner;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.osmdroid.ResourceProxy;
import org.mozilla.osmdroid.api.IGeoPoint;
import org.mozilla.osmdroid.events.DelayedMapListener;
import org.mozilla.osmdroid.events.MapListener;
import org.mozilla.osmdroid.events.ScrollEvent;
import org.mozilla.osmdroid.events.ZoomEvent;
import org.mozilla.osmdroid.tileprovider.BitmapPool;
import org.mozilla.osmdroid.tileprovider.MapTile;
import org.mozilla.osmdroid.tileprovider.tilesource.ITileSource;
import org.mozilla.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.mozilla.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.mozilla.osmdroid.tileprovider.tilesource.XYTileSource;
import org.mozilla.osmdroid.util.GeoPoint;
import org.mozilla.osmdroid.views.MapView;
import org.mozilla.osmdroid.views.overlay.Overlay;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

public class MapFragment extends android.support.v4.app.Fragment
        implements MetricsView.IMapLayerToggleListener {

    public static enum NoMapAvailableMessage { eHideNoMapMessage, eNoMapDueToNoAccessibleStorage, eNoMapDueToNoInternet }

    private static final String LOG_TAG = AppGlobals.makeLogTag(MapFragment.class.getSimpleName());

    private static final String COVERAGE_REDIRECT_URL = "https://location.services.mozilla.com/map.json";
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
    private MapLocationListener mMapLocationListener;
    private LowResMapOverlay mLowResMapOverlayHighZoom;
    private LowResMapOverlay mLowResMapOverlayLowZoom;
    private Overlay mCoverageTilesOverlayLowZoom;
    private Overlay mCoverageTilesOverlayHighZoom;
    private ITileSource mHighResMapSource;
    private View mRootView;
    private TextView mTextViewIsLowResMap;
    private HighLowBandwidthReceiver mHighLowBandwidthChecker;
    private CoverageSetup mCoverageSetup = new CoverageSetup();

    // Used to blank the high-res tile source when adding a low-res overlay
    private class BlankTileSource extends OnlineTileSourceBase {
        BlankTileSource() {
            super("fake", ResourceProxy.string.mapquest_aerial /* arbitrary value */,
                    AbstractMapOverlay.getDisplaySizeBasedMinZoomLevel(),
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

        doOnCreateView(savedInstanceState);

        return mRootView;
    }

    /*
      This method has been extracted from onCreateView so that it's easy to stub
      it out when the class is under test.
     */
    void doOnCreateView(Bundle savedInstanceState) {
        AbstractMapOverlay.setDisplayBasedMinimumZoomLevel(getApplication());
        showMapNotAvailableMessage(NoMapAvailableMessage.eHideNoMapMessage);

        hideLowResMapMessage();
        initializeMapControls();
        initializeLastLocation(savedInstanceState);
        initializeMapOverlays();
        initializeVisibleCounts();
        initializeListeners();
        showPausedDueToNoMotionMessage(getApplication().isIsScanningPausedDueToNoMotion());

        showCopyright();
    }

    private void hideLowResMapMessage() {
        mTextViewIsLowResMap = (TextView) mRootView.findViewById(R.id.low_resolution_map_message);
        mTextViewIsLowResMap.setVisibility(View.GONE);
    }

    private void initializeMapControls() {
        mMap = (MapView) mRootView.findViewById(R.id.map);
        mMap.setBuiltInZoomControls(true);
        mMap.setMultiTouchControls(true);
        mMap.setMinZoomLevel(AbstractMapOverlay.getDisplaySizeBasedMinZoomLevel());
    }

    private void initializeCenterMeListener() {
        final ImageButton centerMe = (ImageButton) mRootView.findViewById(R.id.my_location_button);
        centerMe.setVisibility(View.INVISIBLE);
        centerMe.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mAccuracyOverlay == null || mAccuracyOverlay.getLocation() == null)
                    return;
                mMap.getController().animateTo((mAccuracyOverlay.getLocation()));
                mUserPanning = false;
                centerMe.setBackgroundResource(R.drawable.ic_mylocation_android_assets);
            }
        });
    }

    private void initializeListeners() {
        initializeCenterMeListener();

        mMap.getOverlays().add(new SwipeListeningOverlay(getActivity().getApplicationContext(), new SwipeListeningOverlay.OnSwipeListener() {
            @Override
            public void onSwipe() {
                mUserPanning = true;
                setCenterButtonToNotCenteredIcon();
            }
        }));

        mMap.setMapListener(new DelayedMapListener(new MapListener() {
            @Override
            public boolean onZoom(final ZoomEvent e) {
                // This is key to no-wifi (low-res) tile functions, that
                // when the zoom level changes, we check to see if the
                // low-zoom or high-zoom should be shown
                int z = e.getZoomLevel();
                updateOverlayBaseLayer(z);
                updateOverlayCoverageLayer(z);
                mObservationPointsOverlay.zoomChanged(mMap);
                return true;
            }

            @Override
            public boolean onScroll(final ScrollEvent e) {
                return true;
            }
        }, 0));
        ObservedLocationsReceiver observer = ObservedLocationsReceiver.getInstance();
        observer.setMapActivity(this);
    }

    private void initializeMapOverlays() {
        mAccuracyOverlay = new AccuracyCircleOverlay(mRootView.getContext(), getResources().getColor(R.color.gps_track));
        mMap.getOverlays().add(mAccuracyOverlay);

        mObservationPointsOverlay = new ObservationPointsOverlay(mRootView.getContext());
        mMap.getOverlays().add(mObservationPointsOverlay);
    }

    private void initializeLastLocation(Bundle savedInstanceState) {
        mFirstLocationFix = true;
        int zoomLevel;
        GeoPoint lastLoc = null;
        if (savedInstanceState != null) {
            zoomLevel = savedInstanceState.getInt(ZOOM_KEY, DEFAULT_ZOOM);
            if (savedInstanceState.containsKey(LAT_KEY) && savedInstanceState.containsKey(LON_KEY)) {
                mFirstLocationFix = false;
                final double latitude = savedInstanceState.getDouble(LAT_KEY);
                final double longitude = savedInstanceState.getDouble(LON_KEY);
                lastLoc = new GeoPoint(latitude, longitude);
            }
        } else {
            lastLoc = ClientPrefs.getInstance(mRootView.getContext()).getLastMapCenter();
            zoomLevel = DEFAULT_ZOOM_AFTER_FIX;
            if (new GeoPoint(0, 0).equals(lastLoc)) {
                lastLoc = null;
            }
        }

        if (lastLoc != null) {
            final GeoPoint loc = lastLoc;
            final int zoom = zoomLevel;
            setCenterAndZoom(loc, zoom);

            mMap.postDelayed(new Runnable() {
                @Override
                public void run() {
                    // https://github.com/osmdroid/osmdroid/issues/22
                    // These need a fully constructed map, which on first load seems to take a while.
                    // Post with no delay does not work for me, adding an arbitrary
                    // delay of 300 ms should be plenty.
                    Log.d(LOG_TAG, "postDelayed ZOOM " + zoom);
                    setCenterAndZoom(loc, zoom);
                }
            }, 300);
        }
    }

    private void initializeVisibleCounts() {
        Configuration c = getResources().getConfiguration();
        if (c.fontScale > 1) {
            Log.d(LOG_TAG, "Large text is enabled: " + c.fontScale);
            mRootView.findViewById(R.id.text_satellites_sep).setVisibility(View.GONE);
            mRootView.findViewById(R.id.text_satellites_avail).setVisibility(View.GONE);
        } else {
            initTextView(R.id.text_satellites_avail, "00");
        }
        initTextView(R.id.text_cells_visible, "000");
        initTextView(R.id.text_wifis_visible, "000");
        initTextView(R.id.text_observation_count, "00000");
    }

    private void setCenterAndZoom(GeoPoint loc, int zoom) {
        mMap.getController().setZoom(zoom);
        mMap.getController().setCenter(loc);
    }

    MainApp getApplication() {
        return (MainApp) getActivity().getApplication();
    }

    public LocationManager getLocationManager() {
        return (LocationManager) getActivity().getApplicationContext().
                getSystemService(Context.LOCATION_SERVICE);

    }
    private static String sCoverageUrl; // Only used by CoverageSetup

    private class CoverageSetup {
        private AtomicBoolean isGetUrlAndInitCoverageRunning = new AtomicBoolean();

        private void initOnMainThread() {
            final Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    final ClientPrefs.MapTileResolutionOptions resolution =
                            ClientPrefs.getInstance(mRootView.getContext()).getMapTileResolutionType();
                    if (mCoverageTilesOverlayLowZoom != null ||  // checks if init() has already happened
                        resolution == ClientPrefs.MapTileResolutionOptions.NoMap) {
                        return;
                    }
                    initCoverageTiles(sCoverageUrl);
                    updateOverlayCoverageLayer(mMap.getZoomLevel());
                }
            };
            mMap.post(runnable);
        }

        void getUrlAndInit() {
            if (!isGetUrlAndInitCoverageRunning.compareAndSet(false, true)) {
                return;
            }

            final Runnable coverageUrlQuery = new Runnable() {
                @Override
                public void run() {
                    if (sCoverageUrl != null) {
                        initOnMainThread();
                        isGetUrlAndInitCoverageRunning.set(false);
                        return;
                    }

                    mGetUrl.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            try {
                                IHttpUtil httpUtil = (IHttpUtil) ServiceLocator.getInstance().getService(IHttpUtil.class);
                                java.util.Scanner scanner = new java.util.Scanner(httpUtil.getUrlAsStream(COVERAGE_REDIRECT_URL), "UTF-8");
                                if (scanner.hasNext()) {
                                    scanner.useDelimiter("\\A");
                                    String result = scanner.next();
                                    try {
                                        sCoverageUrl = new JSONObject(result).getString("tiles_url");
                                        removeLayer(mCoverageTilesOverlayHighZoom);
                                        removeLayer(mCoverageTilesOverlayLowZoom);
                                        mCoverageTilesOverlayHighZoom = mCoverageTilesOverlayLowZoom = null;
                                    } catch (JSONException ex) {
                                        AppGlobals.guiLogInfo("Failed to get coverage url: " + ex.toString());
                                    }
                                }
                                scanner.close();
                            } catch (Exception ex) {
                                // this will catch java.net.UnknownHostException when offline
                                if (AppGlobals.isDebug) {
                                    Log.d(LOG_TAG, ex.toString());
                                }
                            }
                            // always init coverage tiles
                            // cached tiles will be shown even if sCoverageUrl == null
                            initOnMainThread();
                            isGetUrlAndInitCoverageRunning.set(false);
                        }
                    }, 0);
                }
            };

            mMap.post(coverageUrlQuery);
        }
    }

    private void initCoverageTiles(String coverageUrl) {
        Log.i(LOG_TAG, "initCoverageTiles: " + coverageUrl);
        mCoverageTilesOverlayLowZoom = new CoverageOverlay(CoverageOverlay.LowResType.LOWER_ZOOM,
                mRootView.getContext(), coverageUrl, mMap);
        mCoverageTilesOverlayHighZoom = new CoverageOverlay(CoverageOverlay.LowResType.HIGHER_ZOOM,
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

    // Unfortunately, just showing low/high detail isn't enough data reduction.
    // To handle the case where the user zooms out to show a large area when in low bandwidth mode,
    // we need an additional "LowZoom" overlay. So in low bandwidth mode, you will see
    // that based on the current zoom level of the map, we show "HighZoom" or "LowZoom" overlays.
    void setHighBandwidthMap(boolean isHighBandwidth) {
        final ClientPrefs prefs = ClientPrefs.getInstance(mRootView.getContext());
        if (prefs == null || getActivity() == null) {
            return;
        }

        final ClientPrefs.MapTileResolutionOptions tileType = prefs.getMapTileResolutionType();
        final int idxTileType = tileType.ordinal();
        if (idxTileType > 0) {
            if (tileType == ClientPrefs.MapTileResolutionOptions.NoMap) {
                mTextViewIsLowResMap.setVisibility(View.VISIBLE);
                mTextViewIsLowResMap.setText(getActivity().getString(R.string.map_turned_off));
                mMap.setTileSource(new BlankTileSource());
                removeLayer(mLowResMapOverlayLowZoom);
                removeLayer(mLowResMapOverlayHighZoom);
                removeLayer(mCoverageTilesOverlayLowZoom);
                removeLayer(mCoverageTilesOverlayHighZoom);
                mLowResMapOverlayHighZoom = mLowResMapOverlayLowZoom = null;
                mCoverageTilesOverlayHighZoom = mCoverageTilesOverlayLowZoom = null;
                return;
            } else if (tileType == ClientPrefs.MapTileResolutionOptions.HighRes) {
                isHighBandwidth = true;
            } else if (tileType == ClientPrefs.MapTileResolutionOptions.LowRes) {
                isHighBandwidth = false;
            }
        }

        final boolean isMLSTileStore = (BuildConfig.TILE_SERVER_URL != null);

        if (idxTileType == 0) {
            if (isHighBandwidth) {
                mTextViewIsLowResMap.setVisibility(View.GONE);
            } else {
                mTextViewIsLowResMap.setText(getActivity().getString(R.string.low_resolution_map));
                mTextViewIsLowResMap.setVisibility(View.VISIBLE);
            }
        } else {
            String[] labels = getActivity().getResources().getStringArray(R.array.map_tile_resolution_options);
            mTextViewIsLowResMap.setText(labels[idxTileType]);
            mTextViewIsLowResMap.setVisibility(View.VISIBLE);
        }

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
                }
            }

            // We've destroyed 2 layers for lowResMapOverlay
            // Force GC to cleanup underlying LRU caches in overlay
            System.gc();

            if (!isMLSTileStore) {
                mHighResMapSource = TileSourceFactory.MAPQUESTOSM;
            } else {
                mHighResMapSource = new XYTileSource("Stumbler-BaseMap-Tiles",
                        null, 1, AbstractMapOverlay.MAX_ZOOM_LEVEL_OF_MAP,
                        AbstractMapOverlay.TILE_PIXEL_SIZE,
                        AbstractMapOverlay.FILE_TYPE_SUFFIX_PNG,
                        new String[]{BuildConfig.TILE_SERVER_URL});
            }
            System.gc();
            mMap.setTileSource(mHighResMapSource);
        } else {
            if (mLowResMapOverlayHighZoom != null) {
                // already set this
                return;
            }

            // Unhooking the highres map means we should nullify it and force GC
            // to cleanup underlying LRU cache in MapSource
            mHighResMapSource = null;
            System.gc();

            mMap.setTileSource(new BlankTileSource());
            if (mLowResMapOverlayHighZoom == null) {
                mLowResMapOverlayLowZoom = new LowResMapOverlay(LowResMapOverlay.LowResType.LOWER_ZOOM,
                        this.getActivity(), isMLSTileStore, mMap);
                mLowResMapOverlayHighZoom = new LowResMapOverlay(LowResMapOverlay.LowResType.HIGHER_ZOOM,
                        this.getActivity(), isMLSTileStore, mMap);

                updateOverlayBaseLayer(mMap.getZoomLevel());
            }

        }
    }

    public void mapNetworkConnectionChanged() {

        FragmentActivity activity = getActivity();
        if (activity == null) {
            // This is only null because of roboelectric
            return;
        }

        if (activity.getFilesDir() == null) {
            // Not the ideal spot for this check perhaps, but there is no point in checking
            // the network when storage is not available.
            showMapNotAvailableMessage(NoMapAvailableMessage.eNoMapDueToNoAccessibleStorage);
            return;
        }
        getUrlAndInit();

        // Note that under test, roboelectric will return null from getActivity(), but
        // getActivity().getSystemService(...) will properly return the ShadowConnectivityManager
        final ConnectivityManager cm = (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo info = cm.getActiveNetworkInfo();
        final boolean hasNetwork = (info != null) && info.isConnected();
        final boolean hasWifi = (info != null) && (info.getType() == ConnectivityManager.TYPE_WIFI);

        NoMapAvailableMessage message = hasNetwork ? NoMapAvailableMessage.eHideNoMapMessage : NoMapAvailableMessage.eNoMapDueToNoInternet;
        showMapNotAvailableMessage(message);
        setHighBandwidthMap(hasWifi);
    }

    void getUrlAndInit() {
        mCoverageSetup.getUrlAndInit();
    }

    public void setZoomButtonsVisible(boolean visible) {
        mMap.setZoomButtonsVisible(visible);
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
        if (app.getService() == null) {
            return;
        }

        boolean isScanning = app.isScanningOrPaused();
        if (isScanning) {
            app.stopScanning();
        } else {
            app.startScanning();
        }

        dimToolbar();
    }

    private void showCopyright() {
        TextView copyrightArea = (TextView) mRootView.findViewById(R.id.copyright_area);
        if (BuildConfig.TILE_SERVER_URL == null) {
            copyrightArea.setText(getActivity().getString(R.string.map_copyright_fdroid));
        } else {
            copyrightArea.setText(getActivity().getString(R.string.map_copyright_moz));
        }
    }

    private void setCenterButtonToNotCenteredIcon() {
        ImageButton centerMe = (ImageButton) mRootView.findViewById(R.id.my_location_button);
        centerMe.setVisibility(View.VISIBLE);
        centerMe.setBackgroundResource(R.drawable.ic_mylocation_no_dot_android_assets);
    }

    void setUserPositionAt(Location location) {
        if (mAccuracyOverlay.getLocation() == null) {
            setCenterButtonToNotCenteredIcon();
        }

        mAccuracyOverlay.setLocation(location);

        if (mFirstLocationFix) {
            setCenterAndZoom(new GeoPoint(location), DEFAULT_ZOOM_AFTER_FIX);
            mFirstLocationFix = false;
            mUserPanning = false;
        } else if (!mUserPanning) {
            mMap.getController().animateTo((mAccuracyOverlay.getLocation()));
        }

    }

    void updateGPSInfo(int satellites, int fixes) {
        formatTextView(R.id.text_satellites_avail, "%d", satellites);
        formatTextView(R.id.text_satellites_used, "%d", fixes);
        // @TODO this is still not accurate
        int icon = fixes >= GPSScanner.MIN_SAT_USED_IN_FIX ? R.drawable.ic_gps_receiving_flaticondotcom : R.drawable.ic_gps_no_signal_flaticondotcom;
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
    public void onResume() {
        super.onResume();
        Log.d(LOG_TAG, "onResume");

        doOnResume();
    }

    /*
     This method has been extracted from onResume so that we can just disable the behavior when
     the class is under test
     */
    void doOnResume() {
        mMapLocationListener = new MapLocationListener(this);

        ObservedLocationsReceiver observer = ObservedLocationsReceiver.getInstance();
        observer.setMapActivity(this);

        dimToolbar();

        mapNetworkConnectionChanged();

        mHighLowBandwidthChecker = new HighLowBandwidthReceiver(this);

        ClientPrefs prefs = ClientPrefs.getInstance(getActivity().getApplicationContext());
        setShowMLS(prefs.getOnMapShowMLS());

        mObservationPointsOverlay.zoomChanged(mMap);
        mMap.postInvalidate();
    }

    private void saveStateToPrefs() {
        IGeoPoint center = mMap.getMapCenter();
        ClientPrefs.getInstance(mRootView.getContext()).setLastMapCenter(center);
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

        if (mMapLocationListener != null) {
            mMapLocationListener.removeListener();
            mMapLocationListener = null;
        }
        ObservedLocationsReceiver observer = ObservedLocationsReceiver.getInstance();
        observer.removeMapActivity();
        mHighLowBandwidthChecker.unregister(this.getApplication());
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

    public void formatTextView(int textViewId, int stringId, Object... args) {
        String str = getResources().getString(stringId);
        formatTextView(textViewId, str, args);
    }

    public void formatTextView(int textViewId, String str, Object... args) {
        TextView textView = (TextView) mRootView.findViewById(textViewId);
        str = String.format(str, args);
        textView.setText(str);
    }

    private void initTextView(int textViewId, String bound) {
        TextView textView = (TextView) mRootView.findViewById(textViewId);
        Paint textPaint = textView.getPaint();
        int width = (int) Math.ceil(textPaint.measureText(bound));
        textView.setWidth(width);
        textView.getLayoutParams().width = width;
        textView.setText("0");
    }

    public void newMLSPoint(ObservationPoint point) {
        mObservationPointsOverlay.update(point, mMap, true);
    }

    public void newObservationPoint(ObservationPoint point) {
        mObservationPointsOverlay.update(point, mMap, false);
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

    public void showPausedDueToNoMotionMessage(boolean show) {
        mRootView.findViewById(R.id.scanning_paused_message).setVisibility(show? View.VISIBLE : View.INVISIBLE);
        if (mMapLocationListener != null ) {
            mMapLocationListener.pauseGpsUpdates(show);
        }
    }

    public void stop() {
        mRootView.findViewById(R.id.scanning_paused_message).setVisibility(View.INVISIBLE);
        if (mMapLocationListener != null ) {
            mMapLocationListener.removeListener();
            mMapLocationListener = null;
        }
    }
}
