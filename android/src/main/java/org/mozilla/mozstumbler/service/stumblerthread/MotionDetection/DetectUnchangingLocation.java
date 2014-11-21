package org.mozilla.mozstumbler.service.stumblerthread.motiondetection;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.GPSScanner;

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

    public DetectUnchangingLocation(Context context, BroadcastReceiver callbackReceiver) {
        mContext = context;
        LocalBroadcastManager.getInstance(context).registerReceiver(callbackReceiver,
                new IntentFilter(ACTION_LOCATION_NOT_CHANGING));
    }

    boolean isTimeWindowForMovementExceeded() {
        if (mLastLocation == null) {
            return System.currentTimeMillis() - mStartTimeMs < INITIAL_DELAY_TO_WAIT_FOR_GPS_FIX_MS;
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
        LocalBroadcastManager.getInstance(mContext).registerReceiver(this,  intentFilter);
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

    public void quickCheckAfterMotionSensorMovement() {
        // False positives for movement are common.
        // In this case, do a quick check, there is a significant possibility that the movement detected
        // is not enough to change the GPS position.
        // Don't need to wait the full time window before concluding the user has not moved.
        final int kWaitTimeMs = 1000 * 20;
        mDoSingleLocationCheck = true;
        mHandler.postDelayed(mCheckTimeout, kWaitTimeMs);
    }
}
