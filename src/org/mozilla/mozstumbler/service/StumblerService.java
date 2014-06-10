package org.mozilla.mozstumbler.service;

import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import org.mozilla.mozstumbler.service.datahandling.StumblerBundleReceiver;
import org.mozilla.mozstumbler.service.sync.SyncUtils;

public final class StumblerService extends Service {
    private static final String LOGTAG          = StumblerService.class.getName();
    private Scanner                mScanner;
    private Reporter               mReporter;

    // our default receiver for StumblerBundles. we may want to
    // let the application disable this in the future.
    private StumblerBundleReceiver mStumblerBundleReceiver = new StumblerBundleReceiver();
    private boolean                mIsBound;
    private final IBinder          mBinder         = new StumblerBinder();
    private Prefs mPrefs;

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

        mReporter.registerBundleReceiver(mStumblerBundleReceiver);
    }

    public void stopScanning() {
        if (mScanner.isScanning()) {
            mScanner.stopScanning();
            mReporter.flush();
            if (!mIsBound) {
                stopSelf();
                mReporter.unregisterBundleReceiver();
            }
            SyncUtils.TriggerRefresh(false);
        }
    }

    public Prefs getPrefs() { return mPrefs; }

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
        Log.d(LOGTAG, "onCreate");

        mPrefs = new Prefs(this);
        mScanner = new Scanner(this);
        mReporter = new Reporter(this);

        SyncUtils.CreateSyncAccount(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(LOGTAG, "onDestroy");

        mReporter.shutdown();
        mReporter = null;
        mScanner = null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // keep running!
        return Service.START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        mIsBound = true;
        Log.d(LOGTAG, "onBind");
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(LOGTAG, "onUnbind");
        if (!mScanner.isScanning()) {
            stopSelf();
        }
        mIsBound = false;
        return true;
    }

    @Override
    public void onRebind(Intent intent) {
        mIsBound = true;
        Log.d(LOGTAG,"onRebind");
    }
}
