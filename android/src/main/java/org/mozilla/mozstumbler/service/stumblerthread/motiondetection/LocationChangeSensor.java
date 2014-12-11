package org.mozilla.mozstumbler.service.stumblerthread.motiondetection;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.Toast;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.core.logging.Log;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.GPSScanner;

import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.ISystemClock;

public class LocationChangeSensor extends BroadcastReceiver {
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
    private static final String LOG_TAG = AppGlobals.makeLogTag(BroadcastReceiver.class.getSimpleName());
    private final Context mContext;
    private final Handler mHandler = new Handler();
    private ISystemClock sysClock;

    private int mPrefMotionChangeDistanceMeters;
    private long mPrefMotionChangeTimeWindowMs;
    private long mStartTimeMs;
    private boolean mDoSingleLocationCheck;
    public static String ACTION_LOCATION_NOT_CHANGING = AppGlobals.ACTION_NAMESPACE + ".LOCATION_UNCHANGING";

    // attributes used to track the state of the LocationChangeSensor
    boolean checkTimeScheduled = false;
    Location mLastLocation;

    private final Runnable mCheckTimeout = new Runnable() {
        public void run() {
            try {
                if (isTimeWindowForMovementExceeded()) {
                    AppGlobals.guiLogInfo("No GPS in time window.");
                    Log.d(LOG_TAG, "No GPS in time window.");
                    LocalBroadcastManager.getInstance(mContext).sendBroadcastSync(new Intent(ACTION_LOCATION_NOT_CHANGING));
                    return;
                }
                Log.d(LOG_TAG, "We're getting GPS readings in a timely manner. Nothing to see here.");
                scheduleTimeoutCheck();
            } finally {
                checkTimeScheduled = false;
            }
        }
    };

    /// Debugging code
    static LocationChangeSensor sDebugInstance;
    public static void debugSendLocationUnchanging() {
        if (sDebugInstance.mLastLocation == null) {
            Toast.makeText(sDebugInstance.mContext, "No location yet", Toast.LENGTH_SHORT).show();
            return;
        }

        sDebugInstance.mDoSingleLocationCheck = true;
        Intent intent = new Intent(GPSScanner.ACTION_GPS_UPDATED);
        intent.putExtra(Intent.EXTRA_SUBJECT, GPSScanner.SUBJECT_NEW_LOCATION);
        intent.putExtra(GPSScanner.NEW_LOCATION_ARG_LOCATION, sDebugInstance.mLastLocation);
        LocalBroadcastManager.getInstance(sDebugInstance.mContext).sendBroadcastSync(intent);
    }
    /// ---

    public LocationChangeSensor(Context context, BroadcastReceiver callbackReceiver) {
        sDebugInstance = this;
        mContext = context;
        LocalBroadcastManager.getInstance(context).registerReceiver(callbackReceiver,
                new IntentFilter(ACTION_LOCATION_NOT_CHANGING));

        // Bind all services in
        ServiceLocator svcLocator = ServiceLocator.getInstance();
        sysClock = (ISystemClock) svcLocator.getService(ISystemClock.class);
    }

    boolean isTimeWindowForMovementExceeded() {
        if (mLastLocation == null) {
            final long timeWaited = sysClock.currentTimeMillis() - mStartTimeMs;
            final boolean expired = timeWaited > mPrefMotionChangeTimeWindowMs;
            AppGlobals.guiLogInfo("No loc., is gps wait exceeded:" + expired + " (" + timeWaited/1000.0 + "s)");
            return expired;
        }

        final long ageLastLocation = sysClock.currentTimeMillis() - mLastLocation.getTime();
        AppGlobals.guiLogInfo("Last loc. age: " + ageLastLocation / 1000.0 + " s, (max age: " +
                                mPrefMotionChangeTimeWindowMs/1000.0 + ")");
        return ageLastLocation > mPrefMotionChangeTimeWindowMs;
    }

    public void start() {
        mLastLocation = null;
        mStartTimeMs = sysClock.currentTimeMillis();

        Prefs prefs = Prefs.getInstanceWithoutContext();
        if (prefs == null) {
            return;
        }

        mPrefMotionChangeDistanceMeters = prefs.getMotionChangeDistanceMeters();
        mPrefMotionChangeTimeWindowMs = 1000 * prefs.getMotionChangeTimeWindowSeconds();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(GPSScanner.ACTION_GPS_UPDATED);
        LocalBroadcastManager.getInstance(mContext).registerReceiver(this, intentFilter);
        mHandler.postDelayed(mCheckTimeout, mPrefMotionChangeTimeWindowMs);
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

        // Set the location time to current time instead of GPS time, as the remainder of the code
        // compares this time to current time, and we don't want 2 different time systems compared
        newPosition.setTime(sysClock.currentTimeMillis());

        if (mLastLocation == null) {
            Log.d(LOG_TAG, "Received first location");
            mLastLocation = newPosition;
        } else {
            double dist = mLastLocation.distanceTo(newPosition);
            Log.d(LOG_TAG, "Computed distance: " + dist);
            Log.d(LOG_TAG, "Pref distance: " + mPrefMotionChangeDistanceMeters);

            // TODO: this pref doesn't take into account the accuracy of the location.
            if (dist > mPrefMotionChangeDistanceMeters) {
                Log.d(LOG_TAG, "Received new location exceeding distance changed in meters pref");
                mLastLocation = newPosition;
            } else if (isTimeWindowForMovementExceeded() || mDoSingleLocationCheck) {
                Log.d(LOG_TAG, "New GPS location received, but time window exceeded.");
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
        Log.d(LOG_TAG, "Scheduled timeout check for " + (addedDelay/1000) + " seconds");

        checkTimeScheduled = true;
        mHandler.postDelayed(mCheckTimeout, mPrefMotionChangeTimeWindowMs + addedDelay);
    }

    public void quickCheckForFalsePositiveAfterMotionSensorMovement() {
        // False positives for movement are common, particularly for the legacy sensor.
        // Without this check, a false positive cause scanning to run for mPrefMotionChangeTimeWindowMs (2 mins default).
        // We don't need to wait the full time window before concluding the user has not moved.
        // This check waits 20 seconds for location change, if not, go back to paused.
        final int kWaitTimeMs = 1000 * 20;
        mDoSingleLocationCheck = true;
        Log.d(LOG_TAG, "Scheduled timeout check for " + (kWaitTimeMs/1000) + " seconds");

        mHandler.postDelayed(mCheckTimeout, kWaitTimeMs);
    }
}
