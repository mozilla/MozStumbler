/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import org.mozilla.mozstumbler.service.scanners.cellscanner.CellScanner;
import org.mozilla.mozstumbler.service.scanners.GPSScanner;
import org.mozilla.mozstumbler.service.scanners.WifiScanner;
import org.mozilla.mozstumbler.service.SharedConstants.ActiveOrPassiveStumbling;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class Scanner {
    private static final String LOGTAG = Scanner.class.getName();
    private Timer mPassiveModeFlushTimer;
    private final Context mContext;
    private boolean       mIsScanning;
    private GPSScanner    mGPSScanner;
    private WifiScanner   mWifiScanner;
    private CellScanner   mCellScanner;
    private ActiveOrPassiveStumbling mStumblingMode = ActiveOrPassiveStumbling.ACTIVE_STUMBLING;

    public Scanner(Context context) {
        mContext = context;
        mGPSScanner  = new GPSScanner(context, this);
        mWifiScanner = new WifiScanner(context);
        mCellScanner = new CellScanner(context);
    }


    public boolean isBatteryLow() {
        Intent intent = mContext.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent == null)
            return false;

        int rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING);
        int level = Math.round(rawLevel * scale/100.0f);

        final int kMinBatteryPct = 15;
        return !isCharging && level < kMinBatteryPct;
    }

    public void newPassiveGpsLocation() {
        if (isBatteryLow()) {
            return;
        }

        if (SharedConstants.isDebug) Log.d(LOGTAG, "New passive location");

        mWifiScanner.start(ActiveOrPassiveStumbling.PASSIVE_STUMBLING);
        mCellScanner.start(ActiveOrPassiveStumbling.PASSIVE_STUMBLING);

        // how often to flush a leftover bundle to the reports table
        // If there is a bundle, and nothing happens for 10sec, then flush it
        final int flushRate_ms = 10000;

        if (mPassiveModeFlushTimer != null) {
            mPassiveModeFlushTimer.cancel();
        }

        Date when = new Date();
        when.setTime(when.getTime() + flushRate_ms);
        mPassiveModeFlushTimer = new Timer();
        mPassiveModeFlushTimer.schedule(new TimerTask() {
          @Override
          public void run() {
              Intent flush = new Intent(Reporter.ACTION_FLUSH_TO_DB);
              LocalBroadcastManager.getInstance(mContext).sendBroadcast(flush);
          }
        }, when);
    }

    void setPassiveMode(boolean on) {
        mStumblingMode = (on)? ActiveOrPassiveStumbling.PASSIVE_STUMBLING :
               ActiveOrPassiveStumbling.ACTIVE_STUMBLING;
    }

    void startScanning() {
        if (mIsScanning) {
            return;
        }
        if (SharedConstants.isDebug) Log.d(LOGTAG, "Scanning started...");

        mGPSScanner.start(mStumblingMode);
        if (mStumblingMode == ActiveOrPassiveStumbling.ACTIVE_STUMBLING) {
            mWifiScanner.start(mStumblingMode);
            mCellScanner.start(mStumblingMode);
            // in passive mode, these scans are started by passive gps notifications
        }
        mIsScanning = true;
    }

    void stopScanning() {
        if (!mIsScanning) {
            return;
        }

        if (SharedConstants.isDebug) Log.d(LOGTAG, "Scanning stopped");

        mGPSScanner.stop();
        mWifiScanner.stop();
        mCellScanner.stop();

        mIsScanning = false;
    }

    boolean isScanning() {
        return mIsScanning;
    }

    int getAPCount() {
        return mWifiScanner.getAPCount();
    }

    int getVisibleAPCount() {
        return mWifiScanner.getVisibleAPCount();
    }

    int getWifiStatus() {
        return mWifiScanner.getStatus();
    }

    int getCellInfoCount() {
        return mCellScanner.getCellInfoCount();
    }

    int getCurrentCellInfoCount() {
        return mCellScanner.getCurrentCellInfoCount();
    }

    int getLocationCount() {
        return mGPSScanner.getLocationCount();
    }

    double getLatitude() {
        return mGPSScanner.getLatitude();
    }

    double getLongitude() {
        return mGPSScanner.getLongitude();
    }

    void checkPrefs() {
        mGPSScanner.checkPrefs();
    }

    boolean isGeofenced() {
        return mGPSScanner.isGeofenced();
    }
}
