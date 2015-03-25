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
import org.mozilla.mozstumbler.svclocator.services.ISystemClock;
import org.mozilla.mozstumbler.svclocator.services.log.ILogger;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

public class MotionSensor {

    public static final String ACTION_USER_MOTION_DETECTED = AppGlobals.ACTION_NAMESPACE + ".USER_MOVE";
    private static final String LOG_TAG = LoggerUtil.makeLogTag(MotionSensor.class);
    private static final ILogger Log = (ILogger) ServiceLocator.getInstance().getService(ILogger.class);
    static final long TIME_UNTIL_GPS_GOES_COLD = 2 * 60 * 60 * 1000; // 2 hours
    private final NetworkLocationChangeDetector mNetworkLocationChangeDetector = new NetworkLocationChangeDetector();
    /// Testing code
    private final SensorManager mSensorManager;
    private final Context mAppContext;
    private IMotionSensor mMotionSensor;
    private static MotionSensor sDebugInstance;
    Handler mHandler = new Handler();

    boolean mIsStartMotionSensorRunnableScheduled;
    Runnable mStartMotionSensorRunnable = new Runnable() {
        public void run() {
            mIsStartMotionSensorRunnableScheduled = false;
            mMotionSensor.start();
        }
    };

    public MotionSensor(Context appCtx) {
        mAppContext = appCtx;
        mFalsePositiveFilter = new FalsePositiveFilter(mAppContext);

        mSensorManager = (SensorManager) mAppContext.getSystemService(Context.SENSOR_SERVICE);
        if (mSensorManager == null) {
            AppGlobals.guiLogInfo("No sensor manager.");
            return;
        }

        sDebugInstance = this;

        setTypeFromPrefs();
    }

    private void setTypeFromPrefs() {
        if (Prefs.getInstance(mAppContext).isMotionSensorTypeSignificant()) {
            mMotionSensor = SignificantMotionSensor.getSensor(mAppContext);
        } else {
            mMotionSensor = new LegacyMotionSensor(mAppContext);
        }
    }

    // Call when the scanning has stopped (not when paused).
    // This class can use this event to update its state
    public void scannerFullyStopped() {
        setTypeFromPrefs();
        mFalsePositiveFilter.reset();
    }

    public static void debugMotionDetected() {
        if (!sDebugInstance.mMotionSensor.isActive()) {
            return;
        }
        AppGlobals.guiLogInfo("TEST: Major motion detected.");
        LocalBroadcastManager.getInstance(sDebugInstance.mAppContext).sendBroadcastSync(new Intent(ACTION_USER_MOTION_DETECTED));
    }

    public static boolean hasSignificantMotionSensor(Context appCtx) {
        return SignificantMotionSensor.getSensor(appCtx) != null;
    }

    public void start() {
        if (!Prefs.getInstance(mAppContext).isMotionSensorEnabled() ||
                mMotionSensor.isActive() ||
                mIsStartMotionSensorRunnableScheduled) {
            return;
        }

        mHandler.removeCallbacks(mStartMotionSensorRunnable);

        long delayMs = Prefs.getInstance(mAppContext).getMotionDetectionMinPauseTime() * 1000;
        final long pausedMs = mFalsePositiveFilter.timeSinceInitialGPS();
        if (pausedMs < 5 * 60 * 1000) {
            // The paused motion sensor has a huge benefit in the case where I am walking around frequently
            // in an office, and keep triggering motion with no location change. However, I don't like that when
            // the motion sensor starts the first time, it sleeps the full amount. For example, I could be
            // actively stumbling, get stopped at a streetlight, scanning pauses for a forced 20s, and I get
            // an unnecessary gap in the data. This code ensures that the pause time is ramped up over a 5 min
            // period. So, the first pause is 1/5 of the <>MinPauseTime, at 2 mins of no location change
            // the pause time is 2/5. After 5 mins, the full pause time is used.
            final double mins = (pausedMs < 60 * 1000)? 1 : pausedMs / 1000 / 60.0; // 1.0 to 5.0
            delayMs *= mins / 5.0;
        }

        // During this sleep period, all motion is ignored, this ensures that even if the motion sensor
        // keeps falsely triggering, waking the scanning, and then pausing again, there will still
        // be some guaranteed battery savings because of this sleep.
        AppGlobals.guiLogInfo("Sleep for " + delayMs + "ms", "green", false, false);

        mIsStartMotionSensorRunnableScheduled = true;
        mHandler.postDelayed(mStartMotionSensorRunnable, delayMs);

        mFalsePositiveFilter.stop();

        mNetworkLocationChangeDetector.start(mAppContext);
    }

    public void stop() {
        if (!mMotionSensor.isActive()) {
            return;
        }

        mHandler.removeCallbacks(null);
        mIsStartMotionSensorRunnableScheduled = false;

        mMotionSensor.stop();

        if (mFalsePositiveFilter.timeSinceInitialGPS() < TIME_UNTIL_GPS_GOES_COLD) {
            mFalsePositiveFilter.start();
        }

        mNetworkLocationChangeDetector.stop();
    }

