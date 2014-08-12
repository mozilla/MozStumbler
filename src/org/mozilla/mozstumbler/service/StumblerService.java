/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service;

import android.content.Intent;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.mozilla.mozstumbler.service.blocklist.WifiBlockListInterface;
import org.mozilla.mozstumbler.service.datahandling.DataStorageManager;
import org.mozilla.mozstumbler.service.datahandling.StumblerBundleReceiver;
import org.mozilla.mozstumbler.service.scanners.ScanManager;
import org.mozilla.mozstumbler.service.scanners.cellscanner.CellScanner;
import org.mozilla.mozstumbler.service.scanners.cellscanner.CellScannerNoWCDMA;
import org.mozilla.mozstumbler.service.sync.UploadAlarmReceiver;
import org.mozilla.mozstumbler.service.utils.NetworkUtils;
import org.mozilla.mozstumbler.service.utils.PersistentIntentService;

public final class StumblerService extends PersistentIntentService
        implements DataStorageManager.StorageIsEmptyTracker {
    private static final String LOG_TAG = "Stumbler" + StumblerService.class.getSimpleName();
    public static final String ACTION_BASE = AppGlobals.ACTION_NAMESPACE;
    public static final String ACTION_START_PASSIVE = ACTION_BASE + ".START_PASSIVE";
    public static final String ACTION_EXTRA_MOZ_API_KEY = ACTION_BASE + ".MOZKEY";
    public static final AtomicBoolean sFirefoxStumblingEnabled = new AtomicBoolean();
    private final ScanManager mScanManager = new ScanManager();
    private final StumblerBundleReceiver mStumblerBundleReceiver = new StumblerBundleReceiver();
    private final Reporter mReporter = new Reporter(mStumblerBundleReceiver);
    private boolean mIsBound;
    private final IBinder mBinder = new StumblerBinder();

    public StumblerService() {
        super("StumblerService");
    }

    public StumblerService(String name) {
        super(name);
    }

    public final class StumblerBinder extends Binder {
        // Only to be used in the non-standalone, non-passive case (MozStumbler). In the passive standalone usage
        // of this class, everything, including initialization, is done on its dedicated thread
        public StumblerService getServiceAndInitialize(Thread callingThread) {
            if (Looper.getMainLooper().getThread() != callingThread) {
                throw new RuntimeException("Only call from main thread");
            };
            init();
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

        mScanManager.startScanning(this);
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

    public void setWifiBlockList(WifiBlockListInterface list) {
        mScanManager.setWifiBlockList(list);
    }

    public Prefs getPrefs() {
        return Prefs.getInstance();
    }

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

    // Previously this was done in onCreate(). Moved out of that so that in the passive standalone service
    // use (i.e. Fennec), init() can be called from this class's dedicated thread.
    private void init() {
        Prefs.createGlobalInstance(this);
        NetworkUtils.createGlobalInstance(this);
        DataStorageManager.createGlobalInstance(this, this);

        if (!CellScanner.isCellScannerImplSet()) {
            CellScanner.setCellScannerImpl(new CellScannerNoWCDMA(this));
        }

        mReporter.startup(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        setIntentRedelivery(true);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Used to move these disk I/O ops off the calling thread
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                if (AppGlobals.isDebug) Log.d(LOG_TAG, "onDestroy");

                if (sFirefoxStumblingEnabled.get() == false) {
                    Prefs.getInstance().setFirefoxScanEnabled(false);
                }

                if (DataStorageManager.getInstance() != null) {
                    try {
                        DataStorageManager.getInstance().saveCurrentReportsToDisk();
                    } catch (IOException ex) {
                        AppGlobals.guiLogInfo(ex.toString());
                        Log.e(LOG_TAG, "Exception in onDestroy saving reports", ex);
                    }
                }
                return null;
            }
        }.execute();

        mReporter.shutdown();
        mScanManager.stopScanning();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent == null) {
            return;
        }

        init();

        if (intent.getBooleanExtra(ACTION_START_PASSIVE, false) == false) {
            stopSelf();
        } else {
            if (!DataStorageManager.getInstance().isDirEmpty()) {
                // non-empty on startup, schedule an upload
                // This is the only upload trigger in Firefox mode
                final int secondsToWait = 10;
                UploadAlarmReceiver.scheduleAlarm(this, secondsToWait, false /* no repeat*/);
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
        if (AppGlobals.isDebug)Log.d(LOG_TAG, "onBind");
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (AppGlobals.isDebug) Log.d(LOG_TAG, "onUnbind");
        if (!mScanManager.isScanning()) {
            stopSelf();
        }
        mIsBound = false;
        return true;
    }

    @Override
    public void onRebind(Intent intent) {
        mIsBound = true;
        if (AppGlobals.isDebug)Log.d(LOG_TAG,"onRebind");
    }

    // Note that in passive mode, having data isn't an upload trigger, it is triggered by the start intent
    public void storageIsEmpty(boolean isEmpty) {
        if (isEmpty) {
            UploadAlarmReceiver.cancelAlarm(this, !mScanManager.isPassiveMode());
        } else if (!mScanManager.isPassiveMode()) {
            int secondsToWait = 5 * 60;
            UploadAlarmReceiver.scheduleAlarm(this, secondsToWait, true /* repeating */);
        }
    }
}
