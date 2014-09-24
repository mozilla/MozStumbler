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

import org.mozilla.mozstumbler.BuildConfig;
import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.http.HttpUtil;
import org.mozilla.mozstumbler.client.http.IHttpUtil;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.stumblerthread.StumblerService;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.DataStorageContract;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.DataStorageManager;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.WifiScanner;

import java.io.IOException;
import java.util.Properties;

public final class MainActivity extends FragmentActivity {

    private static final String LOG_TAG = AppGlobals.LOG_PREFIX + MainActivity.class.getSimpleName();

    public static final String ACTION_BASE = AppGlobals.ACTION_NAMESPACE + ".MainActivity.";
    public static final String ACTION_UPDATE_UI = ACTION_BASE + "UPDATE_UI";

    public static final String ACTION_UI_UNPAUSE_SCANNING = ACTION_BASE + "UNPAUSE_SCANNING";
    public static final String ACTION_UI_PAUSE_SCANNING = ACTION_BASE + "PAUSE_SCANNING";


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


    private MainApp getApp() {
        return (MainApp) this.getApplication();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
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


        Log.d(LOG_TAG, "onCreate");
    }

    private void updateUI() {
//        ClientStumblerService service = getApp().getService();
//        if (service == null) {
//            return;
//        }
//
//        boolean scanning = service.isScanning();
//
//        // The start/stop button goes into an invalid state sometimes
//        CompoundButton scanningBtn = (CompoundButton) findViewById(R.id.toggle_scanning);
//        scanningBtn.setChecked(scanning);
//
//        int locationsScanned = 0;
//        double latitude = 0;
//        double longitude = 0;
//        int wifiStatus = WifiScanner.STATUS_IDLE;
//        int APs = 0;
//        int visibleAPs = 0;
//        int cellInfoScanned = 0;
//        int currentCellInfo = 0;
//
//        locationsScanned = service.getLocationCount();
//        Location location = service.getLocation();
//        latitude = location.getLatitude();
//        longitude = location.getLongitude();
//        wifiStatus = service.getWifiStatus();
//        APs = service.getAPCount();
//        visibleAPs = service.getVisibleAPCount();
//        cellInfoScanned = service.getCellInfoCount();
//        currentCellInfo = service.getCurrentCellInfoCount();
//
//        String lastLocationString = (this.getGpsFixes() > 0 && locationsScanned > 0)?
//                                    formatLocation(latitude, longitude) : "-";
//
//        formatTextView(R.id.gps_satellites, R.string.gps_satellites, this.getGpsFixes(), this.getGpsSats());
//        formatTextView(R.id.last_location, R.string.last_location, lastLocationString);
//        formatTextView(R.id.wifi_access_points, R.string.wifi_access_points, APs);
//        setVisibleAccessPoints(scanning, wifiStatus, visibleAPs);
//        formatTextView(R.id.cells_visible, R.string.cells_visible, currentCellInfo);
//        formatTextView(R.id.cells_scanned, R.string.cells_scanned, cellInfoScanned);
//        formatTextView(R.id.locations_scanned, R.string.locations_scanned, locationsScanned);
//        showUploadStats();
   }



}
