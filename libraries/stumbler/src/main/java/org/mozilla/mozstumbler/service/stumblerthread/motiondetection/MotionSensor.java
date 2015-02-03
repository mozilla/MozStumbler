package org.mozilla.mozstumbler.service.stumblerthread.motiondetection;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.SensorManager;
import android.net.wifi.ScanResult;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.Toast;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.WifiScanner;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

import java.util.ArrayList;
import java.util.List;

public class MotionSensor {

    public static final String ACTION_USER_MOTION_DETECTED = AppGlobals.ACTION_NAMESPACE + ".USER_MOVE";
    private static final String LOG_TAG = LoggerUtil.makeLogTag(MotionSensor.class);
    /// Testing code
    private final SensorManager mSensorManager;
    private final Context mAppContext;

    private IMotionSensor motionSensor;
    private static MotionSensor sDebugInstance;

    private List<String> mWifiListWhenStarted = new ArrayList<String>();

WifiScanner mScanner;

    public MotionSensor(Context appCtx) {
        mAppContext = appCtx;

        mSensorManager = (SensorManager) mAppContext.getSystemService(Context.SENSOR_SERVICE);
        if (mSensorManager == null) {
            AppGlobals.guiLogInfo("No sensor manager.");
            return;
        }

        sDebugInstance = this;

        motionSensor = SignificantMotionSensor.getSensor(mAppContext);

        // If no TYPE_SIGNIFICANT_MOTION is available, use alternate means to sense motion
        if (motionSensor == null) {
            motionSensor = new LegacyMotionSensor(mAppContext);
            AppGlobals.guiLogInfo("Device has legacy motion sensor.");
        }
    }

    public static void debugMotionDetected() {
        if (!sDebugInstance.motionSensor.isActive()) {
            return;
        }
        AppGlobals.guiLogInfo("TEST: Major motion detected.");
        LocalBroadcastManager.getInstance(sDebugInstance.mAppContext).sendBroadcastSync(new Intent(ACTION_USER_MOTION_DETECTED));
    }

    public static boolean hasSignificantMotionSensor(Context appCtx) {
        return SignificantMotionSensor.getSensor(appCtx) != null;
    }

    void unregister() {
        try {
            LocalBroadcastManager.getInstance(mAppContext).unregisterReceiver(b1);
        } catch (Exception ex) {}
        try {
            LocalBroadcastManager.getInstance(mAppContext).unregisterReceiver(b2);
        } catch (Exception ex) {}
    }

    BroadcastReceiver b1 = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            List<ScanResult> results = intent.getParcelableArrayListExtra(WifiScanner.ACTION_WIFIS_SCANNED_ARG_RESULTS);
            for (ScanResult r : results){
                mWifiListWhenStarted.add(r.BSSID);
            }
            mScanner.stop();
            unregister();
        }
    };
    BroadcastReceiver b2 = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            List<ScanResult> results = intent.getParcelableArrayListExtra(WifiScanner.ACTION_WIFIS_SCANNED_ARG_RESULTS);
            int common = 0;
            for (ScanResult r : results) {
                if (mWifiListWhenStarted.contains(r.BSSID)) {
                    common++;
                }
            }
            double kPercentMatch = 0.5;
            // if more than 50% match, then we assume the user hasn't moved
            if (common >= (Math.max(mWifiListWhenStarted.size(), results.size()) * kPercentMatch)) {
                Intent sendIntent = new Intent(LocationChangeSensor.ACTION_LOCATION_NOT_CHANGING);
                LocalBroadcastManager.getInstance(mAppContext).sendBroadcastSync(sendIntent);
                Toast.makeText(mAppContext, "same wifi", Toast.LENGTH_SHORT).show();
            }

            // unregister and stop the scanner
            mScanner.stop();
            unregister();
        }
    };

    public void start() {
        motionSensor.start();
        if (mScanner == null) {
            mScanner = new WifiScanner(mAppContext);
        }
        unregister();
        LocalBroadcastManager.getInstance(mAppContext).registerReceiver(b1, new IntentFilter(WifiScanner.ACTION_WIFIS_SCANNED));
        mScanner.start(AppGlobals.ActiveOrPassiveStumbling.ACTIVE_STUMBLING);
    }

    public void stop() {
        motionSensor.stop();
        if (mScanner != null) {
            LocalBroadcastManager.getInstance(mAppContext).registerReceiver(b2, new IntentFilter(WifiScanner.ACTION_WIFIS_SCANNED));
            mScanner.start(AppGlobals.ActiveOrPassiveStumbling.ACTIVE_STUMBLING);
        }

        Runnable runnable = new Runnable() {
            public void run() {
                if (mScanner != null) {
                    mScanner.stop();
                }
                unregister();
            }
        };
        final long kWaitForWifiResultsMs = 20 * 1000;
        new Handler().postDelayed(runnable, kWaitForWifiResultsMs);
    }
}
