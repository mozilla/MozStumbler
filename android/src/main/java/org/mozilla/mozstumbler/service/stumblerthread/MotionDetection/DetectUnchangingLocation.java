package org.mozilla.mozstumbler.service.stumblerthread.motiondetection;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.GPSScanner;

public class DetectUnchangingLocation extends BroadcastReceiver {
    private final Context mContext;
    private Location mLastLocation;
    private double mLocationChangeMeters;
    private final Handler mHandler = new Handler();
    // If no location change in this time window, then the user is not moving
    private final long TIME_WINDOW_FOR_LOCATION_CHANGE_MS = 1000 * 120;

    private static String ACTION_LOCATION_NOT_CHANGING = AppGlobals.ACTION_NAMESPACE + ".LOCATION_UNCHANGING";
    
    private final Runnable mCheckGps = new Runnable() {
        public void run() {
            mHandler.postDelayed(mCheckGps, TIME_WINDOW_FOR_LOCATION_CHANGE_MS);

            if (!isLocationTooOld() && !isLocationTheSame()) {
                return;
            }

            LocalBroadcastManager.getInstance(mContext).sendBroadcastSync(new Intent(ACTION_LOCATION_NOT_CHANGING));
        }
    };

    public DetectUnchangingLocation(Context context, BroadcastReceiver callbackReceiver) {
        mContext = context;
        LocalBroadcastManager.getInstance(context).registerReceiver(callbackReceiver,
                new IntentFilter(ACTION_LOCATION_NOT_CHANGING));
    }

    boolean isLocationTooOld() {
        if (mLastLocation == null) {
            return false;
        }
        return System.currentTimeMillis() - TIME_WINDOW_FOR_LOCATION_CHANGE_MS > mLastLocation.getTime();
    }

    boolean isLocationTheSame() {
        return mLocationChangeMeters < 50;
    }

    public void start() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(GPSScanner.ACTION_GPS_UPDATED);
        LocalBroadcastManager.getInstance(mContext).registerReceiver(this,  intentFilter);
        mHandler.postDelayed(mCheckGps, TIME_WINDOW_FOR_LOCATION_CHANGE_MS);
    }

    public void stop() {
        mHandler.removeCallbacks(mCheckGps);
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(this);
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

        if (mLastLocation!= null) {
            mLocationChangeMeters = newPosition.distanceTo(mLastLocation);
        }
        mLastLocation = newPosition;

        mHandler.removeCallbacks(mCheckGps);
        mHandler.postDelayed(mCheckGps, TIME_WINDOW_FOR_LOCATION_CHANGE_MS);
    }

    public void quickCheckAfterMotionSensorMovement() {
        if (Build.VERSION.SDK_INT >= 18) {
            return;
        }

        // Without significant motion detection (less than Android 4.3), false positives for movement are common.
        // In this case, do a quick check, there is a significant possibility that the movement after idle
        // is not enough to change the GPS position.
        // Don't need to wait the full time window before concluding the user has not moved.
        final int kWaitTimeMs = 1000 * 20;
        mHandler.postDelayed(mCheckGps, kWaitTimeMs);
    }
}
