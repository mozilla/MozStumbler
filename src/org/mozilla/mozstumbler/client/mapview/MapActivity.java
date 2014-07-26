package org.mozilla.mozstumbler.client.mapview;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.mozstumbler.client.MainApp;
import org.mozilla.mozstumbler.service.SharedConstants;
import org.mozilla.mozstumbler.service.StumblerService;
import org.mozilla.mozstumbler.BuildConfig;
import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.MainActivity;
import org.mozilla.mozstumbler.service.scanners.GPSScanner;
import org.osmdroid.tileprovider.BitmapPool;
import org.osmdroid.tileprovider.MapTileProviderBasic;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.TilesOverlay;

public final class MapActivity extends Activity {
    private static final String LOGTAG = MapActivity.class.getName();

    private static final String COVERAGE_REDIRECT_URL = "https://location.services.mozilla.com/map.json";
    private static String sCoverageUrl = null;
    private static final int MENU_REFRESH           = 1;
    private static final String ZOOM_KEY = "zoom";
    private static final int DEFAULT_ZOOM = 2;
    private static final String LAT_KEY = "latitude";
    private static final String LON_KEY = "longitude";

    private MapView mMap;
    private AccuracyCircleOverlay mAccuracyOverlay;
    private boolean mFirstLocationFix;
    private ReporterBroadcastReceiver mReceiver;
    Timer mGetUrl = new Timer();
    TilesOverlay mCoverageTilesOverlay = null;

    private class ReporterBroadcastReceiver extends BroadcastReceiver {
        public void reset()
        {
            mMap.getOverlays().remove(mAccuracyOverlay);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(GPSScanner.ACTION_GPS_UPDATED)) {
                MainApp app = (MainApp) getApplication();
                new GetLocationAndMapItTask().execute(app.getService());
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_map);

        mMap = (MapView) this.findViewById(R.id.map);
        mMap.setTileSource(getTileSource());
        mMap.setBuiltInZoomControls(true);
        mMap.setMultiTouchControls(true);

        mFirstLocationFix = true;
        int zoomLevel = DEFAULT_ZOOM; // Default to seeing the world, until we get a fix
        if (savedInstanceState != null) {
            mFirstLocationFix = false;
            zoomLevel = savedInstanceState.getInt(ZOOM_KEY, DEFAULT_ZOOM);
            if (savedInstanceState.containsKey(LAT_KEY) && savedInstanceState.containsKey(LON_KEY)) {
                final double latitude = savedInstanceState.getDouble(LAT_KEY);
                final double longitude = savedInstanceState.getDouble(LON_KEY);
                mMap.getController().setCenter(new GeoPoint(latitude, longitude));
            }
        }
        mMap.getController().setZoom(zoomLevel);

        Log.d(LOGTAG, "onCreate");

        // @TODO: we do a similar "read from URL" in Updater, AbstractCommunicator, make one function for this
        if (sCoverageUrl == null) {
            mGetUrl.schedule(new TimerTask() {
                @Override
                public void run() {
                    java.util.Scanner scanner;
                    try {
                        scanner = new java.util.Scanner(new URL(COVERAGE_REDIRECT_URL).openStream(), "UTF-8");
                    } catch (Exception ex) {
                        Log.d(LOGTAG, ex.toString());
                        if (SharedConstants.guiLogMessageBuffer != null)
                            SharedConstants.guiLogMessageBuffer.add("Failed to get coverage url:" + ex.toString());
                        return;
                    }
                    scanner.useDelimiter("\\A");
                    String result = scanner.next();
                    try {
                        sCoverageUrl = new JSONObject(result).getString("tiles_url");
                    } catch (JSONException ex) {
                        if (SharedConstants.guiLogMessageBuffer != null)
                            SharedConstants.guiLogMessageBuffer.add("Failed to get coverage url:" + ex.toString());
                    }
                    scanner.close();
                }
            }, 0);
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
                                new String[] { BuildConfig.TILE_SERVER_URL });
    }

    private static TilesOverlay CoverageTilesOverlay(Context context) {
        final MapTileProviderBasic coverageTileProvider = new MapTileProviderBasic(context);
        final ITileSource coverageTileSource = new XYTileSource("Mozilla Location Service Coverage Map",
                null,
                1, 13, 256,
                ".png",
                new String[] { sCoverageUrl });
        coverageTileProvider.setTileSource(coverageTileSource);
        final TilesOverlay coverageTileOverlay = new TilesOverlay(coverageTileProvider,context);
        coverageTileOverlay.setLoadingBackgroundColor(Color.TRANSPARENT);
        return coverageTileOverlay;
    }

