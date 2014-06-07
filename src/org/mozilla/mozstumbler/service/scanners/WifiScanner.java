package org.mozilla.mozstumbler.service.scanners;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;
import org.mozilla.mozstumbler.service.blocklist.BSSIDBlockList;
import org.mozilla.mozstumbler.service.blocklist.SSIDBlockList;
import org.mozilla.mozstumbler.service.SharedConstants;
import org.mozilla.mozstumbler.service.Prefs;

public class WifiScanner extends BroadcastReceiver {
    public static final String ACTION_BASE = SharedConstants.ACTION_NAMESPACE + ".WifiScanner.";
    public static final String ACTION_WIFIS_SCANNED = ACTION_BASE + "WIFIS_SCANNED";
    public static final String ACTION_WIFIS_SCANNED_ARG_RESULTS = "scan_results";
    public static final String ACTION_WIFIS_SCANNED_ARG_TIME = SharedConstants.ACTION_ARG_TIME;

    public static final int STATUS_IDLE = 0;
    public static final int STATUS_ACTIVE = 1;
    public static final int STATUS_WIFI_DISABLED = -1;

    private static final String LOGTAG = WifiScanner.class.getName();
    private static final long WIFI_MIN_UPDATE_TIME = 1000; // milliseconds

    private boolean mStarted;
    private final Context mContext;
    private WifiLock mWifiLock;
    private Timer mWifiScanTimer;
    private final Set<String> mAPs = Collections.synchronizedSet(new HashSet<String>());
    private AtomicInteger mVisibleAPs = new AtomicInteger();

    public WifiScanner(Context c) {
        mContext = c;
    }

    public synchronized void start() {
        if (mStarted) {
            return;
        }
        mStarted = true;

        boolean scanAlways = new Prefs(mContext).getWifiScanAlways();

        if (scanAlways || getWifiManager().isWifiEnabled()) {
            activatePeriodicScan();
        }

        IntentFilter i = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        if (!scanAlways) i.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        mContext.registerReceiver(this, i);
    }

    public synchronized void stop() {
        if (mStarted) {
            mContext.unregisterReceiver(this);
        }
        deactivatePeriodicScan();
        mStarted = false;
    }

    public void onReceive(Context c, Intent intent) {
        String action = intent.getAction();

        if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
            Log.v(LOGTAG, "WIFI_STATE_CHANGED_ACTION new state: " + intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, -1));
            if (getWifiManager().isWifiEnabled()) {
                activatePeriodicScan();
            } else {
                deactivatePeriodicScan();
            }
        } else if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(action)) {
            ArrayList<ScanResult> scanResults = new ArrayList<ScanResult>();
            for (ScanResult scanResult : getWifiManager().getScanResults()) {
                scanResult.BSSID = BSSIDBlockList.canonicalizeBSSID(scanResult.BSSID);
                if (shouldLog(scanResult)) {
                    scanResults.add(scanResult);
                    mAPs.add(scanResult.BSSID);
                    //Log.v(LOGTAG, "BSSID=" + scanResult.BSSID + ", SSID=\"" + scanResult.SSID + "\", Signal=" + scanResult.level);
                }
            }
            mVisibleAPs.set(scanResults.size());
            reportScanResults(scanResults);
        }
    }

    public int getAPCount() {
        return mAPs.size();
    }

    public int getVisibleAPCount() {
        return mVisibleAPs.get();
    }

    public synchronized int getStatus() {
        if (!mStarted) {
            return STATUS_IDLE;
        }
        if (mWifiScanTimer == null) {
            return STATUS_WIFI_DISABLED;
        }
        return STATUS_ACTIVE;
    }

    private synchronized void activatePeriodicScan() {
        if (mWifiScanTimer != null) {
            return;
        }

        Log.v(LOGTAG, "Activate Periodic Scan");

        mWifiLock = getWifiManager().createWifiLock(WifiManager.WIFI_MODE_SCAN_ONLY,
                "MozStumbler");
        mWifiLock.acquire();

        // Ensure that we are constantly scanning for new access points.
        mWifiScanTimer = new Timer();
        mWifiScanTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                Log.d(LOGTAG, "WiFi Scanning Timer fired");
                getWifiManager().startScan();
            }
        }, 0, WIFI_MIN_UPDATE_TIME);
    }

    private synchronized void deactivatePeriodicScan() {
        if (mWifiScanTimer == null) {
            return;
        }

        Log.v(LOGTAG, "Deactivate periodic scan");

        mWifiLock.release();
        mWifiLock = null;

        mWifiScanTimer.cancel();
        mWifiScanTimer = null;

        mVisibleAPs.set(0);
    }

    private static boolean shouldLog(ScanResult scanResult) {
        if (BSSIDBlockList.contains(scanResult)) {
            Log.w(LOGTAG, "Blocked BSSID: " + scanResult);
            return false;
        }
        if (SSIDBlockList.contains(scanResult)) {
            Log.w(LOGTAG, "Blocked SSID: " + scanResult);
            return false;
        }
        return true;
    }

    private WifiManager getWifiManager() {
        return (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
    }

    private void reportScanResults(ArrayList<ScanResult> scanResults) {
        if (scanResults.isEmpty()) return;
        Intent i = new Intent(ACTION_WIFIS_SCANNED);
        i.putParcelableArrayListExtra(ACTION_WIFIS_SCANNED_ARG_RESULTS, scanResults);
        i.putExtra(ACTION_WIFIS_SCANNED_ARG_TIME, System.currentTimeMillis());
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(i);
    }
}
