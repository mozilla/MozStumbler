package org.mozilla.mozstumbler.client.mapview;

import android.annotation.TargetApi;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;

import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.overlay.GpsLocationProvider;
import com.mapbox.mapboxsdk.overlay.UserLocationOverlay;
import com.mapbox.mapboxsdk.tileprovider.tilesource.MapboxTileLayer;
import com.mapbox.mapboxsdk.tileprovider.tilesource.TileLayer;
import com.mapbox.mapboxsdk.tileprovider.tilesource.WebSourceTileLayer;
import com.mapbox.mapboxsdk.views.MapView;

import org.mozilla.mozstumbler.BuildConfig;
import org.mozilla.mozstumbler.R;

public final class MapActivity extends Activity {
    private static final String LOGTAG = MapActivity.class.getName();

    private static final String COVERAGE_URL = "https://location.services.mozilla.com/tiles/{z}/{x}/{y}.png";
    private static final int MENU_REFRESH           = 1;
    private static final String CENTER_KEY = "center";
    private static final String ZOOM_KEY = "zoom";

    private MapView mMap;
    private UserLocationOverlay mUserLocationOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_map);

        mMap = (MapView) this.findViewById(R.id.map);
        mMap.setTileSource(mlsCoverageTileLayer());
        mMap.addTileSource(getTileSource());
        mUserLocationOverlay = addLocationOverlay(this, mMap);

        float zoomLevel = 13; // Default to the max that the Coverage Map provides
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(CENTER_KEY)) {
                LatLng center = savedInstanceState.getParcelable(CENTER_KEY);
                if (center != null) {
                    mMap.setCenter(center);
                }
            }
            if (savedInstanceState.containsKey(ZOOM_KEY)) {
                zoomLevel = savedInstanceState.getFloat(ZOOM_KEY);
            }
        }
        mMap.setZoom(zoomLevel);

        Log.d(LOGTAG, "onCreate");
    }

    @Override
    protected void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
        bundle.putParcelable(CENTER_KEY, mMap.getCenter());
        bundle.putFloat(ZOOM_KEY, mMap.getZoomLevel());
    }

    private static UserLocationOverlay addLocationOverlay(Activity activity, MapView mapView) {
        UserLocationOverlay userLocationOverlay = new UserLocationOverlay(
                new GpsLocationProvider(activity), mapView);
        userLocationOverlay.enableMyLocation();
        userLocationOverlay.setDrawAccuracyEnabled(true);
        mapView.setCenter(userLocationOverlay.getMyLocation());
        mapView.getOverlays().add(userLocationOverlay);
        return userLocationOverlay;
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
                if (mUserLocationOverlay != null) {
                    mMap.setCenter(mUserLocationOverlay.getMyLocation());
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    private static TileLayer getTileSource() {
        if (BuildConfig.TILE_SERVER_URL == null) {
            return openStreetMapTileLayer();
        }
        return new MapboxTileLayer(BuildConfig.TILE_SERVER_URL);
    }

    private static TileLayer openStreetMapTileLayer() {
        return new WebSourceTileLayer("openstreetmap",
                "http://tile.openstreetmap.org/{z}/{x}/{y}.png").setName("OpenStreetMap")
                .setAttribution("© OpenStreetMap Contributors")
                .setMinimumZoomLevel(1)
                .setMaximumZoomLevel(18);
    }

    private static TileLayer mlsCoverageTileLayer() {
        return new WebSourceTileLayer("mozilla", COVERAGE_URL)
                .setName("Mozilla Location Service Coverage Map")
                .setAttribution("© Mozilla Location Services Contributors")
                .setMinimumZoomLevel(1)
                .setMaximumZoomLevel(13);
    }
}