    private void positionMapAt(Location location) {
        if  (mCoverageTilesOverlay == null && sCoverageUrl != null) {
            mCoverageTilesOverlay = CoverageTilesOverlay(this);
            mMap.getOverlays().add(mCoverageTilesOverlay);
        }

        if (mAccuracyOverlay == null) {
            mAccuracyOverlay = new AccuracyCircleOverlay(this, location);
            mMap.getOverlays().add(mAccuracyOverlay);
        } else {
            mAccuracyOverlay.setLocation(location);
        }

        final GeoPoint point = new GeoPoint(location.getLatitude(), location.getLongitude());
        if (mFirstLocationFix) {
            mMap.getController().setZoom(13);
            mFirstLocationFix = false;
            mMap.getController().setCenter(point);
        } else {
            mMap.getController().animateTo(point);
        }
        mMap.invalidate();
    }

    private static class AccuracyCircleOverlay extends Overlay {
        private GeoPoint mPoint;
        private float mAccuracy;
        private Paint mCircleFillPaint = new Paint();
        private Paint mCircleStrokePaint = new Paint();
        private Paint mCenterPaint = new Paint();
        private Paint mCenterStrokePaint = new Paint();

        public AccuracyCircleOverlay(Context ctx, Location location) {
            super(ctx);
            mPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
            mAccuracy = location.getAccuracy();

            mCircleFillPaint.setARGB(40, 100, 100, 255);
            mCircleFillPaint.setStyle(Paint.Style.FILL);

            mCircleStrokePaint.setARGB(165, 100, 100, 255);
            mCircleStrokePaint.setStyle(Paint.Style.STROKE);

            mCenterPaint.setARGB(255, 100, 100, 255);
            mCenterPaint.setStyle(Paint.Style.FILL);

            mCenterStrokePaint.setARGB(255, 255, 255, 255);
            mCenterStrokePaint.setStyle(Paint.Style.STROKE);
            mCenterStrokePaint.setStrokeWidth(5);
        }

        protected void draw(Canvas c, MapView osmv, boolean shadow) {
            if (shadow || mPoint == null) {
                return;
            }
            Projection pj = osmv.getProjection();
            Point center = pj.toPixels(mPoint, null);
            float radius = pj.metersToEquatorPixels(mAccuracy);

            // Fill
            c.drawCircle(center.x, center.y, radius, mCircleFillPaint);

            // Border
            c.drawCircle(center.x, center.y, radius, mCircleStrokePaint);

            // Center
            c.drawCircle(center.x, center.y, 15, mCenterPaint);
            c.drawCircle(center.x, center.y, 15, mCenterStrokePaint);
        }

        public void setLocation(final Location location) {
            mAccuracy = location.getAccuracy();
            mPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        Intent i = new Intent(MainActivity.ACTION_UNPAUSE_SCANNING);
        LocalBroadcastManager.getInstance(this).sendBroadcast(i);

        mReceiver = new ReporterBroadcastReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(GPSScanner.ACTION_GPS_UPDATED);
        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, intentFilter);
        Log.d(LOGTAG, "onStart");
    }

    @Override
    protected void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
        bundle.putInt(ZOOM_KEY, mMap.getZoomLevel());
        bundle.putDouble(LON_KEY, mMap.getMapCenter().getLongitude());
        bundle.putDouble(LAT_KEY, mMap.getMapCenter().getLatitude());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Log.d(LOGTAG, "onDestroy");
        mMap.getTileProvider().clearTileCache();
        BitmapPool.getInstance().clearBitmapPool();
    }

    @Override
    protected void onStop() {
        super.onStop();

        Log.d(LOGTAG, "onStop");
        if (mReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver);
            mReceiver = null;
        }
    }

    private final class GetLocationAndMapItTask extends AsyncTask<StumblerService, Void, Location> {
        @Override
        public Location doInBackground(StumblerService... params) {
            Log.d(LOGTAG, "requesting location...");

            StumblerService service = params[0];
            final Location result = service.getLocation();
            return result;
        }

        @Override
        protected void onPostExecute(Location result) {
            positionMapAt(result);
        }
    }
}
