package org.mozilla.mozstumbler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.telephony.TelephonyManager;
import android.util.Log;
import java.util.List;
import java.util.Map;
import org.mozilla.mozstumbler.cellscanner.CellInfo;
import org.mozilla.mozstumbler.cellscanner.CellScanner;

final class Reporter extends BroadcastReceiver {
    private static final String LOGTAG          = Reporter.class.getName();

    /**
     * The maximum time of observation
     */
    private static final int REPORTER_WINDOW  = 24 * 60 * 60 * 1000; //ms

    /**
     * The maximum number of Wi-Fi access points in a single observation
     */
    private static final int WIFI_COUNT_WATERMARK = 100;

    /**
     * The maximum number of cells in a single observation
     */
    private static final int CELLS_COUNT_WATERMARK = 50;

    private final Context       mContext;
    private final int             mPhoneType;

    private StumblerBundle     mBundle;

    Reporter(Context context) {
        mContext = context;
        resetData();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiScanner.ACTION_WIFIS_SCANNED);
        intentFilter.addAction(CellScanner.ACTION_CELLS_SCANNED);
        intentFilter.addAction(GPSScanner.ACTION_GPS_UPDATED);
        mContext.registerReceiver(this, intentFilter);

        TelephonyManager tm = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        mPhoneType = tm.getPhoneType();
    }

    private void resetData() {
        mBundle = null;
    }

    void flush() {
        reportCollectedLocation();
    }

    void shutdown() {
        Log.d(LOGTAG, "shutdown");
        flush();
        mContext.unregisterReceiver(this);
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
            reportCollectedLocation();
            Location newPosition = intent.getParcelableExtra(GPSScanner.NEW_LOCATION_ARG_LOCATION);
            mBundle = (newPosition != null) ? new StumblerBundle(newPosition, mPhoneType) : mBundle;
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (action.equals(WifiScanner.ACTION_WIFIS_SCANNED)) {
            receivedWifiMessage(intent);
        } else if (action.equals(CellScanner.ACTION_CELLS_SCANNED)) {
            receivedCellMessage(intent);
        } else if (action.equals(GPSScanner.ACTION_GPS_UPDATED)) {
            // Calls reportCollectedLocation, this is the ideal case
            receivedGpsMessage(intent);
        }

        Location currentPosition = mBundle != null ? mBundle.getGpsPosition() : null;

        long time = intent.getLongExtra(SharedConstants.ACTION_ARG_TIME, System.currentTimeMillis());
        if (currentPosition != null && Math.abs(time - currentPosition.getTime()) > REPORTER_WINDOW) {
            // no gps for a while, just bundle what we have
            reportCollectedLocation();
        }

        if (mBundle != null &&
            (mBundle.getWifiData().size() > WIFI_COUNT_WATERMARK ||
             mBundle.getCellData().size() > CELLS_COUNT_WATERMARK)) {
            // no gps for a while, have too much data, just bundle it
            reportCollectedLocation();
        }
    }

    private void putWifiResults(List<ScanResult> results) {
        if (mBundle == null) {
            return;
        }

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

        Map<String, CellInfo> currentCellData = mBundle.getCellData();
        for (CellInfo result : cells) {
            String key = result.getCellIdentity();
            if (!currentCellData.containsKey(key)) {
                currentCellData.put(key, result);
            }
        }
    }

    private void reportCollectedLocation() {
        if (mBundle == null) {
            return;
        }

        Intent broadcast = new Intent("org.mozilla.mozstumbler.intent.action.STUMBLER_BUNDLE"); // FIXME make into a constant
        broadcast.putExtra("StumblerBundle", mBundle);
        // send to StumblerBundleReceiver
        mContext.sendBroadcast(broadcast);

        mBundle.getGpsPosition().setTime(System.currentTimeMillis());
    }
}
