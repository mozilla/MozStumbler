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
import org.mozilla.mozstumbler.service.core.logging.ClientLog;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.GPSScanner;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.IHaltable;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.ISystemClock;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

public class LocationChangeSensor extends BroadcastReceiver implements IHaltable {
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
    private static final String LOG_TAG = LoggerUtil.makeLogTag(BroadcastReceiver.class);
    public static String ACTION_LOCATION_NOT_CHANGING = AppGlobals.ACTION_NAMESPACE + ".LOCATION_UNCHANGING";
    /// Debugging code
    static LocationChangeSensor sDebugInstance;
    private final Context mContext;
    private final Handler mHandler = new Handler();
    private ISystemClock sysClock;
    private int mPrefMotionChangeDistanceMeters;
    private long mPrefMotionChangeTimeWindowMs;
    private final Runnable mCheckTimeout = new Runnable() {
        public void run() {
            if (isTimeWindowForMovementExceeded()) {
                AppGlobals.guiLogInfo("GPS time window exceeded.");
                ClientLog.d(LOG_TAG, "GPS time window exceeded.");
                LocalBroadcastManager.getInstance(mContext).sendBroadcastSync(new Intent(ACTION_LOCATION_NOT_CHANGING));
                return;
            }
            ClientLog.d(LOG_TAG, "No gps timeout yet.");
            scheduleTimeoutCheck(mPrefMotionChangeTimeWindowMs);
        }
    };
    private long mStartTimeMs;
    private Location mLastLocation;
    // Package scoped for testing
    boolean mIsCheckTimeoutPosted;

    public LocationChangeSensor(Context context, BroadcastReceiver callbackReceiver) {
        sDebugInstance = this;
        mContext = context;
        LocalBroadcastManager.getInstance(context).registerReceiver(callbackReceiver,
                new IntentFilter(ACTION_LOCATION_NOT_CHANGING));

        // Bind all services in
        ServiceLocator svcLocator = ServiceLocator.getInstance();
        sysClock = (ISystemClock) svcLocator.getService(ISystemClock.class);
    }
    /// ---

    static final String DEBUG_SEND_UNCHANGING_LOC = "debug-send-unchanging-location";
    public static void debugSendLocationUnchanging() {
        if (sDebugInstance.mLastLocation == null) {
            Toast.makeText(sDebugInstance.mContext, "No location yet", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(GPSScanner.ACTION_GPS_UPDATED);
        intent.putExtra(Intent.EXTRA_SUBJECT, GPSScanner.SUBJECT_NEW_LOCATION);
        intent.putExtra(GPSScanner.NEW_LOCATION_ARG_LOCATION, sDebugInstance.mLastLocation);
        intent.putExtra(DEBUG_SEND_UNCHANGING_LOC, true);
        LocalBroadcastManager.getInstance(sDebugInstance.mContext).sendBroadcastSync(intent);
    }

    boolean isTimeWindowForMovementExceeded() {
        if (mLastLocation == null) {
            final long timeWaited = sysClock.currentTimeMillis() - mStartTimeMs;
            final boolean expired = timeWaited > mPrefMotionChangeTimeWindowMs;
            AppGlobals.guiLogInfo("No loc., is gps wait exceeded:" + expired + " (" + timeWaited / 1000.0 + "s)");
            return expired;
        }

        final long ageLastLocation = sysClock.currentTimeMillis() - mLastLocation.getTime();
        String log = "Last loc. age: " + ageLastLocation / 1000.0 + " s, (" +
                sysClock.currentTimeMillis() / 1000 + "-" + mLastLocation.getTime() / 1000 +
                ", max age: " + mPrefMotionChangeTimeWindowMs / 1000.0 + ")";
        AppGlobals.guiLogInfo(log);
        ClientLog.d(LOG_TAG, log);
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

        scheduleTimeoutCheck(mPrefMotionChangeTimeWindowMs);
    }

    public void stop() {
        removeTimeoutCheck();
        try {
            LocalBroadcastManager.getInstance(mContext).unregisterReceiver(this);
        } catch (Exception e) {
        }
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
            ClientLog.d(LOG_TAG, "Received first location");
            mLastLocation = newPosition;
        } else {
            double dist = mLastLocation.distanceTo(newPosition);
            ClientLog.d(LOG_TAG, "Computed distance: " + dist);
            ClientLog.d(LOG_TAG, "Pref distance: " + mPrefMotionChangeDistanceMeters);

            boolean forceTimeout = intent.hasExtra(DEBUG_SEND_UNCHANGING_LOC);

            // TODO: this pref doesn't take into account the accuracy of the location.
            if (dist > mPrefMotionChangeDistanceMeters) {
                ClientLog.d(LOG_TAG, "Received new location exceeding distance changed in meters pref");
                mLastLocation = newPosition;
            } else if (isTimeWindowForMovementExceeded() || forceTimeout) {
                String log = "Insufficient movement:" + dist + " m, " + mPrefMotionChangeDistanceMeters + " m needed.";
                ClientLog.d(LOG_TAG, log);
                AppGlobals.guiLogInfo(log);
                LocalBroadcastManager.getInstance(mContext).sendBroadcastSync(new Intent(ACTION_LOCATION_NOT_CHANGING));
                removeTimeoutCheck();
                return;
            }
        }

        scheduleTimeoutCheck(mPrefMotionChangeTimeWindowMs);
    }

    private void scheduleTimeoutCheck(long delay) {
        removeTimeoutCheck();

        // Don't schedule it for an exact delay, we want it slightly after this timeout, as the OS can
        // trigger this earlier than requested (by a fraction of a second).
        final long addedDelayMs = 100;
        mHandler.postDelayed(mCheckTimeout, delay + addedDelayMs);
        mIsCheckTimeoutPosted = true;

        ClientLog.d(LOG_TAG, "Scheduled timeout check for " + (delay / 1000.0) + " seconds");
    }

    void removeTimeoutCheck() {
        mHandler.removeCallbacks(mCheckTimeout);
        mIsCheckTimeoutPosted = false;
    }


    Location testing_getLastLocation() {
        return mLastLocation;
    }

    void testing_setTimeoutCheckTime(long timeMs) {
        mPrefMotionChangeTimeWindowMs = timeMs;
    }
}
