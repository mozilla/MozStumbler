package org.mozilla.mozstumbler.client;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;
import org.mozilla.mozstumbler.BuildConfig;
import org.mozilla.mozstumbler.client.fragments.SettingsFragment;
import org.mozilla.mozstumbler.client.fragments.TabBarFragment;
import org.mozilla.mozstumbler.client.fragments.leaderboard.LeaderboardFragment;
import org.mozilla.mozstumbler.client.fragments.map.MapFragment;
import org.mozilla.mozstumbler.client.fragments.monitor.StumblingDataFragment;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.datahandling.DataStorageContract;
import org.mozilla.mozstumbler.service.utils.DateTimeUtils;
import org.mozilla.mozstumbler.client.mapview.MapActivity;
import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.service.StumblerService;
import org.mozilla.mozstumbler.service.scanners.WifiScanner;

import java.io.IOException;
import java.util.Properties;

public final class MainActivity extends Activity implements TabBarFragment.OnTabSelectedListener,
        TabBarFragment.OnBackButtonPressedListener,
        StumblingDataFragment.DismissStumblingDataFragmentListener {

    public interface MainActivityStateListener {
        public void backgrounded();
        public void foregrounded();
    }

    private MainActivityStateListener mainActivityStateListener;

    private static final String LOGTAG = MainActivity.class.getName();

    public static final String ACTION_BASE = AppGlobals.ACTION_NAMESPACE + ".MainActivity.";
    public static final String ACTION_UPDATE_UI = ACTION_BASE + "UPDATE_UI";

    /** if service exists, start scanning, otherwise do nothing  */
    public static final String ACTION_UNPAUSE_SCANNING = ACTION_BASE + "UNPAUSE_SCANNING";

    private static final String LEADERBOARD_URL = "https://location.services.mozilla.com/leaders";

    int                      mGpsFixes;
    int                      mGpsSats;
    private boolean          mGeofenceHere = false;

    private TabBarFragment tabBarFragment;

    private MapFragment mapFragment;
    private LeaderboardFragment leaderboardFragment;
    private SettingsFragment settingsFragment;
    private StumblingDataFragment stumblingDataFragment;

    private Fragment currentContentFragment;

    private MainApp getApp() {
        return (MainApp) this.getApplication();
    }

    @Override
    protected void onStart() {
        super.onStart();
        getApp().setMainActivity(this);
        mainActivityStateListener = getApp();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mainActivityStateListener != null) {
            mainActivityStateListener.foregrounded();
        }

        mGeofenceHere = getApp().getPrefs().getGeofenceHere();
        if (mGeofenceHere) {
            getApp().getPrefs().setGeofenceEnabled(false);
        }

        setGeofenceText();
        updateUiOnMainThread();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mainActivityStateListener != null) {
            mainActivityStateListener.backgrounded();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        getApp().setMainActivity(null);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final ActionBar actionBar = getActionBar();
        actionBar.hide();

        if (savedInstanceState == null) {
            tabBarFragment = new TabBarFragment();
            tabBarFragment.setTabSelectedListener(this);
            tabBarFragment.setBackButtonPressedListener(this);

            mapFragment = new MapFragment();
            leaderboardFragment = new LeaderboardFragment();

            settingsFragment = new SettingsFragment();

            stumblingDataFragment = new StumblingDataFragment();
            stumblingDataFragment.setDismissStumblingDataFragmentListener(this);

            showTabBarFragment();
            showMapFragment();
        }

        if (BuildConfig.MOZILLA_API_KEY != null) {
            Updater.checkForUpdates(this);
        }

        Log.d(LOGTAG, "onCreate");
    }

    void checkGps() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            new AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle(R.string.app_name)
                .setMessage(R.string.gps_alert_msg)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    }
                })
                .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .show();
        }
    }

    protected void updateUiOnMainThread() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateUI();
            }
        });
    }

    private void updateUI() {
        StumblerService service = getApp().getService();
        if (service == null) {
            return;
        }

        int wifiStatus = service.getWifiStatus();
        double latitude = service.getLatitude();
        double longitude = service.getLongitude();

        int locationsScanned = service.getLocationCount();
        int accessPointsScanned = service.getAPCount();
        int accessPointsVisible = service.getVisibleAPCount();

        int cellTowersScanned = service.getCellInfoCount();
        int cellTowersVisible = service.getCurrentCellInfoCount();

        boolean isGeofenced = service.isGeofenced();

        if (stumblingDataFragment != null) {
            Bundle dataBundle = new Bundle();

            dataBundle.putInt(StumblerService.KEY_WIFI_STATUS, wifiStatus);
            dataBundle.putDouble(StumblerService.KEY_LATITUDE, latitude);
            dataBundle.putDouble(StumblerService.KEY_LONGITUDE, longitude);
            dataBundle.putInt(StumblerService.KEY_LOCATIONS_SCANNED, locationsScanned);
            dataBundle.putInt(StumblerService.KEY_ACCESS_POINTS_SCANNED, accessPointsScanned);
            dataBundle.putInt(StumblerService.KEY_ACCESS_POINTS_VISIBLE, accessPointsVisible);
            dataBundle.putInt(StumblerService.KEY_CELL_TOWERS_SCANNED, cellTowersScanned);
            dataBundle.putInt(StumblerService.KEY_CELL_TOWERS_VISIBLE, cellTowersVisible);

            stumblingDataFragment.updateDataWithBundle(dataBundle);
        }

//        String lastLocationString = (mGpsFixes > 0 && locationsScanned > 0)?
//                                    formatLocation(latitude, longitude) : "-";

//        service.checkPrefs();
//        if (mGeofenceHere) {
//            if (mGpsFixes > 0 && locationsScanned > 0) {
//                Location coord = new Location(AppGlobals.LOCATION_ORIGIN_INTERNAL);
//                coord.setLatitude(latitude);
//                coord.setLongitude(longitude);
//                service.getPrefs().setGeofenceLocation(coord);
//                service.getPrefs().setGeofenceEnabled(true);
//                service.getPrefs().setGeofenceHere(false);
//                mGeofenceHere = false;
//                setGeofenceText();
//            }
//        }
//        TextView geofence_tv = (TextView) findViewById(R.id.geofence_status);
//        geofence_tv.setTextColor(isGeofenced ? Color.RED : Color.BLACK);

//        showUploadStats();
   }

    public void onToggleScanningClicked(View v) {
        getApp().toggleScanning(this);
    }

    /*
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_about:
                startActivity(new Intent(getApplication(), AboutActivity.class));
                return true;
            case R.id.action_preferences:
                PreferencesScreen.setPrefs(getApp().getPrefs());
                startActivity(new Intent(getApplication(), PreferencesScreen.class));
                return true;
            case R.id.action_view_leaderboard:
                Uri.Builder builder = Uri.parse(LEADERBOARD_URL).buildUpon();
                builder.fragment(getApp().getPrefs().getNickname());
                Uri leaderboardUri = builder.build();
                Intent openLeaderboard = new Intent(Intent.ACTION_VIEW, leaderboardUri);
                startActivity(openLeaderboard);
                return true;
            case R.id.action_view_map:
                Intent intent = new Intent(this, MapActivity.class);
                startActivity(intent);
                return true;
            case R.id.action_view_log:
                startActivity(new Intent(getApplication(), LogActivity.class));
                return true;
            case R.id.action_upload_observations:
                UploadReportsDialog newFragment = new UploadReportsDialog();
                newFragment.show(getSupportFragmentManager(), "UploadReportsDialog");
                return true;
            default:
                return super.onOptionsItemSelected(item);

        }
    }
   */

    private void formatTextView(int textViewId, int stringId, Object... args) {
//        TextView textView = (TextView) findViewById(textViewId);
//        String str = getResources().getString(stringId);
//        str = String.format(str, args);
//        textView.setText(str);
    }

    private String formatLocation(double latitude, double longitude) {
        final String coordinateFormatString = getResources().getString(R.string.coordinate);
        return String.format(coordinateFormatString, latitude, longitude);
    }

    private void setGeofenceText() {
//        if (getApp().getPrefs().getGeofenceEnabled()) {
//            Location coord = getApp().getPrefs().getGeofenceLocation();
//            formatTextView(R.id.geofence_status, R.string.geofencing_on,
//                    coord.getLatitude(), coord.getLongitude());
//        } else {
//            formatTextView(R.id.geofence_status, R.string.geofencing_off);
//        }
    }

    public void showUploadStats() {
        if (AppGlobals.dataStorageManager == null)
            return;

        try {
            Properties props = AppGlobals.dataStorageManager.readSyncStats();
            long lastUploadTime = Long.parseLong(props.getProperty(DataStorageContract.Stats.KEY_LAST_UPLOAD_TIME, "0"));
            String value = (lastUploadTime > 0)? DateTimeUtils.formatTimeForLocale(lastUploadTime) : "-";
            formatTextView(R.id.last_upload_time, R.string.last_upload_time, value);

            value = (props.getProperty(DataStorageContract.Stats.KEY_OBSERVATIONS_SENT, "0"));
            formatTextView(R.id.reports_sent, R.string.reports_sent, Integer.parseInt(value));
        }
        catch (IOException ex) {
            Log.e(LOGTAG, "Exception in showUploadStats()", ex);
        }
    }

    private void showTabBarFragment() {
        getFragmentManager().beginTransaction()
                .add(R.id.container, tabBarFragment)
                .commit();
    }

    private void showMapFragment() {
        if (currentContentFragment == null) {

            getFragmentManager().beginTransaction()
                    .add(R.id.content, mapFragment)
                    .commit();

            currentContentFragment = mapFragment;
        }
        else if (currentContentFragment != mapFragment) {

            getFragmentManager().beginTransaction()
                    .remove(currentContentFragment)
                    .commit();

            currentContentFragment = mapFragment;
        }
    }

    private void showLeaderboardFragment() {
        if (currentContentFragment != mapFragment) {
            getFragmentManager().beginTransaction()
                    .remove(currentContentFragment)
                    .commit();
        }

        getFragmentManager().beginTransaction()
                .add(R.id.content, leaderboardFragment)
                .commit();

        currentContentFragment = leaderboardFragment;
    }

    private void showSettingsFragment() {
        if (currentContentFragment != mapFragment) {
            getFragmentManager().beginTransaction()
                    .remove(currentContentFragment)
                    .commit();
        }

        getFragmentManager().beginTransaction()
                .add(R.id.content, settingsFragment)
                .commit();

        currentContentFragment = settingsFragment;
    }

    public void showStumblingDataFragment(int containerId) {
        if (currentContentFragment == stumblingDataFragment) {
            return;
        }

        getFragmentManager().beginTransaction()
                .add(containerId, stumblingDataFragment)
                .commit();

        currentContentFragment = stumblingDataFragment;
    }

    public void toggleStumblerServices() {
        getApp().toggleScanning(this);
    }

    @Override
    public void tabSelected(TabBarFragment.SelectedTab selectedTab) {
        switch (selectedTab) {
            case MAP_TAB:
                showMapFragment();
                break;
            case LEADERBOARD_TAB:
                showLeaderboardFragment();
                break;
            case SETTINGS_TAB:
                showSettingsFragment();
                break;
            default:
                break;
        }
    }

    @Override
    public void backButtonPressed() {
        if (currentContentFragment == settingsFragment) {

            getFragmentManager().beginTransaction()
                    .remove(settingsFragment)
                    .commit();

            currentContentFragment = leaderboardFragment;

            tabBarFragment.toggleBackButton(false, null);
        }
    }

    @Override
    public void dismissStumblingDataFragment() {
        getFragmentManager().beginTransaction()
                .remove(stumblingDataFragment)
                .commit();

        currentContentFragment = mapFragment;
    }
}
