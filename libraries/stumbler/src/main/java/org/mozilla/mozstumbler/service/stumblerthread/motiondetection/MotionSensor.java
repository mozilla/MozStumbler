package org.mozilla.mozstumbler.service.stumblerthread.motiondetection;

import android.content.Context;
import android.content.Intent;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.log.ILogger;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

import java.util.HashMap;
import java.util.Map;

public class MotionSensor {

    public static final String ACTION_USER_MOTION_DETECTED = AppGlobals.ACTION_NAMESPACE + ".USER_MOVE";
    private static final String LOG_TAG = LoggerUtil.makeLogTag(MotionSensor.class);
    private static final ILogger Log = (ILogger) ServiceLocator.getInstance().getService(ILogger.class);

    /// Testing code
    private final SensorManager mSensorManager;
    private final Context mAppContext;
    private IMotionSensor motionSensor;
    private static MotionSensor sDebugInstance;
    Handler mHandler = new Handler();

    boolean mIsStartMotionSensorRunnableScheduled;
    Runnable mStartMotionSensorRunnable = new Runnable() {
        public void run() {
            mIsStartMotionSensorRunnableScheduled = false;
            motionSensor.start();
        }
    };

    // To handle the scenario where a person's GPS location is unchanging, yet they keep moving
    // and triggering the accelerometer (walking with the device in-pocket), sleep for progressively
    // longer periods after repeatedly triggering the motion sensor.
    // Once the GPS location changes, this delay is reset back to zero.
    private static final Map<Integer, Long> sTimesNotMovedAndSleepMs = new HashMap<Integer, Long>();
    static {
        sTimesNotMovedAndSleepMs.put(5, 30 * 1000L);
        sTimesNotMovedAndSleepMs.put(25, 60 * 1000L);
        sTimesNotMovedAndSleepMs.put(50, 120 * 1000L);
    }


    public MotionSensor(Context appCtx) {
        mAppContext = appCtx;
        mQuickCheck = new QuickCheckGPSLocationChanged(mAppContext);

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

    private boolean isSignificantMotionSensorEnabled() {
        return hasSignificantMotionSensor(mAppContext) &&
               Prefs.getInstance(mAppContext).getIsMotionSensorTypeSignificant();
    }

    public void start() {
        if (!Prefs.getInstance(mAppContext).getIsMotionSensorEnabled() ||
            motionSensor.isActive() ||
            mIsStartMotionSensorRunnableScheduled) {
            return;
        }

        mHandler.removeCallbacks(null);

        long delay = 0;
        if (!isSignificantMotionSensorEnabled()) {
            for (Map.Entry<Integer, Long> entry : sTimesNotMovedAndSleepMs.entrySet()) {
                if (mQuickCheck.getSuccessiveTimesNotMoved() >= entry.getKey()) {
                    delay = entry.getValue();
                    AppGlobals.guiLogInfo("Sleep for " + delay + "ms", "green", false, false);
                }
            }
        }
        mIsStartMotionSensorRunnableScheduled = true;
        mHandler.postDelayed(mStartMotionSensorRunnable, delay);

        mQuickCheck.updateLocation();
    }

    public void stop() {
        if (!motionSensor.isActive()) {
            return;
        }

        mHandler.removeCallbacks(null);
        mIsStartMotionSensorRunnableScheduled = false;

        motionSensor.stop();
        mQuickCheck.start();
    }

    public void scannerFullyStopped() {
        mQuickCheck.resetSuccessiveTimesNotMovedCount();
    }

    QuickCheckGPSLocationChanged mQuickCheck;
    private static class QuickCheckGPSLocationChanged {
        final Context mContext;
        final Handler handler = new Handler();
        private Location mLastLocation;
        private final long mMinMotionChangeDistanceMeters = 30;
        private int successiveTimesNotMoved;

        LocationListener mListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                stop();
                if (mLastLocation == null) {
                    return;
                }
                double dist = mLastLocation.distanceTo(location);
                if (dist < mMinMotionChangeDistanceMeters) {
                    successiveTimesNotMoved++;
                    Intent sendIntent = new Intent(LocationChangeSensor.ACTION_LOCATION_NOT_CHANGING);
                    LocalBroadcastManager.getInstance(mContext).sendBroadcastSync(sendIntent);
                    AppGlobals.guiLogInfo("not moved", "green", true, false);
                } else {
                    successiveTimesNotMoved = 0;
                }
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {}
            public void onProviderEnabled(String provider) {}
            public void onProviderDisabled(String provider) {}
        };

        public QuickCheckGPSLocationChanged(Context mAppContext) {
            mContext = mAppContext;
        }

        void updateLocation() {
            stop();

            LocationManager lm = (LocationManager)mContext.getSystemService(Context.LOCATION_SERVICE);
            mLastLocation = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        }

        void start() {
            stop();

            if (mLastLocation == null) {
                return;
            }
            LocationManager lm = (LocationManager)mContext.getSystemService(Context.LOCATION_SERVICE);
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mListener);

            handler.postDelayed(new Runnable() {
                public void run() {
                    stop();
                }
            }, 20 * 1000);
        }

        void stop() {
            LocationManager lm = (LocationManager)mContext.getSystemService(Context.LOCATION_SERVICE);
            lm.removeUpdates(mListener);
            handler.removeCallbacks(null);
        }

        public void resetSuccessiveTimesNotMovedCount() {
            successiveTimesNotMoved = 0;
        }

        public int getSuccessiveTimesNotMoved() {
            return successiveTimesNotMoved;
        }
    }
}
