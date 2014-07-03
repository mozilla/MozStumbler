/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import org.mozilla.mozstumbler.service.datahandling.ServerContentResolver;
import org.mozilla.mozstumbler.service.datahandling.StumblerBundleReceiver;
import org.mozilla.mozstumbler.service.scanners.cellscanner.CellScanner;
import org.mozilla.mozstumbler.service.scanners.cellscanner.CellScannerNoWCDMA;
import org.mozilla.mozstumbler.service.sync.AsyncUploader;
import org.mozilla.mozstumbler.service.utils.NetworkUtils;
import java.util.Timer;
import java.util.TimerTask;

public final class StumblerService extends Service {
    public  static final String ACTION_BASE = SharedConstants.ACTION_NAMESPACE;
    public  static final String ACTION_START_PASSIVE = ACTION_BASE + ".START_PASSIVE";
    private static final String LOGTAG          = StumblerService.class.getName();

    private Scanner                mScanner;
    private Reporter               mReporter;

    // our default receiver for StumblerBundles. we may want to
    // let the application disable this in the future.
    private StumblerBundleReceiver mStumblerBundleReceiver = new StumblerBundleReceiver();
    private boolean                mIsBound;
    private final IBinder          mBinder         = new StumblerBinder();

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
        if (SharedConstants.isDebug) Log.d(LOGTAG, "onCreate");

        if (SharedConstants.appVersionCode < 1) {
            //TODO look at how to set these
            //SharedConstants.appVersionName =;
            //SharedConstants.appVersionCode =;
            SharedConstants.isDebug = true;
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
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getBooleanExtra(ACTION_START_PASSIVE, false)) {
            if (SharedConstants.stumblerContentResolver == null) {
                SharedConstants.stumblerContentResolver = new ServerContentResolver(this);
            }

            mScanner.setPassiveMode(true);
            startScanning();

            // TODO way more logic here. Decide on the timing. Perhaps trigger based on wake from sleep, or when network status changed.
            long delay_ms = 10 * 60 * 1000; // ten mins
            long period_ms = 60 * 60 * 1000; // one hour
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    if (!mScanner.isBatteryLow()) {
                        AsyncUploader uploader = new AsyncUploader(null /* don't need to listen for completion */);
                        uploader.execute();
                    }
                }
            }, delay_ms, period_ms);
        }

        // keep running!
        return Service.START_STICKY;
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
}
