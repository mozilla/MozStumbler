package org.mozilla.mozstumbler;

import android.util.Log;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

public class WifiScanner extends BroadcastReceiver {
    public static final String WIFI_SCANNER_EXTRA_SUBJECT = "WifiScanner";
    public static final String WIFI_SCANNER_ARG_SCAN_RESULTS = "org.mozilla.mozstumbler.WifiScanner.scan_results";

    private static final String LOGTAG = Scanner.class.getName();
    private static final long WIFI_MIN_UPDATE_TIME = 1000; // milliseconds

    private boolean mStarted;
    private final Context mContext;
    private WifiLock mWifiLock;
    private Timer mWifiScanTimer;
    private final Set<String> mAPs = Collections.synchronizedSet(new HashSet<String>());
    private AtomicInteger mVisibleAPs = new AtomicInteger();

    WifiScanner(Context c) {
        mContext = c;
    }

    public synchronized void start() {
        WifiManager wm = getWifiManager();

        if (mStarted) {
            return;
        }
        mStarted = true;

        if (getWifiManager().isWifiEnabled()) {
            activatePeriodicScan();
        }

        IntentFilter i = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        i.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
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
        Intent i = new Intent(ScannerService.MESSAGE_TOPIC);
        i.putExtra(Intent.EXTRA_SUBJECT, WIFI_SCANNER_EXTRA_SUBJECT);
        i.putParcelableArrayListExtra(WIFI_SCANNER_ARG_SCAN_RESULTS, scanResults);
        i.putExtra("time", System.currentTimeMillis());
        mContext.sendBroadcast(i);
    }
}
