package org.mozilla.mozstumbler.service.stumblerthread.motiondetection;

import android.content.Context;
import android.content.Intent;
import android.hardware.SensorManager;
import android.support.v4.content.LocalBroadcastManager;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.log.ILogger;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

public class MotionSensor {

    public static final String ACTION_USER_MOTION_DETECTED = AppGlobals.ACTION_NAMESPACE + ".USER_MOVE";
    private static final String LOG_TAG = LoggerUtil.makeLogTag(MotionSensor.class);
    private static final ILogger Log = (ILogger) ServiceLocator.getInstance().getService(ILogger.class);

    /// Testing code
    private final SensorManager mSensorManager;
    private final Context mAppContext;

    private IMotionSensor motionSensor;
    private static MotionSensor sDebugInstance;

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
        }
        setTypeFromPrefs();
    }

    private void setTypeFromPrefs() {
        if (Prefs.getInstance(mAppContext).getIsMotionSensorTypeSignificant()) {
            motionSensor = SignificantMotionSensor.getSensor(mAppContext);
        }
    }

    // Call when the scanning has stopped (not when paused).
    // This class can use this event to update its state
    public void scannerFullyStopped() {
        setTypeFromPrefs();
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

    public void start() {
        motionSensor.start();
    }

    public void stop() {
        motionSensor.stop();
    }
}
