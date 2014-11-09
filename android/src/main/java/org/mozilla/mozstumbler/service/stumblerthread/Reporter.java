/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.support.v4.content.LocalBroadcastManager;
import android.telephony.TelephonyManager;

import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.core.logging.Log;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.DataStorageContract;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.DataStorageManager;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.StumblerBundle;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.GPSScanner;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.WifiScanner;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.cellscanner.CellInfo;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.cellscanner.CellScanner;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Reporter extends BroadcastReceiver implements IReporter {
    private static final String LOG_TAG = AppGlobals.LOG_PREFIX + Reporter.class.getSimpleName();
    public static final String ACTION_FLUSH_TO_BUNDLE = AppGlobals.ACTION_NAMESPACE + ".FLUSH";
    public static final String ACTION_NEW_BUNDLE = AppGlobals.ACTION_NAMESPACE + ".NEW_BUNDLE";
    public static final String NEW_BUNDLE_ARG_BUNDLE = "bundle";
    private boolean mIsStarted;

    /* The maximum number of Wi-Fi access points in a single observation. */
    public static final int MAX_WIFIS_PER_LOCATION = 200;

    /* The maximum number of cells in a single observation */
    public static final int MAX_CELLS_PER_LOCATION  = 50;

    private Context mContext;
    private int mPhoneType;

    private StumblerBundle mBundle;
    private int mObservationCount = 0;
    private final Set<String> mUniqueAPs = new HashSet<String>();
    private final Set<String> mUniqueCells = new HashSet<String>();

    public Reporter() {}

    public synchronized void startup(Context context) {
        if (mIsStarted) {
            return;
        }

        mContext = context.getApplicationContext();
        TelephonyManager tm = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm != null) {
            mPhoneType = tm.getPhoneType();
        } else {
            Log.d(LOG_TAG, "No telephony manager.");
            mPhoneType = TelephonyManager.PHONE_TYPE_NONE;
        }

        mIsStarted = true;

        mBundle = null;
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiScanner.ACTION_WIFIS_SCANNED);
        intentFilter.addAction(CellScanner.ACTION_CELLS_SCANNED);
        intentFilter.addAction(GPSScanner.ACTION_GPS_UPDATED);
        intentFilter.addAction(ACTION_FLUSH_TO_BUNDLE);

        LocalBroadcastManager.getInstance(mContext).registerReceiver(this,
                intentFilter);
    }

    public synchronized void shutdown() {
        if (mContext == null) {
            return;
        }

        mIsStarted = false;

        Log.d(LOG_TAG, "shutdown");
        flush();
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(this);
    }

    private void receivedWifiMessage(Intent intent) {
        List<ScanResult> results = intent.getParcelableArrayListExtra(WifiScanner.ACTION_WIFIS_SCANNED_ARG_RESULTS);
        putWifiResults(results);
    }

    private void receivedCellMessage(Intent intent) {
        List<CellInfo> results = intent.getParcelableArrayListExtra(CellScanner.ACTION_CELLS_SCANNED_ARG_CELLS);
        putCellResults(results);
    }

    private void receivedGpsMessage(Intent intent) {
        String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);

        if (subject.equals(GPSScanner.SUBJECT_NEW_LOCATION)) {
            Location newPosition = intent.getParcelableExtra(GPSScanner.NEW_LOCATION_ARG_LOCATION);
            // Only create StumblerBundle instances if the position exists
            if (newPosition != null) {
                flush();
                mBundle = new StumblerBundle(newPosition, mPhoneType);
            }
        }
    }

    @Override
    public synchronized void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (action.equals(ACTION_FLUSH_TO_BUNDLE)) {
            flush();
            return;
        } else if (action.equals(WifiScanner.ACTION_WIFIS_SCANNED)) {
            receivedWifiMessage(intent);
        } else if (action.equals(CellScanner.ACTION_CELLS_SCANNED)) {
            receivedCellMessage(intent);
        } else if (action.equals(GPSScanner.ACTION_GPS_UPDATED)) {
            // This is the common case
            receivedGpsMessage(intent);
        }

        if (mBundle != null &&
                (mBundle.getWifiData().size() > MAX_WIFIS_PER_LOCATION ||
                 mBundle.getCellData().size() > MAX_CELLS_PER_LOCATION))
        {
            // no gps for a while, have too much data, just bundle it
            flush();
        }
    }

    public synchronized Map<String, ScanResult> getWifiData() {
        if (mBundle == null){
            return null;
        }
        return mBundle.getWifiData();
    }

    public synchronized Map<String, CellInfo> getCellData() {
        if (mBundle == null){
            return null;
        }
        return mBundle.getCellData();
    }

    public synchronized Location getGPSLocation() {
        if (mBundle == null){
            return null;
        }
        return mBundle.getGpsPosition();
    }

    private void putWifiResults(List<ScanResult> results) {
        if (mBundle == null) {
            return;
        }

        Map<String, ScanResult> currentWifiData = mBundle.getWifiData();
        for (ScanResult result : results) {
            if (currentWifiData.size() > MAX_WIFIS_PER_LOCATION) {
                AppGlobals.guiLogInfo("Max wifi limit exceeded for this location, ignoring data.");
                return;
            }

            String key = result.BSSID;
            if (!currentWifiData.containsKey(key)) {
                currentWifiData.put(key, result);
            }

        }
    }

    private void putCellResults(List<CellInfo> cells) {
        if (mBundle == null) {
            return;
        }

        Map<String, CellInfo> currentCellData = mBundle.getCellData();
        for (CellInfo result : cells) {
            if (currentCellData.size() > MAX_CELLS_PER_LOCATION) {
                AppGlobals.guiLogInfo("Max cell limit exceeded for this location, ignoring data.");
                return;
            }
            String key = result.getCellIdentity();
            if (!currentCellData.containsKey(key)) {
                currentCellData.put(key, result);
            }
        }
    }

    public synchronized void flush() {
        JSONObject mlsObj;
        int wifiCount = 0;
        int cellCount = 0;

        if (mBundle == null) {
            return;
        }

        try {
            mlsObj = mBundle.toMLSJSON();
            wifiCount = mlsObj.getInt(DataStorageContract.ReportsColumns.WIFI_COUNT);
            cellCount = mlsObj.getInt(DataStorageContract.ReportsColumns.CELL_COUNT);
        } catch (JSONException e) {
            Log.w(LOG_TAG, "Failed to convert bundle to JSON: " + e);
            mBundle = null;
            return;
        }

        if (AppGlobals.isDebug) {
            Log.d(LOG_TAG, "Received a MLS bundle" + mlsObj.toString());
        }

        if (wifiCount + cellCount < 1) {
            mBundle = null;
            return;
        }

        AppGlobals.guiLogInfo("MLS record: " + mlsObj.toString());

        try {
            DataStorageManager.getInstance().insert(mlsObj.toString(), wifiCount, cellCount);

            Intent i = new Intent(ACTION_NEW_BUNDLE);
            i.putExtra(NEW_BUNDLE_ARG_BUNDLE, mBundle);
            i.putExtra(AppGlobals.ACTION_ARG_TIME, System.currentTimeMillis());
            LocalBroadcastManager.getInstance(mContext).sendBroadcastSync(i);

            mObservationCount++;
            mUniqueAPs.addAll(mBundle.getWifiData().keySet());
            mUniqueCells.addAll(mBundle.getCellData().keySet());
        } catch (IOException e) {
            Log.w(LOG_TAG, e.toString());
        }

        mBundle = null;
    }

    public synchronized int getObservationCount() {
        return mObservationCount;
    }

    public synchronized int getUniqueAPCount() {
        return mUniqueAPs.size();
    }

    public synchronized int getUniqueCellCount() {
        return mUniqueCells.size();
    }
}
