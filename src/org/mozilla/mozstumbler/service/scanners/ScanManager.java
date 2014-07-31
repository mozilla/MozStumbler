/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.scanners;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.BatteryManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.Reporter;
import org.mozilla.mozstumbler.service.scanners.cellscanner.CellScanner;
import org.mozilla.mozstumbler.service.AppGlobals.ActiveOrPassiveStumbling;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class ScanManager {
    private static final String LOGTAG = ScanManager.class.getName();
    private Timer mPassiveModeFlushTimer;
    private final Context mContext;
    private boolean       mIsScanning;
    private GPSScanner    mGPSScanner;
    private WifiScanner   mWifiScanner;
    private CellScanner   mCellScanner;
    private ActiveOrPassiveStumbling mStumblingMode = ActiveOrPassiveStumbling.ACTIVE_STUMBLING;

    public ScanManager(Context context) {
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

        if (AppGlobals.isDebug) Log.d(LOGTAG, "New passive location");

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
              LocalBroadcastManager.getInstance(mContext).sendBroadcastSync(flush);
          }
        }, when);
    }

    public void setPassiveMode(boolean on) {
        mStumblingMode = (on)? ActiveOrPassiveStumbling.PASSIVE_STUMBLING :
               ActiveOrPassiveStumbling.ACTIVE_STUMBLING;
    }

    public void startScanning() {
        if (mIsScanning) {
            return;
        }
        if (AppGlobals.isDebug) Log.d(LOGTAG, "Scanning started...");

        mGPSScanner.start(mStumblingMode);
        if (mStumblingMode == ActiveOrPassiveStumbling.ACTIVE_STUMBLING) {
            mWifiScanner.start(mStumblingMode);
            mCellScanner.start(mStumblingMode);
            // in passive mode, these scans are started by passive gps notifications
        }
        mIsScanning = true;
    }

    public void stopScanning() {
        if (!mIsScanning) {
            return;
        }

        if (AppGlobals.isDebug) Log.d(LOGTAG, "Scanning stopped");

        mGPSScanner.stop();
        mWifiScanner.stop();
        mCellScanner.stop();

        mIsScanning = false;
    }

    public boolean isScanning() {
        return mIsScanning;
    }

    public int getAPCount() {
        return mWifiScanner.getAPCount();
    }

    public int getVisibleAPCount() {
        return mWifiScanner.getVisibleAPCount();
    }

    public int getWifiStatus() {
        return mWifiScanner.getStatus();
    }

    public int getCellInfoCount() {
        return mCellScanner.getCellInfoCount();
    }

    public int getCurrentCellInfoCount() {
        return mCellScanner.getCurrentCellInfoCount();
    }

    public int getLocationCount() {
        return mGPSScanner.getLocationCount();
    }

    public double getLatitude() {
        return mGPSScanner.getLatitude();
    }

    public double getLongitude() {
        return mGPSScanner.getLongitude();
    }

    public Location getLocation() {
        return mGPSScanner.getLocation();
    }

    public void checkPrefs() {
        mGPSScanner.checkPrefs();
    }

    public boolean isGeofenced() {
        return mGPSScanner.isGeofenced();
    }
}
