/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.location.Location;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.BaseColumns;
import android.util.Log;
import org.mozilla.mozstumbler.service.datahandling.DatabaseContract;
import org.mozilla.mozstumbler.service.datahandling.ServerContentResolver;
import org.mozilla.mozstumbler.service.datahandling.StumblerBundleReceiver;
import org.mozilla.mozstumbler.service.scanners.cellscanner.CellScanner;
import org.mozilla.mozstumbler.service.scanners.cellscanner.CellScannerNoWCDMA;
import org.mozilla.mozstumbler.service.sync.AsyncUploader;
import org.mozilla.mozstumbler.service.utils.NetworkUtils;
import java.util.Timer;
import java.util.TimerTask;

public final class StumblerService extends PersistentIntentService
        implements ServerContentResolver.DatabaseIsEmptyTracker {
    private static final String LOGTAG          = StumblerService.class.getName();
    public static final String ACTION_BASE = SharedConstants.ACTION_NAMESPACE;
    public static final String ACTION_START_PASSIVE = ACTION_BASE + ".START_PASSIVE";
    public static final String ACTION_EXTRA_MOZ_API_KEY = ACTION_BASE + ".MOZKEY";

    public enum FirefoxStumbleState {
        UNKNOWN, ON, OFF
    }
    public static FirefoxStumbleState sFirefoxStumblingEnabled = FirefoxStumbleState.UNKNOWN;

    private Scanner                mScanner;
    private Reporter               mReporter;
    private StumblerBundleReceiver mStumblerBundleReceiver = new StumblerBundleReceiver();
    private boolean                mIsBound;
    private final IBinder          mBinder         = new StumblerBinder();
    private Timer                  mPassiveUploadTimer;
    private static boolean sDidCheckForStaleDatabase;

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
        return mScanner.isScanning();
    }

    public void startScanning() {
        if (mScanner.isScanning()) {
            return;
        }

        mScanner.startScanning();
    }

    public void stopScanning() {
        if (mScanner.isScanning()) {
            mScanner.stopScanning();
            mReporter.flush();
            if (!mIsBound) {
                stopSelf();
            }
        }
    }

    public Prefs getPrefs() { return Prefs.getInstance(); }

    public void checkPrefs() {
        mScanner.checkPrefs();
    }

    public int getLocationCount() {
        return mScanner.getLocationCount();
    }

    public double getLatitude() {
        return mScanner.getLatitude();
    }

    public double getLongitude() {
        return mScanner.getLongitude();
    }

    public Location getLocation() {
        return mScanner.getLocation();
    }

    public int getWifiStatus() {
        return mScanner.getWifiStatus();
    }

    public int getAPCount() {
        return mScanner.getAPCount();
    }

    public int getVisibleAPCount() {
        return mScanner.getVisibleAPCount();
    }

    public int getCellInfoCount() {
        return mScanner.getCellInfoCount();
    }

    public int getCurrentCellInfoCount() {
        return mScanner.getCurrentCellInfoCount();
    }

    public boolean isGeofenced () {
        return mScanner.isGeofenced();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        setIntentRedelivery(true);

        if (SharedConstants.appVersionCode < 1) {
            //TODO look at how to set these
            //SharedConstants.appVersionName =;
            //SharedConstants.appVersionCode =;
        }

        Prefs.createGlobalInstance(this);
        NetworkUtils.createGlobalInstance(this);

        if (!CellScanner.isCellScannerImplSet()) {
            CellScanner.setCellScannerImpl(new CellScannerNoWCDMA(this));
        }
        mScanner = new Scanner(this);
        mReporter = new Reporter(this, mStumblerBundleReceiver);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (sFirefoxStumblingEnabled == FirefoxStumbleState.OFF) {
            Prefs.getInstance().setFirefoxScanEnabled(false);
        }

        if (SharedConstants.isDebug) Log.d(LOGTAG, "onDestroy");

        if (SharedConstants.stumblerContentResolver != null &&
            SharedConstants.stumblerContentResolver instanceof ServerContentResolver) {
            SharedConstants.stumblerContentResolver.shutdown();
        }
        mReporter.shutdown();
        mReporter = null;
        mScanner = null;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null && intent.getBooleanExtra(ACTION_START_PASSIVE, false)) {
            if (SharedConstants.stumblerContentResolver == null) {
                SharedConstants.stumblerContentResolver = new ServerContentResolver(this, this);
                if (!SharedConstants.stumblerContentResolver.isDbEmpty()) {
                    startPassiveModeUploadTimer();
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

            mScanner.setPassiveMode(true);
            startScanning();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        mIsBound = true;
        if (SharedConstants.isDebug)Log.d(LOGTAG, "onBind");
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (SharedConstants.isDebug) Log.d(LOGTAG, "onUnbind");
        if (!mScanner.isScanning()) {
            stopSelf();
        }
        mIsBound = false;
        return true;
    }

    @Override
    public void onRebind(Intent intent) {
        mIsBound = true;
        if (SharedConstants.isDebug)Log.d(LOGTAG,"onRebind");
    }

    void startPassiveModeUploadTimer() {
        if (mPassiveUploadTimer != null)
            return;

        mPassiveUploadTimer = new Timer();

        // Periodically check if wifi is available, screen is on, upload any bundles
        long delay_ms = 3 * 60 * 1000;
        long period_ms = 3 * 60 * 1000;

        mPassiveUploadTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                String[] projection = { DatabaseContract.Reports.TIME };
                Cursor cursor = SharedConstants.stumblerContentResolver.query(DatabaseContract.Reports.CONTENT_URI,
                        projection, null, null, BaseColumns._ID + " limit 1");
                if (cursor.getCount() < 1) {
                    cursor.close();
                    databaseIsEmpty(true);
                    return;
                }

                cursor.moveToLast();
                long time = cursor.getLong(cursor.getColumnIndex(DatabaseContract.Reports.TIME));
                cursor.close();
                if (time > 0 && !sDidCheckForStaleDatabase) {
                    sDidCheckForStaleDatabase = true;
                    long currentTime = System.currentTimeMillis();
                    long msPerWeek = 604800 * 1000;
                    if (currentTime - time > 2 * msPerWeek) {
                        SharedConstants.stumblerContentResolver.delete(DatabaseContract.Reports.CONTENT_URI, null, null);
                        databaseIsEmpty(true);
                        return;
                    }
                }

                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (!mScanner.isBatteryLow() && NetworkUtils.getInstance().isWifiAvailable() &&
                    pm.isScreenOn())
                {
                    AsyncUploader uploader = new AsyncUploader(null /* don't need to listen for completion */);
                    uploader.execute();
                }
            }
        }, delay_ms, period_ms);
    }


    public void databaseIsEmpty(boolean isEmpty) {
        if (isEmpty && mPassiveUploadTimer != null) {
            mPassiveUploadTimer.cancel();
            mPassiveUploadTimer = null;
        } else {
            startPassiveModeUploadTimer();
        }
    }

}
