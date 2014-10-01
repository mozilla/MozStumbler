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
import android.util.Log;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.DataStorageContract;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.DataStorageManager;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.StumblerBundle;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.cellscanner.CellInfo;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.cellscanner.CellScanner;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.GPSScanner;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.WifiScanner;

public final class Reporter extends BroadcastReceiver implements IReporter {
    private static final String LOG_TAG = AppGlobals.LOG_PREFIX + Reporter.class.getSimpleName();
    public static final String ACTION_FLUSH_TO_BUNDLE = AppGlobals.ACTION_NAMESPACE + ".FLUSH";
    private boolean mIsStarted;

    /* The maximum number of Wi-Fi access points in a single observation. */
    private static final int MAX_WIFIS_PER_LOCATION = 200;

    /* The maximum number of cells in a single observation */
    private static final int MAX_CELLS_PER_LOCATION  = 50;

    private Context mContext;
    private int mPhoneType;

    private long lastNmeaGGA;
    private long lastNmeaGSV;

    private StumblerBundle mBundle;
    private JSONObject mPreviousBundleJSON;

    Reporter() {}

    public synchronized JSONObject getPreviousBundleJSON() {
        return mPreviousBundleJSON;
    }

    public synchronized void startup(Context context) {
        if (mIsStarted) {
            return;
        }

        mContext = context.getApplicationContext();
        TelephonyManager tm = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        mPhoneType = tm.getPhoneType();

        mIsStarted = true;

        mBundle = null;
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiScanner.ACTION_WIFIS_SCANNED);
        intentFilter.addAction(CellScanner.ACTION_CELLS_SCANNED);
        intentFilter.addAction(GPSScanner.ACTION_GPS_UPDATED);
        intentFilter.addAction(GPSScanner.ACTION_NMEA_RECEIVED);
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
            // Only create StumblerBundle instances if the NMEA data looks marginally ok
            if (newPosition != null && this.hasNMEAData()) {
                flush();
                mBundle = new StumblerBundle(newPosition, mPhoneType);
            }
        }
    }

    /**
     * Returns True if we've received both GGA and GSV data within the
     * last minute.
     */
    public synchronized boolean hasNMEAData() {
        long gga_delta = System.currentTimeMillis() - this.lastNmeaGGA;
        long gsv_delta = System.currentTimeMillis() - this.lastNmeaGSV;

        if (this.lastNmeaGGA == 0 || this.lastNmeaGSV == 0) {
            return false;
        }
        return gga_delta < 60000 && gsv_delta < 60000;
    }

    private synchronized void updatelastNmeaGGA()
    {
        this.lastNmeaGGA = System.currentTimeMillis();
    }

    private synchronized void updatelastNmeaGSV()
    {
        this.lastNmeaGSV = System.currentTimeMillis();
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
        } else if (action.equals(GPSScanner.ACTION_NMEA_RECEIVED)) {
            // We only care that we're getting a bunch of GGA and GSV
            // commands.
            String nmea_data = intent.getStringExtra(GPSScanner.NMEA_DATA);
            if (nmea_data != null && nmea_data.length() > 7) {
                String nmea_type = nmea_data.substring(3, 6);

                if (nmea_type.equals("GGA")) {
                    // essential fix data which provide 3D
                    // location and accuracy data.
                    this.updatelastNmeaGGA();
                } else if (nmea_type.equals("GSV")) {
                    //  Satellites in View shows data about the
                    //  satellites that the unit might be able to
                    //  find based on its viewing mask and almanac
                    //  data. It also shows current ability to
                    //  track this data. Note that one GSV
                    //  sentence only can provide data for up to 4
                    //  satellites and thus there may need to be 3
                    //  sentences for the full information. It is
                    //  reasonable for the GSV sentence to contain
                    //  more satellites than GGA might indicate
                    //  since GSV may include satellites that are
                    //  not used as part of the solution. 
                    this.updatelastNmeaGSV();
                }
            }

            if (mBundle != null &&
                    (mBundle.getWifiData().size() > MAX_WIFIS_PER_LOCATION ||
                     mBundle.getCellData().size() > MAX_CELLS_PER_LOCATION)) 
            {
                // no gps for a while, have too much data, just bundle it
                flush();
            }
        }
    }

    private void putWifiResults(List<ScanResult> results) {
        if (mBundle == null) {
            return;
        }

        // TODO: vng - this get/test/put loop should really just 
        // push key/value pairs into the mBundle celldata map through
        // something like an update call.
        Map<String, ScanResult> currentWifiData = mBundle.getWifiData();
        for (ScanResult result : results) {
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

        // TODO: vng - this get/test/put loop should really just 
        // push key/value pairs into the mBundle celldata map through
        // something like an update call.
        Map<String, CellInfo> currentCellData = mBundle.getCellData();
        for (CellInfo result : cells) {
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
            return;
        }

        if (AppGlobals.isDebug) {
            Log.d(LOG_TAG, "Received bundle: " + mlsObj.toString());
        }

        if (wifiCount + cellCount < 1)
            return;

        mPreviousBundleJSON = mlsObj;

        AppGlobals.guiLogInfo(mlsObj.toString());

        try {
            DataStorageManager.getInstance().insert(mlsObj.toString(), wifiCount, cellCount);
        } catch (IOException e) {
            Log.w(LOG_TAG, e.toString());
        }
    }
}
