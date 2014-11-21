/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread.scanners;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.mozilla.mozstumbler.client.ClientPrefs;
import org.mozilla.mozstumbler.client.MainApp;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.AppGlobals.ActiveOrPassiveStumbling;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.stumblerthread.Reporter;
import org.mozilla.mozstumbler.service.stumblerthread.blocklist.WifiBlockListInterface;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.cellscanner.CellScanner;
import org.mozilla.mozstumbler.service.utils.BatteryCheckReceiver;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class ScanManager {
    private static final String LOG_TAG = AppGlobals.makeLogTag(ScanManager.class.getSimpleName());
    private Timer mPassiveModeFlushTimer;

    private static Context mAppContext;

    private boolean mIsScanning;
    private GPSScanner mGPSScanner;
    private WifiScanner mWifiScanner;
    private CellScanner mCellScanner;
    private ActiveOrPassiveStumbling mStumblingMode = ActiveOrPassiveStumbling.ACTIVE_STUMBLING;

    private BatteryCheckReceiver mPassiveModeBatteryChecker;
    private enum PassiveModeBatteryState { OK, LOW, IGNORE_BATTERY_STATE }
    private PassiveModeBatteryState mPassiveModeBatteryState;

    private BatteryCheckReceiver.BatteryCheckCallback mBatteryCheckCallback = new BatteryCheckReceiver.BatteryCheckCallback() {
        @Override
        public void batteryCheckCallback(BatteryCheckReceiver receiver) {
            if (mPassiveModeBatteryState == PassiveModeBatteryState.IGNORE_BATTERY_STATE) {
                return;
            }
            final int kMinBatteryPct = 15;
            boolean isLow = receiver.isBatteryNotChargingAndLessThan(kMinBatteryPct);
            mPassiveModeBatteryState = isLow? PassiveModeBatteryState.LOW : PassiveModeBatteryState.OK;
        }
    };

    public ScanManager() {}

    // By default, if the battery level is low, then the service stops scanning, however the client
    // can disable this and perform more complex logic
    public void setShouldStopPassiveScanningOnBatteryLow(boolean shouldStop) {
        mPassiveModeBatteryState = shouldStop? PassiveModeBatteryState.OK :
                                               PassiveModeBatteryState.IGNORE_BATTERY_STATE;
    }

    public void newPassiveGpsLocation() {
        if (mPassiveModeBatteryState == PassiveModeBatteryState.LOW) {
            return;
        }

        if (AppGlobals.isDebug) {
            Log.d(LOG_TAG, "New passive location");
        }

        mWifiScanner.start(ActiveOrPassiveStumbling.PASSIVE_STUMBLING);
        mCellScanner.start(ActiveOrPassiveStumbling.PASSIVE_STUMBLING);

        // how often to flush a leftover bundle to the reports table
        // If there is a bundle, and nothing happens for 10sec, then flush it
        final int flushRate_ms = 10000;

        if (mPassiveModeFlushTimer != null) {
            mPassiveModeFlushTimer.cancel();
        }

        Date when = new Date();
        when.setTime(when.getTime() + flushRate_ms);
        mPassiveModeFlushTimer = new Timer();
        mPassiveModeFlushTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                Intent flush = new Intent(Reporter.ACTION_FLUSH_TO_BUNDLE);
                if (mAppContext == null) {
                    return;
                }
                LocalBroadcastManager.getInstance(mAppContext).sendBroadcastSync(flush);
            }
        }, when);
    }

    public synchronized boolean isPassiveMode() {
        return ActiveOrPassiveStumbling.PASSIVE_STUMBLING == mStumblingMode;
    }

    public synchronized void setPassiveMode(boolean on) {
        if (on && mPassiveModeBatteryChecker == null) {
            mPassiveModeBatteryChecker = new BatteryCheckReceiver(mAppContext, mBatteryCheckCallback);
        }
        mStumblingMode = (on) ? ActiveOrPassiveStumbling.PASSIVE_STUMBLING :
                ActiveOrPassiveStumbling.ACTIVE_STUMBLING;
    }

    public synchronized void startScanning(Context ctx) {

        Log.d(LOG_TAG, "ScanManager::startScanning");

        if (this.isScanning()) {
            return;
        }

        mAppContext = ctx.getApplicationContext();
        if (mAppContext == null) {
            Log.w(LOG_TAG, "No app context available.");
            return;
        }

        if (AppGlobals.isDebug) {
            // Simulation contexts are only allowed for debug builds.
            Prefs prefs = Prefs.getInstance();
            if (prefs != null) {
                if (prefs.isSimulateStumble()) {
                    mAppContext = new SimulationContext(mAppContext);
                    Log.d(LOG_TAG, "ScanManager using SimulateStumbleContextWrapper");
                }
            }
        }

        mGPSScanner = new GPSScanner(mAppContext, this);
        mWifiScanner = new WifiScanner(mAppContext);
        mCellScanner = new CellScanner(mAppContext);

        mGPSScanner.start(mStumblingMode);
        if (mStumblingMode == ActiveOrPassiveStumbling.ACTIVE_STUMBLING) {
            mWifiScanner.start(mStumblingMode);
            mCellScanner.start(mStumblingMode);

            // in passive mode, these scans are started by passive gps notifications
        }
        mIsScanning = true;
    }

    public synchronized boolean stopScanning() {
        if (!this.isScanning()) {
            return false;
        }

        if (mAppContext instanceof SimulationContext) {
            ((SimulationContext)mAppContext).deactivateSimulation();
        }
        // Reset the application context to the unwrapped version
        mAppContext = mAppContext.getApplicationContext();

        if (AppGlobals.isDebug) {
            Log.d(LOG_TAG, "Scanning stopped");
        }

        mGPSScanner.stop();
        mWifiScanner.stop();
        mCellScanner.stop();

        mGPSScanner = null;
        mWifiScanner = null;
        mCellScanner = null;

        mIsScanning = false;
        return true;
    }

    public void setWifiBlockList(WifiBlockListInterface list) {
        WifiScanner.setWifiBlockList(list);
    }

    public synchronized boolean isScanning() {
        return mIsScanning;
    }

    public int getVisibleAPCount() {
        return (mWifiScanner == null) ? 0 : mWifiScanner.getVisibleAPCount();
    }

    public int getWifiStatus() {
        return (mWifiScanner == null) ? 0 : mWifiScanner.getStatus();
    }

    public int getVisibleCellInfoCount() {
        return (mCellScanner == null) ? 0 : mCellScanner.getVisibleCellInfoCount();
    }

    public int getLocationCount() {
        return (mGPSScanner == null) ? 0 : mGPSScanner.getLocationCount();
    }

    public Location getLocation() {
        return (mGPSScanner == null) ? new Location("null") : mGPSScanner.getLocation();
    }

}
