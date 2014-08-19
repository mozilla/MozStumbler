package org.mozilla.mozstumbler.client;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
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
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.DataStorageContract;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.DataStorageManager;
import org.mozilla.mozstumbler.client.mapview.MapActivity;
import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.service.stumblerthread.StumblerService;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.WifiScanner;

import java.io.IOException;
import java.util.Properties;

public final class MainActivity extends FragmentActivity {
    private static final String LOG_TAG = AppGlobals.LOG_PREFIX + MainActivity.class.getSimpleName();

    public static final String ACTION_BASE = AppGlobals.ACTION_NAMESPACE + ".MainActivity.";
    public static final String ACTION_UPDATE_UI = ACTION_BASE + "UPDATE_UI";

    /* if service exists, start scanning, otherwise do nothing  */
    public static final String ACTION_UNPAUSE_SCANNING = ACTION_BASE + "UNPAUSE_SCANNING";

    private static final String LEADERBOARD_URL = "https://location.services.mozilla.com/leaders";

    int                      mGpsFixes;
    int                      mGpsSats;
    private boolean          mGeofenceHere = false;

    private MainApp getApp() {
        return (MainApp) this.getApplication();
    }

    @Override
    protected void onStart() {
        super.onStart();
        getApp().setMainActivity(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        mGeofenceHere = getApp().getPrefs().getGeofenceHere();
        if (mGeofenceHere) {
            getApp().getPrefs().setGeofenceEnabled(false);
        }
        setGeofenceText();

        updateUiOnMainThread();
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

        if (BuildConfig.MOZILLA_API_KEY != null) {
            Updater.checkForUpdates(this);
        }

        Log.d(LOG_TAG, "onCreate");
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

        boolean scanning = service.isScanning();

        CompoundButton scanningBtn = (CompoundButton) findViewById(R.id.toggle_scanning);
        scanningBtn.setChecked(scanning);

        int locationsScanned = 0;
        double latitude = 0;
        double longitude = 0;
        int wifiStatus = WifiScanner.STATUS_IDLE;
        int APs = 0;
        int visibleAPs = 0;
        int cellInfoScanned = 0;
        int currentCellInfo = 0;
        boolean isGeofenced = false;

        locationsScanned = service.getLocationCount();
        latitude = service.getLatitude();
        longitude = service.getLongitude();
        wifiStatus = service.getWifiStatus();
        APs = service.getAPCount();
        visibleAPs = service.getVisibleAPCount();
        cellInfoScanned = service.getCellInfoCount();
        currentCellInfo = service.getCurrentCellInfoCount();
        isGeofenced = service.isGeofenced();

        String lastLocationString = (mGpsFixes > 0 && locationsScanned > 0)?
                                    formatLocation(latitude, longitude) : "-";

        formatTextView(R.id.gps_satellites, R.string.gps_satellites, mGpsFixes, mGpsSats);
        formatTextView(R.id.last_location, R.string.last_location, lastLocationString);
        formatTextView(R.id.wifi_access_points, R.string.wifi_access_points, APs);
        setVisibleAccessPoints(scanning, wifiStatus, visibleAPs);
        formatTextView(R.id.cells_visible, R.string.cells_visible, currentCellInfo);
        formatTextView(R.id.cells_scanned, R.string.cells_scanned, cellInfoScanned);
        formatTextView(R.id.locations_scanned, R.string.locations_scanned, locationsScanned);
        service.checkPrefs();
        if (mGeofenceHere) {
            if (mGpsFixes > 0 && locationsScanned > 0) {
                Location coord = new Location(AppGlobals.LOCATION_ORIGIN_INTERNAL);
                coord.setLatitude(latitude);
                coord.setLongitude(longitude);
                service.getPrefs().setGeofenceLocation(coord);
                service.getPrefs().setGeofenceEnabled(true);
                service.getPrefs().setGeofenceHere(false);
                mGeofenceHere = false;
                setGeofenceText();
            }
        }
        TextView geofence_tv = (TextView) findViewById(R.id.geofence_status);
        geofence_tv.setTextColor(isGeofenced ? Color.RED : Color.BLACK);
        showUploadStats();
   }

    public void onToggleScanningClicked(View v) {
        getApp().toggleScanning(this);
    }

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

    private void setVisibleAccessPoints(boolean scanning, int wifiStatus, int apsCount) {
        String status;
        if (!scanning) {
            status = "";
        } else {
            switch (wifiStatus) {
                case WifiScanner.STATUS_IDLE:
                    status = "";
                    break;
                case WifiScanner.STATUS_WIFI_DISABLED:
                    status = getString(R.string.wifi_disabled);
                    break;
                case WifiScanner.STATUS_ACTIVE:
                    status = String.valueOf(apsCount);
                    break;
                default:
                    throw new IllegalArgumentException();
            }
        }

        formatTextView(R.id.visible_wifi_access_points, R.string.visible_wifi_access_points, status);
    }

    private void formatTextView(int textViewId, int stringId, Object... args) {
        TextView textView = (TextView) findViewById(textViewId);
        String str = getResources().getString(stringId);
        str = String.format(str, args);
        textView.setText(str);
    }

    private String formatLocation(double latitude, double longitude) {
        final String coordinateFormatString = getResources().getString(R.string.coordinate);
        return String.format(coordinateFormatString, latitude, longitude);
    }

    private void setGeofenceText() {
        if (getApp().getPrefs().getGeofenceEnabled()) {
            Location coord = getApp().getPrefs().getGeofenceLocation();
            formatTextView(R.id.geofence_status, R.string.geofencing_on,
                    coord.getLatitude(), coord.getLongitude());
        } else {
            formatTextView(R.id.geofence_status, R.string.geofencing_off);
        }
    }

    public void showUploadStats() {
        if (DataStorageManager.getInstance() == null) {
            return;
        }

        try {
            Properties props = DataStorageManager.getInstance().readSyncStats();
            long lastUploadTime = Long.parseLong(props.getProperty(DataStorageContract.Stats.KEY_LAST_UPLOAD_TIME, "0"));
            String value = (lastUploadTime > 0)? DateTimeUtils.formatTimeForLocale(lastUploadTime) : "-";
            formatTextView(R.id.last_upload_time, R.string.last_upload_time, value);

            value = (props.getProperty(DataStorageContract.Stats.KEY_OBSERVATIONS_SENT, "0"));
            formatTextView(R.id.reports_sent, R.string.reports_sent, Integer.parseInt(value));
        }
        catch (IOException ex) {
            Log.e(LOG_TAG, "Exception in showUploadStats()", ex);
        }
    }


}
