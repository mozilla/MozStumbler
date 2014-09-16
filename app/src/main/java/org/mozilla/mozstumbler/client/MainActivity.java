/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.stumblerthread.StumblerService;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.DataStorageContract;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.DataStorageManager;
import org.mozilla.mozstumbler.client.mapview.MapActivity;
import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.WifiScanner;

import java.io.IOException;
import java.util.Properties;

public final class MainActivity extends FragmentActivity
                                implements IMainActivity {

    private static final String LOG_TAG = AppGlobals.LOG_PREFIX + MainActivity.class.getSimpleName();

    public static final String ACTION_BASE = AppGlobals.ACTION_NAMESPACE + ".MainActivity.";
    public static final String ACTION_UPDATE_UI = ACTION_BASE + "UPDATE_UI";

    public static final String ACTION_UI_UNPAUSE_SCANNING = ACTION_BASE + "UNPAUSE_SCANNING";
    public static final String ACTION_UI_PAUSE_SCANNING = ACTION_BASE + "PAUSE_SCANNING";


    private static final String LEADERBOARD_URL = "https://location.services.mozilla.com/leaders";

    int                      mGpsFixes;
    int                      mGpsSats;


    public synchronized void setGpsFixes(int fixes) {
        mGpsFixes = fixes;
    }

    public synchronized void setGpsSats(int sats) {
        mGpsSats = sats;
    }

    public synchronized int getGpsFixes() {
        return mGpsFixes;
    }

    public synchronized int getGpsSats() {
        return mGpsSats;
    }

    private BroadcastReceiver notificationDrawerEventReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            StumblerService service = getApp().getService();
            if (service == null) {
                return;
            }

            CompoundButton scanningBtn = (CompoundButton) findViewById(R.id.toggle_scanning);

            if (intent.getAction().equals(MainActivity.ACTION_UI_PAUSE_SCANNING) && service.isScanning()) {
                // Grab the scanning button and just click it
                onToggleScanningClicked(scanningBtn);
            } else if (intent.getAction().equals(MainActivity.ACTION_UI_UNPAUSE_SCANNING) && !service.isScanning()) {
                onToggleScanningClicked(scanningBtn);
            }
        }
    };


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

        Updater upd = new Updater();
        upd.checkForUpdates(this);

        // Register a listener for a toggle event in the notification pulldown
        LocalBroadcastManager bManager = LocalBroadcastManager.getInstance(this);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(MainActivity.ACTION_UI_UNPAUSE_SCANNING);
        intentFilter.addAction(MainActivity.ACTION_UI_PAUSE_SCANNING);
        bManager.registerReceiver(notificationDrawerEventReceiver, intentFilter);

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

    public void updateUiOnMainThread() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateUI();
            }
        });
    }

    private void updateUI() {
        ClientStumblerService service = getApp().getService();
        if (service == null) {
            return;
        }

        boolean scanning = service.isScanning();

        // The start/stop button goes into an invalid state sometimes
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

        locationsScanned = service.getLocationCount();
        Location location = service.getLocation();
        latitude = location.getLatitude();
        longitude = location.getLongitude();
        wifiStatus = service.getWifiStatus();
        APs = service.getAPCount();
        visibleAPs = service.getVisibleAPCount();
        cellInfoScanned = service.getCellInfoCount();
        currentCellInfo = service.getCurrentCellInfoCount();

        String lastLocationString = (this.getGpsFixes() > 0 && locationsScanned > 0)?
                                    formatLocation(latitude, longitude) : "-";

        formatTextView(R.id.gps_satellites, R.string.gps_satellites, this.getGpsFixes(), this.getGpsSats());
        formatTextView(R.id.last_location, R.string.last_location, lastLocationString);
        formatTextView(R.id.wifi_access_points, R.string.wifi_access_points, APs);
        setVisibleAccessPoints(scanning, wifiStatus, visibleAPs);
        formatTextView(R.id.cells_visible, R.string.cells_visible, currentCellInfo);
        formatTextView(R.id.cells_scanned, R.string.cells_scanned, cellInfoScanned);
        formatTextView(R.id.locations_scanned, R.string.locations_scanned, locationsScanned);
        showUploadStats();
   }

    public void onToggleScanningClicked(View v) {
        getApp().toggleScanning(this);

        StumblerService service = getApp().getService();
        if (service == null) {
            return;
        }

        updateUiOnMainThread();

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
                Intent intent = new Intent(this.getApplicationContext(), MapActivity.class);
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
