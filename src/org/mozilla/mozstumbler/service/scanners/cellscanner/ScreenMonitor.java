package org.mozilla.mozstumbler.service.scanners.cellscanner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.telephony.CellLocation;
import android.util.Log;

/**
 * Determine whether the cell location is updated when the screen is off
 * https://code.google.com/p/android/issues/detail?id=10931
 */
public class ScreenMonitor {
    private static final String LOGTAG = "ScreenOffWorkaround";

    private static final String PREFS_FILE = ScreenMonitor.class.getName();
    private static final String LOCATION_UPDATES_COUNT_PREF = "location_updates_count";
    private static final long FIRST_LOCATION_MIN_TIME_MS = 2000;
    private static final long NO_DATA = -1;

    private final Context mContext;

    private boolean mScreenIsOn;
    private long mLocationUpdatesCount = NO_DATA;
    private long mFirstChangeTimestamp;

    private CellLocation mCellLocation;

    public ScreenMonitor(Context context) {
        mContext = context;
    }

    public void start() {
        load();
        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mScreenIsOn = pm.isScreenOn();
        Log.i(LOGTAG, "Total cell location updates when the screen is off: " +
                (mLocationUpdatesCount == NO_DATA ? " no data" : mLocationUpdatesCount));
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        mContext.registerReceiver(mScreenReceiver, filter);
    }

    public void putLocation(CellLocation location) {
        if (mScreenIsOn) return;

        if (mFirstChangeTimestamp != 0) {
            if (Math.abs(System.nanoTime() - mFirstChangeTimestamp) > FIRST_LOCATION_MIN_TIME_MS * 1e6) {
                Log.i(LOGTAG, "Received the first cell location update when the screen is off");
                mLocationUpdatesCount = 1;
                mFirstChangeTimestamp = 0;
            }
        }

        if (mCellLocation == null) {
            mCellLocation = location;
            if (mLocationUpdatesCount < 0) mLocationUpdatesCount = 0;
        } else if (!mCellLocation.equals(location)) {
            mCellLocation = location;
            if (mFirstChangeTimestamp == 0) {
                if (mLocationUpdatesCount <= 0) {
                    mFirstChangeTimestamp = System.nanoTime();
                    mLocationUpdatesCount = 0;
                } else {
                    mLocationUpdatesCount += 1;
                }
            }
        }
    }

    public boolean isLocationValid() {
        return mScreenIsOn || (mLocationUpdatesCount > 0);
    }

    public void stop() {
        mContext.unregisterReceiver(mScreenReceiver);
        persist();
    }

    private void persist() {
        mContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
                .edit()
                .putLong(LOCATION_UPDATES_COUNT_PREF, mLocationUpdatesCount)
                .commit();
    }

    private void load() {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
        mLocationUpdatesCount = prefs.getLong(LOCATION_UPDATES_COUNT_PREF, NO_DATA);
    }

    final BroadcastReceiver mScreenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                mScreenIsOn = false;
                mCellLocation = null;
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                mScreenIsOn = true;
                mFirstChangeTimestamp = 0;
                mCellLocation = null;
            }
        }
    };
}
