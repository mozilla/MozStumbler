/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread.scanners;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.support.v4.content.LocalBroadcastManager;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.AppGlobals.ActiveOrPassiveStumbling;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.core.logging.ClientLog;
import org.mozilla.mozstumbler.service.stumblerthread.Reporter;
import org.mozilla.mozstumbler.service.stumblerthread.motiondetection.LocationChangeSensor;
import org.mozilla.mozstumbler.service.stumblerthread.motiondetection.MotionSensor;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.cellscanner.CellScanner;
import org.mozilla.mozstumbler.service.utils.BatteryCheckReceiver;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class ScanManager {
    public static final String ACTION_SCAN_PAUSED_USER_MOTIONLESS = AppGlobals.ACTION_NAMESPACE + ".NOTIFY_USER_MOTIONLESS";
    public static final String ACTION_SCAN_UNPAUSED_USER_MOVED = AppGlobals.ACTION_NAMESPACE + ".NOTIFY_USER_MOVED";
    private static final String LOG_TAG = LoggerUtil.makeLogTag(ScanManager.class);
    private static Context mAppContext;
    private Timer mPassiveModeFlushTimer;
    private GPSScanner mGPSScanner;
    private WifiScanner mWifiScanner;
    private CellScanner mCellScanner;
    private ActiveOrPassiveStumbling mStumblingMode = ActiveOrPassiveStumbling.ACTIVE_STUMBLING;

    private BatteryCheckReceiver mPassiveModeBatteryChecker;
    private PassiveModeBatteryState mPassiveModeBatteryState;
    private BatteryCheckReceiver.BatteryCheckCallback mBatteryCheckCallback = new BatteryCheckReceiver.BatteryCheckCallback() {
        @Override
        public void batteryCheckCallback(BatteryCheckReceiver receiver) {
            if (mPassiveModeBatteryState == PassiveModeBatteryState.IGNORE_BATTERY_STATE) {
                return;
            }
            final int kMinBatteryPct = 15;
            boolean isLow = receiver.isBatteryNotChargingAndLessThan(kMinBatteryPct);
            mPassiveModeBatteryState = isLow ? PassiveModeBatteryState.LOW : PassiveModeBatteryState.OK;
        }
    };
    private LocationChangeSensor mLocationChangeSensor;
    private MotionSensor mMotionSensor;

    private enum ScannerState {
        STOPPED, STARTED, STARTED_BUT_PAUSED_MOTIONLESS
    }
    private ScannerState mScannerState = ScannerState.STOPPED;

    // After DetectUnchangingLocation reports the user is not moving, and the scanning pauses,
    // then use MotionSensor to determine when to wake up and start scanning again.
    private final BroadcastReceiver mDetectUserIdleReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isStopped() ||
                    Prefs.getInstance(mAppContext).getPowerSavingMode() == Prefs.PowerSavingModeOptions.Off) {
                return;
            }
            stopScanning();
            mScannerState = ScannerState.STARTED_BUT_PAUSED_MOTIONLESS;

            if (AppGlobals.isDebug) {
                ClientLog.d(LOG_TAG, "MotionSensor started");
            }
            mMotionSensor.start();

            Intent sendIntent = new Intent(ACTION_SCAN_PAUSED_USER_MOTIONLESS);
            LocalBroadcastManager.getInstance(mAppContext).sendBroadcastSync(sendIntent);
        }
    };

    private final BroadcastReceiver mDetectMotionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mScannerState != ScannerState.STARTED_BUT_PAUSED_MOTIONLESS ) {
                return;
            }
            mScannerState = ScannerState.STOPPED; // To ensure startScanning() runs
            startScanning(context);
            mMotionSensor.stop();

            if (Prefs.getInstance(mAppContext).getPowerSavingMode() == Prefs.PowerSavingModeOptions.Aggressive) {
                mLocationChangeSensor.quickCheckForFalsePositiveAfterMotionSensorMovement();
            }

            Intent sendIntent = new Intent(ACTION_SCAN_UNPAUSED_USER_MOVED);
            LocalBroadcastManager.getInstance(mAppContext).sendBroadcastSync(sendIntent);
        }
    };

    public ScanManager() {
    }

    // By default, if the battery level is low, then the service stops scanning, however the client
    // can disable this and perform more complex logic
    public void setShouldStopPassiveScanningOnBatteryLow(boolean shouldStop) {
        mPassiveModeBatteryState = shouldStop ? PassiveModeBatteryState.OK :
                PassiveModeBatteryState.IGNORE_BATTERY_STATE;
    }

    public void newPassiveGpsLocation() {
        if (mPassiveModeBatteryState == PassiveModeBatteryState.LOW) {
            return;
        }

        if (AppGlobals.isDebug) {
            ClientLog.d(LOG_TAG, "New passive location");
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
        ClientLog.d(LOG_TAG, "ScanManager::startScanning");

        if (!isStopped()) {
            return;
        }

        mAppContext = ctx.getApplicationContext();
        if (mAppContext == null) {
            ClientLog.w(LOG_TAG, "No app context available.");
            return;
        }

        mScannerState = ScannerState.STARTED;

        if (mLocationChangeSensor == null) {
            mLocationChangeSensor = new LocationChangeSensor(mAppContext, mDetectUserIdleReceiver);
        }
        mLocationChangeSensor.start();

        if (mMotionSensor == null) {
            if (mDetectMotionReceiver != null) {
                LocalBroadcastManager.getInstance(mAppContext).registerReceiver(mDetectMotionReceiver,
                        new IntentFilter(MotionSensor.ACTION_USER_MOTION_DETECTED));
            }

            mMotionSensor = new MotionSensor(mAppContext);
        }

        if (AppGlobals.isDebug) {
            // Simulation contexts are only allowed for debug builds.
            Prefs prefs = Prefs.getInstanceWithoutContext();
            if (prefs != null) {
                ClientLog.i(LOG_TAG, "ScanManager::startScanning simulation pref = " + prefs.isSimulateStumble());
                if (prefs.isSimulateStumble()) {
                    mAppContext = new SimulationContext(mAppContext);
                    ClientLog.d(LOG_TAG, "ScanManager using SimulateStumbleContextWrapper");
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
    }

    public synchronized boolean stopScanning() {
        if (isStopped()) {
            return false;
        }

        mScannerState = ScannerState.STOPPED;

        if (mAppContext instanceof SimulationContext) {
            ((SimulationContext) mAppContext).deactivateSimulation();
        }
        // Reset the application context to the unwrapped version
        mAppContext = mAppContext.getApplicationContext();

        if (AppGlobals.isDebug) {
            ClientLog.d(LOG_TAG, "Scanning stopped");
        }

        mLocationChangeSensor.stop();
        mMotionSensor.stop();

        if (mGPSScanner != null) {
            mGPSScanner.stop();
        }

        if (mWifiScanner != null) {
            mWifiScanner.stop();
        }

        if (mCellScanner != null) {
            mCellScanner.stop();
        }

        mGPSScanner = null;
        mWifiScanner = null;
        mCellScanner = null;

        return true;
    }

    public synchronized boolean isStopped() {
        return mScannerState == ScannerState.STOPPED;
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

    private enum PassiveModeBatteryState {OK, LOW, IGNORE_BATTERY_STATE}
}