    static class NetworkLocationChangeDetector {
        Context mContext;
        private static final float DIST_THRESHOLD_M = 30.0f;
        private static final long TIME_INTERVAL_MS = 30000;
        private Location mLastLocation;

        private LocationListener mNetworkLocationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                if (mLastLocation == null) {
                    mLastLocation = location;
                    return;
                }

                AppGlobals.guiLogInfo("MotionSensor.NetworkLocationChangeDetector triggered. (" +
                    Math.round(location.distanceTo(mLastLocation)) + "m)");
                Intent sendIntent = new Intent(ACTION_USER_MOTION_DETECTED);
                LocalBroadcastManager.getInstance(mContext).sendBroadcastSync(sendIntent);
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {}
            public void onProviderEnabled(String provider) {}
            public void onProviderDisabled(String provider) {}
        };

        public void start(Context c) {
            mContext = c;
            mLastLocation = null;
            LocationManager lm = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
            if (!lm.getAllProviders().contains(LocationManager.NETWORK_PROVIDER)) {
                return;
            }

            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, TIME_INTERVAL_MS, DIST_THRESHOLD_M, mNetworkLocationListener);
        }

        public void stop() {
            if (mContext == null) {
                return;
            }
            LocationManager lm = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
            lm.removeUpdates(mNetworkLocationListener);
        }
    }


    // Try to filter false positives by quickly checking to see if the user location has changed.
    // If the GPS has gone cold (~2 hours), then stop using this filter, as this class relies
    // on getting a GPS in 30 seconds or less.
    FalsePositiveFilter mFalsePositiveFilter;
    static class FalsePositiveFilter {
        private final Context mContext;
        private final Handler mHandler = new Handler();
        private final ISystemClock mClock = (ISystemClock) ServiceLocator.getInstance().getService(ISystemClock.class);
        Location mLastLocation;

        // Movement less than this is considered a false positive
        private final int MIN_DISTANCE_CHANGE_METERS = 10;

        // Only wait 30s for a location, after this, stop trying to filter the false positive
        // as without access to a GPS loc in 30s, this class can't quickly determine movement.
        private final long MAX_TIME_TO_WAIT_FOR_GPS_MS =  30 * 1000;

        // After the accelerometer triggers, the FalsePositiveFilter needs to wait a few seconds to
        // allow for user movement before checking location.
        private final long DELAY_START_TO_ALLOW_MOVEMENT_MS = 5000;

        private final Runnable mStopListeningTimer = new Runnable() {
            public void run() {
                stop();
            }
        };

        private final Runnable mStartListeningTimer = new Runnable() {
            public void run() {
                LocationManager lm = (LocationManager)mContext.getSystemService(Context.LOCATION_SERVICE);
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mListener);
            }
        };

        LocationListener mListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                stop();
                if (mLastLocation == null) {
                    return;
                }
                double dist = mLastLocation.distanceTo(location);
                AppGlobals.guiLogInfo("Distance moved: " + String.format("%.1f", dist) + " m", "green", false, false);

                if (dist < MIN_DISTANCE_CHANGE_METERS) {
                    AppGlobals.guiLogInfo("False positive. not moved.", "green", true, false);
                    Intent sendIntent = new Intent(LocationChangeSensor.ACTION_LOCATION_NOT_CHANGING);
                    LocalBroadcastManager.getInstance(mContext).sendBroadcastSync(sendIntent);
                } else {
                    reset();
                }
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {}
            public void onProviderEnabled(String provider) {}
            public void onProviderDisabled(String provider) {}
        };

        FalsePositiveFilter(Context mAppContext) {
            mContext = mAppContext;
        }

        long timeSinceInitialGPS() {
            if (mLastLocation == null) {
                return 0;
            }
            return mClock.currentTimeMillis() - mLastLocation.getTime();
        }

        void start() {
            stop();

            if (mLastLocation == null) {
                AppGlobals.guiLogInfo("Updated location for false pos. filter");
                LocationManager lm = (LocationManager)mContext.getSystemService(Context.LOCATION_SERVICE);
                mLastLocation = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (mLastLocation != null) {
                    // set the time to now, as if the location at *now* is this GPS location
                    mLastLocation.setTime(mClock.currentTimeMillis());
                }
            }

            if (mLastLocation == null) {
                return;
            }

            mHandler.postDelayed(mStartListeningTimer, DELAY_START_TO_ALLOW_MOVEMENT_MS);
            mHandler.postDelayed(mStopListeningTimer, MAX_TIME_TO_WAIT_FOR_GPS_MS);
        }

        void stop() {
            LocationManager lm = (LocationManager)mContext.getSystemService(Context.LOCATION_SERVICE);
            lm.removeUpdates(mListener);
            mHandler.removeCallbacks(mStopListeningTimer);
            mHandler.removeCallbacks(mStartListeningTimer);
        }

        void reset() {
            mLastLocation = null;
        }
    }
}
