/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread.scanners;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.AppGlobals.ActiveOrPassiveStumbling;
import org.mozilla.mozstumbler.service.stumblerthread.blocklist.BSSIDBlockList;
import org.mozilla.mozstumbler.service.stumblerthread.blocklist.SSIDBlockList;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

public class WifiScanner {
    public static final String ACTION_BASE = AppGlobals.ACTION_NAMESPACE + ".WifiScanner.";
    public static final String ACTION_WIFIS_SCANNED = ACTION_BASE + "WIFIS_SCANNED";
    public static final String ACTION_WIFIS_SCANNED_ARG_RESULTS = "scan_results";
    public static final String ACTION_WIFIS_SCANNED_ARG_TIME = AppGlobals.ACTION_ARG_TIME;

    public static final int STATUS_IDLE = 0;
    public static final int STATUS_ACTIVE = 1;
    public static final int STATUS_WIFI_DISABLED = -1;

    private static final String LOG_TAG = LoggerUtil.makeLogTag(WifiScanner.class);
    private static final long WIFI_MIN_UPDATE_TIME = 5000; // milliseconds
    private final Context mAppContext;
    private final WifiManagerProxy wifiManagerProxy;
    private boolean mStarted;
    private WifiLock mWifiLock;
    private Timer mWifiScanTimer;
    private AtomicInteger mVisibleAPs = new AtomicInteger();

    public WifiScanner(Context appContext) {
        mAppContext = appContext;
        wifiManagerProxy = new WifiManagerProxy(mAppContext);
    }

    public static boolean shouldLog(ScanResult scanResult) {
        if (SSIDBlockList.isOptOut(scanResult)) {
            Log.d(LOG_TAG, "Blocked opt-out SSID");
            return false;
        }
        if (BSSIDBlockList.contains(scanResult)) {
            Log.w(LOG_TAG, "Blocked BSSID: " + scanResult);
            return false;
        }
        if (SSIDBlockList.contains(scanResult)) {
            Log.w(LOG_TAG, "Blocked SSID: " + scanResult);
            return false;
        }
        return true;
    }

    private boolean isScanEnabled() {
        return wifiManagerProxy.isScanEnabled();
    }

    private List<ScanResult> getScanResults() {
        return wifiManagerProxy.getScanResults();
    }

    public synchronized void start(final ActiveOrPassiveStumbling stumblingMode) {
        if (mStarted) {
            return;
        }
        mStarted = true;

        if (isScanEnabled()) {
            activatePeriodicScan(stumblingMode);
        }

        wifiManagerProxy.registerReceiver(this);
    }

    public synchronized void stop() {
        if (mStarted) {
            wifiManagerProxy.unregisterReceiver();
        }
        deactivatePeriodicScan();
        mStarted = false;
    }

    public void onReceive(Context c, Intent intent) {
        String action = intent.getAction();

        if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
            if (isScanEnabled()) {
                activatePeriodicScan(ActiveOrPassiveStumbling.ACTIVE_STUMBLING);
            } else {
                deactivatePeriodicScan();
            }
        } else if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(action)) {
            final List<ScanResult> scanResultList = getScanResults();
            if (scanResultList == null) {
                return;
            }
            final ArrayList<ScanResult> scanResults = new ArrayList<ScanResult>();
            for (ScanResult scanResult : scanResultList) {
                scanResult.BSSID = BSSIDBlockList.canonicalizeBSSID(scanResult.BSSID);

                if (shouldLog(scanResult)) {
                    // Once we've checked that we want this scan result, we can safely discard
                    // the SSID and capabilities.
                    scanResult.SSID = "";
                    scanResult.capabilities = "";
                    scanResults.add(scanResult);
                }
            }
            mVisibleAPs.set(scanResults.size());
            reportScanResults(scanResults);
        }
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

    synchronized void activatePeriodicScan(final ActiveOrPassiveStumbling stumblingMode) {
        if (mWifiScanTimer != null) {
            return;
        }

        if (AppGlobals.isDebug) {
            Log.d(LOG_TAG, "Activate Periodic Scan");
        }

        mWifiLock = wifiManagerProxy.createWifiLock();
        mWifiLock.acquire();

        // Ensure that we are constantly scanning for new access points.
        mWifiScanTimer = new Timer();
        mWifiScanTimer.schedule(new TimerTask() {
            int mPassiveScanCount;

            @Override
            public void run() {
                if (stumblingMode == ActiveOrPassiveStumbling.PASSIVE_STUMBLING &&
                        ++mPassiveScanCount > AppGlobals.PASSIVE_MODE_MAX_SCANS_PER_GPS) {
                    mPassiveScanCount = 0;
                    stop(); // set mWifiScanTimer to null
                    return;
                }
                if (AppGlobals.isDebug) {
                    Log.d(LOG_TAG, "WiFi Scanning Timer fired");
                }
                wifiManagerProxy.runWifiScan();
            }
        }, 0, WIFI_MIN_UPDATE_TIME);
    }

    synchronized void deactivatePeriodicScan() {
        if (mWifiScanTimer == null) {
            return;
        }

        if (AppGlobals.isDebug) {
            Log.d(LOG_TAG, "Deactivate periodic scan");
        }

        mWifiLock.release();
        mWifiLock = null;

        mWifiScanTimer.cancel();
        mWifiScanTimer = null;

        mVisibleAPs.set(0);
    }

    private void reportScanResults(ArrayList<ScanResult> scanResults) {
        if (scanResults.isEmpty()) {
            return;
        }

        Intent i = new Intent(ACTION_WIFIS_SCANNED);
        i.putParcelableArrayListExtra(ACTION_WIFIS_SCANNED_ARG_RESULTS, scanResults);
        i.putExtra(ACTION_WIFIS_SCANNED_ARG_TIME, System.currentTimeMillis());
        LocalBroadcastManager.getInstance(mAppContext).sendBroadcastSync(i);
    }

    public void onProxyReceive(Context c, Intent intent) {
        String action = intent.getAction();

        if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
            if (isScanEnabled()) {
                activatePeriodicScan(ActiveOrPassiveStumbling.ACTIVE_STUMBLING);
            } else {
                deactivatePeriodicScan();
            }
        } else if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(action)) {
            final List<ScanResult> scanResultList = wifiManagerProxy.getScanResults();
            if (scanResultList == null) {
                return;
            }

            final ArrayList<ScanResult> scanResults = new ArrayList<ScanResult>();
            for (ScanResult scanResult : scanResultList) {
                scanResult.BSSID = BSSIDBlockList.canonicalizeBSSID(scanResult.BSSID);
                if (shouldLog(scanResult)) {
                    scanResults.add(scanResult);
                }
            }
            mVisibleAPs.set(scanResults.size());
            reportScanResults(scanResults);
        }
    }
}
