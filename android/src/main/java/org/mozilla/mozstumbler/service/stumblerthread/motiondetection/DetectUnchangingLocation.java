package org.mozilla.mozstumbler.service.stumblerthread.motiondetection;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.GPSScanner;

// This class is a bit confusing because of 2 checks that need to take place.
// 1) One check happens when a gps event arrives, to see if the user moved x meters in t seconds.
// 2) The other is a timeout in case no gps event arrives during time t.
//
// Both cases broadcast the same intent that the user is not moving.
//
// This class never broadcasts the user _is_ moving. That case is handled like this:
//  - scanning starts, user is assumed moving
//  - DetectUnchangingLocation says movement stopped, scanning paused
//  - Motion detector waits for motion, if motion detected, scanning starts (user assumed to be moving)
//
public class DetectUnchangingLocation extends BroadcastReceiver {
    private static final String LOG_TAG = AppGlobals.makeLogTag(BroadcastReceiver.class.getSimpleName());
    private final Context mContext;
    private Location mLastLocation;
    private final Handler mHandler = new Handler();
    private final long INITIAL_DELAY_TO_WAIT_FOR_GPS_FIX_MS = 1000 * 60 * 5; // 5 mins
    private int mPrefMotionChangeDistanceMeters;
    private long mPrefMotionChangeTimeWindowMs;
    private long mStartTimeMs;
    private boolean mDoSingleLocationCheck;
    private static String ACTION_LOCATION_NOT_CHANGING = AppGlobals.ACTION_NAMESPACE + ".LOCATION_UNCHANGING";

    private final Runnable mCheckTimeout = new Runnable() {
        public void run() {
            if (!isTimeWindowForMovementExceeded()) {
                scheduleTimeoutCheck();
                return;
            }
            AppGlobals.guiLogInfo("No GPS in time window.");
            LocalBroadcastManager.getInstance(mContext).sendBroadcastSync(new Intent(ACTION_LOCATION_NOT_CHANGING));
        }
    };

    /// Debugging code
    static DetectUnchangingLocation sDebugInstance;
    public static void debugSendLocationUnchanging() {
        sDebugInstance.mDoSingleLocationCheck = true;
        Intent intent = new Intent(GPSScanner.ACTION_GPS_UPDATED);
        intent.putExtra(Intent.EXTRA_SUBJECT, GPSScanner.SUBJECT_NEW_LOCATION);
        intent.putExtra(GPSScanner.NEW_LOCATION_ARG_LOCATION, sDebugInstance.mLastLocation);
        LocalBroadcastManager.getInstance(sDebugInstance.mContext).sendBroadcastSync(intent);
    }
    /// ---

    public DetectUnchangingLocation(Context context, BroadcastReceiver callbackReceiver) {
        sDebugInstance = this;
        mContext = context;
        LocalBroadcastManager.getInstance(context).registerReceiver(callbackReceiver,
                new IntentFilter(ACTION_LOCATION_NOT_CHANGING));
    }

    boolean isTimeWindowForMovementExceeded() {
        if (mLastLocation == null) {
            return System.currentTimeMillis() - mStartTimeMs > INITIAL_DELAY_TO_WAIT_FOR_GPS_FIX_MS;
        }

        return System.currentTimeMillis() - mPrefMotionChangeTimeWindowMs > mLastLocation.getTime();
    }

    public void start() {
        mLastLocation = null;
        mStartTimeMs = System.currentTimeMillis();
        mPrefMotionChangeDistanceMeters = Prefs.getInstance().getMotionChangeDistanceMeters();
        mPrefMotionChangeTimeWindowMs = 1000 * Prefs.getInstance().getMotionChangeTimeWindowSeconds();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(GPSScanner.ACTION_GPS_UPDATED);
        LocalBroadcastManager.getInstance(mContext).registerReceiver(this, intentFilter);
        mHandler.postDelayed(mCheckTimeout, INITIAL_DELAY_TO_WAIT_FOR_GPS_FIX_MS);
    }

    public void stop() {
        mHandler.removeCallbacks(mCheckTimeout);
        try {
            LocalBroadcastManager.getInstance(mContext).unregisterReceiver(this);
        } catch (Exception e) {}
    }

    public void onReceive(Context context, Intent intent) {
        if (intent == null || !intent.getAction().equals(GPSScanner.ACTION_GPS_UPDATED)) {
            return;
        }

        String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);
        if (subject == null || !subject.equals(GPSScanner.SUBJECT_NEW_LOCATION)) {
            return;
        }

        Location newPosition = intent.getParcelableExtra(GPSScanner.NEW_LOCATION_ARG_LOCATION);
        if (newPosition == null) {
            return;
        }

        if (mLastLocation == null) {
            mLastLocation = newPosition;
        } else {
            double dist = mLastLocation.distanceTo(newPosition);
            if (dist > mPrefMotionChangeDistanceMeters) {
                mLastLocation = newPosition;
            } else if (isTimeWindowForMovementExceeded() || mDoSingleLocationCheck) {
                AppGlobals.guiLogInfo("Insufficient movement:" + dist + " m, " + mPrefMotionChangeDistanceMeters + " m needed.");
                mDoSingleLocationCheck = false;
                LocalBroadcastManager.getInstance(mContext).sendBroadcastSync(new Intent(ACTION_LOCATION_NOT_CHANGING));
                return;
            }
        }

        mDoSingleLocationCheck = false;
        scheduleTimeoutCheck();
    }

    private void scheduleTimeoutCheck() {
        try {
            mHandler.removeCallbacks(mCheckTimeout);
        } catch (Exception e) {}

        // Don't schedule it for exactly mPrefMotionChangeTimeWindowMs, we want it slightly after this timeout
        final long addedDelay = 2 * 1000;
        mHandler.postDelayed(mCheckTimeout, mPrefMotionChangeTimeWindowMs + addedDelay);
    }

    public void quickCheckForFalsePositiveAfterMotionSensorMovement() {
        // False positives for movement are common, particularly for the legacy sensor.
        // Without this check, a false positive cause scanning to run for mPrefMotionChangeTimeWindowMs (2 mins default).
        // We don't need to wait the full time window before concluding the user has not moved.
        // This check waits 20 seconds for location change, if not, go back to paused.
        final int kWaitTimeMs = 1000 * 20;
        mDoSingleLocationCheck = true;
        mHandler.postDelayed(mCheckTimeout, kWaitTimeMs);
    }
}
