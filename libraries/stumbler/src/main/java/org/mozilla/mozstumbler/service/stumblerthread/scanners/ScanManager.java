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
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.log.ILogger;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class ScanManager {
    public static final String ACTION_SCAN_PAUSED_USER_MOTIONLESS = AppGlobals.ACTION_NAMESPACE + ".NOTIFY_USER_MOTIONLESS";
    public static final String ACTION_SCAN_UNPAUSED_USER_MOVED = AppGlobals.ACTION_NAMESPACE + ".NOTIFY_USER_MOVED";

    public static enum ScannerState {
        STOPPED, STARTED, STARTED_BUT_PAUSED_MOTIONLESS;

        public static final String NAMESPACE = "org.mozilla.mozstumbler.scanner.state";

        @Override
        public String toString() {
            return NAMESPACE+ this.name();
        }

        public static ScannerState fromString(String name)  {
            try {
                return ScannerState.valueOf(name.substring(ScannerState.NAMESPACE.length()));
            } catch (IllegalArgumentException iae) {
                return null;
            }
        }
    }

    // Convenience strings so that we don't have to keep invoking tostring()
    private static final String SCANSTATE_STARTED = ScannerState.STARTED.toString();
    private static final String SCANSTATE_STOPPED = ScannerState.STOPPED.toString();
    private static final String SCANSTATE_STARTED_BUT_PAUSED_MOTIONLESS = ScannerState.STARTED_BUT_PAUSED_MOTIONLESS.toString();


    private ILogger Log = (ILogger) ServiceLocator.getInstance().getService(ILogger.class);
    private static final String LOG_TAG = LoggerUtil.makeLogTag(ScanManager.class);

    private static Context mAppContext;
    private Timer mFlushTimer;

    // how often to flush a leftover bundle to the reports table
    // If there is a bundle, and nothing happens for 10sec, then flush it
    // MAX_SCANS_PER_GPS    is 2
    // CELL_MIN_UPDATE_TIME is 1sec
    // WIFI_MIN_UPDATE_TIME is 4sec
    private static final int FLUSH_RATE_MS = 10000; // 10 sec

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



    private ScannerState mScannerState = ScannerState.STOPPED;

    // After DetectUnchangingLocation reports the user is not moving, and the scanning pauses,
    // then use MotionSensor to determine when to wake up and start scanning again.
    private final BroadcastReceiver mDetectUserIdleReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isStopped() || !Prefs.getInstance(mAppContext).isMotionSensorEnabled() ) {
                return;
            }
            pauseScanning();

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

            broadcastScanState(ScannerState.STOPPED);

            startScanning();
            mMotionSensor.stop();

            Intent sendIntent = new Intent(ACTION_SCAN_UNPAUSED_USER_MOVED);
            LocalBroadcastManager.getInstance(mAppContext).sendBroadcastSync(sendIntent);
        }
    };

    private void broadcastScanState(ScannerState scanState) {
        mScannerState = scanState;

        String action = scanState.toString();
        Log.i(LOG_TAG, "Broadcasting scan state == " + action);
        LocalBroadcastManager.getInstance(mAppContext).sendBroadcast(new Intent(action));
    }

    public ScanManager() {}

    public void initContext(Context appCtx) {
        mAppContext = appCtx;
    }


    // By default, if the battery level is low, then the service stops scanning, however the client
    // can disable this and perform more complex logic
    public void setShouldStopPassiveScanningOnBatteryLow(boolean shouldStop) {
        mPassiveModeBatteryState = shouldStop ? PassiveModeBatteryState.OK :
                PassiveModeBatteryState.IGNORE_BATTERY_STATE;
    }

    public void newGpsLocation() {
        if (mPassiveModeBatteryState == PassiveModeBatteryState.LOW) {
            return;
        }

        mWifiScanner.start();
        mCellScanner.start();

        if (mFlushTimer != null) {
            mFlushTimer.cancel();
        }

        Date when = new Date();
        when.setTime(when.getTime() + FLUSH_RATE_MS);
        mFlushTimer = new Timer();
        mFlushTimer.schedule(new TimerTask() {
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
        mStumblingMode = (on) ? ActiveOrPassiveStumbling.PASSIVE_STUMBLING :
                ActiveOrPassiveStumbling.ACTIVE_STUMBLING;
    }

    private synchronized boolean pauseScanning() {
        if (isStopped()) {
            return false;
        }

        broadcastScanState(ScannerState.STARTED_BUT_PAUSED_MOTIONLESS);

        return stopAllScanners();
    }

    public synchronized void startScanning() {
        ClientLog.d(LOG_TAG, "ScanManager::startScanning");

        if (!isStopped()) {
            return;
        }

        broadcastScanState(ScannerState.STARTED);

        if (mLocationChangeSensor == null) {
            mLocationChangeSensor = new LocationChangeSensor(mAppContext, mDetectUserIdleReceiver);
        }
        mLocationChangeSensor.start();

        if (mMotionSensor == null) {
            LocalBroadcastManager.getInstance(mAppContext).registerReceiver(mDetectMotionReceiver,
                    new IntentFilter(MotionSensor.ACTION_USER_MOTION_DETECTED));

            mMotionSensor = new MotionSensor(mAppContext);
        }

        if (AppGlobals.isDebug) {
            // Simulation contexts are only allowed for debug builds.
            Prefs prefs = Prefs.getInstanceWithoutContext();
            if (prefs != null) {
                if (prefs.isSimulateStumble()) {
                    ISimulatorService simSvc  = (ISimulatorService) ServiceLocator.getInstance().getService(ISimulatorService.class);
                    simSvc.startSimulation(mAppContext);

                    ClientLog.d(LOG_TAG, "ScanManager using SimulateStumbleContextWrapper");
                }
            }
        }

        mGPSScanner = new GPSScanner(mAppContext, this);
        mWifiScanner = new WifiScanner(mAppContext);
        mCellScanner = new CellScanner(mAppContext);

        mGPSScanner.start(mStumblingMode);

        if (isPassiveMode()) {
            if (mPassiveModeBatteryChecker == null) {
                mPassiveModeBatteryChecker = new BatteryCheckReceiver(mAppContext, mBatteryCheckCallback);
            }
            mPassiveModeBatteryChecker.start();
        }
    }

    public synchronized boolean stopScanning() {
        if (isStopped()) {
            return false;
        }
        broadcastScanState(ScannerState.STOPPED);
        mMotionSensor.scannerFullyStopped();

        if (mPassiveModeBatteryChecker != null) {
            mPassiveModeBatteryChecker.stop();
        }

        return stopAllScanners();
    }

    private boolean stopAllScanners() {
        ISimulatorService sim = (ISimulatorService) ServiceLocator.getInstance()
                                                            .getService(ISimulatorService.class);
        sim.stopSimulation(); // this is always safe as it's a lazy dynamic proxy

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

        return true;
    }

    public synchronized boolean isStopped() {
        return mScannerState == ScannerState.STOPPED;
    }

    public synchronized boolean isScanning() {
        return mScannerState == ScannerState.STARTED;
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

    private enum PassiveModeBatteryState {OK, LOW, IGNORE_BATTERY_STATE}
}
