/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service;

import android.app.PendingIntent;
import android.content.Intent;
import android.location.Location;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import org.mozilla.mozstumbler.service.datahandling.DataStorageManager;
import org.mozilla.mozstumbler.service.datahandling.StumblerBundleReceiver;
import org.mozilla.mozstumbler.service.scanners.ScanManager;
import org.mozilla.mozstumbler.service.scanners.cellscanner.CellScanner;
import org.mozilla.mozstumbler.service.scanners.cellscanner.CellScannerNoWCDMA;
import org.mozilla.mozstumbler.service.sync.UploadAlarmReceiver;
import org.mozilla.mozstumbler.service.utils.NetworkUtils;
import org.mozilla.mozstumbler.service.utils.PersistentIntentService;

import java.io.IOException;

public final class StumblerService extends PersistentIntentService
        implements DataStorageManager.StorageIsEmptyTracker {
    private static final String LOGTAG          = StumblerService.class.getName();
    public static final String ACTION_BASE = AppGlobals.ACTION_NAMESPACE;
    public static final String ACTION_START_PASSIVE = ACTION_BASE + ".START_PASSIVE";
    public static final String ACTION_EXTRA_MOZ_API_KEY = ACTION_BASE + ".MOZKEY";

    public enum FirefoxStumbleState {
        UNKNOWN, ON, OFF
    }
    public static FirefoxStumbleState sFirefoxStumblingEnabled = FirefoxStumbleState.UNKNOWN;

    private ScanManager mScanManager;
    private Reporter               mReporter;
    private StumblerBundleReceiver mStumblerBundleReceiver = new StumblerBundleReceiver();
    private boolean                mIsBound;
    private final IBinder          mBinder = new StumblerBinder();
    private PendingIntent          mAlarmIntent;

    public StumblerService() {
        super("StumblerService");
    }

    public StumblerService(String name) {
        super(name);
    }

    public final class StumblerBinder extends Binder {
        public StumblerService getService() {
            return StumblerService.this;
        }
    }

    public boolean isScanning() {
        return mScanManager.isScanning();
    }

    public void startScanning() {
        if (mScanManager.isScanning()) {
            return;
        }

        mScanManager.startScanning();
    }

    public void stopScanning() {
        if (mScanManager.isScanning()) {
            mScanManager.stopScanning();
            mReporter.flush();
            if (!mIsBound) {
                stopSelf();
            }
        }
    }

    public Prefs getPrefs() { return Prefs.getInstance(); }

    public void checkPrefs() {
        mScanManager.checkPrefs();
    }

    public int getLocationCount() {
        return mScanManager.getLocationCount();
    }

    public double getLatitude() {
        return mScanManager.getLatitude();
    }

    public double getLongitude() {
        return mScanManager.getLongitude();
    }

    public Location getLocation() {
        return mScanManager.getLocation();
    }

    public int getWifiStatus() {
        return mScanManager.getWifiStatus();
    }

    public int getAPCount() {
        return mScanManager.getAPCount();
    }

    public int getVisibleAPCount() {
        return mScanManager.getVisibleAPCount();
    }

    public int getCellInfoCount() {
        return mScanManager.getCellInfoCount();
    }

    public int getCurrentCellInfoCount() {
        return mScanManager.getCurrentCellInfoCount();
    }

    public boolean isGeofenced () {
        return mScanManager.isGeofenced();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        setIntentRedelivery(true);

        if (AppGlobals.appVersionCode < 1) {
            //TODO look at how to set these
            //SharedConstants.appVersionName =;
            //SharedConstants.appVersionCode =;
        }

        Prefs.createGlobalInstance(this);
        NetworkUtils.createGlobalInstance(this);

        AppGlobals.dataStorageManager = new DataStorageManager(this, this);

        if (!CellScanner.isCellScannerImplSet()) {
            CellScanner.setCellScannerImpl(new CellScannerNoWCDMA(this));
        }
        mScanManager = new ScanManager(this);
        mReporter = new Reporter(this, mStumblerBundleReceiver);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (sFirefoxStumblingEnabled == FirefoxStumbleState.OFF) {
            Prefs.getInstance().setFirefoxScanEnabled(false);
        }

        if (AppGlobals.isDebug) Log.d(LOGTAG, "onDestroy");

        if (AppGlobals.dataStorageManager != null) {
            try {
                AppGlobals.dataStorageManager.saveCurrentReportsToDisk();
            } catch (IOException ex) {
                AppGlobals.guiLogInfo(ex.toString());
                Log.e(LOGTAG, "Exception in onDestroy saving reports", ex);
            }
        }
        mReporter.shutdown();
        mReporter = null;
        mScanManager = null;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null && intent.getBooleanExtra(ACTION_START_PASSIVE, false)) {
            if (AppGlobals.dataStorageManager == null) {
                AppGlobals.dataStorageManager = new DataStorageManager(this, this);
                if (!AppGlobals.dataStorageManager.isDirEmpty()) {
                    // non-empty on startup, schedule an upload
                    UploadAlarmReceiver.scheduleAlarm(this);
                }
            }

            if (sFirefoxStumblingEnabled == FirefoxStumbleState.UNKNOWN) {
                if (!Prefs.getInstance().getFirefoxScanEnabled()) {
                    stopSelf();
                }
            }

            Prefs.getInstance().setFirefoxScanEnabled(true);

            String apiKey = intent.getStringExtra(ACTION_EXTRA_MOZ_API_KEY);
            if (apiKey != null) {
                Prefs.getInstance().setMozApiKey(apiKey);
            }

            mScanManager.setPassiveMode(true);
            startScanning();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        mIsBound = true;
        if (AppGlobals.isDebug)Log.d(LOGTAG, "onBind");
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (AppGlobals.isDebug) Log.d(LOGTAG, "onUnbind");
        if (!mScanManager.isScanning()) {
            stopSelf();
        }
        mIsBound = false;
        return true;
    }

    @Override
    public void onRebind(Intent intent) {
        mIsBound = true;
        if (AppGlobals.isDebug)Log.d(LOGTAG,"onRebind");
    }

    public void storageIsEmpty(boolean isEmpty) {
        if (isEmpty) {
            UploadAlarmReceiver.cancelAlarm(this);
        } else {
            UploadAlarmReceiver.scheduleAlarm(this);
        }
    }
}
