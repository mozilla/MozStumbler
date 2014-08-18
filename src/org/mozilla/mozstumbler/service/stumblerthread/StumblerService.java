/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread;

import android.content.Intent;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.stumblerthread.blocklist.WifiBlockListInterface;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.DataStorageManager;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.StumblerBundleReceiver;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.ScanManager;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.cellscanner.CellScanner;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.cellscanner.CellScannerNoWCDMA;
import org.mozilla.mozstumbler.service.uploadthread.UploadAlarmReceiver;
import org.mozilla.mozstumbler.service.utils.NetworkUtils;
import org.mozilla.mozstumbler.service.utils.PersistentIntentService;

// Used as a bound service (with foreground priority) in MozStumbler, a.k.a. active scanning mode.
// -- In accordance with Android service docs -and experimental findings- this puts the service as low
//    as possible on the Android process kill list.
// -- Binding functions are commented in this class as being unused in the stand-alone service mode.
//
// In stand-alone service mode (a.k.a passive scanning mode), this is created from PassiveServiceReceiver (by calling startService).
// The StumblerService is a sticky unbound service in this usage.
//
public final class StumblerService extends PersistentIntentService
        implements DataStorageManager.StorageIsEmptyTracker {
    private static final String LOG_TAG = AppGlobals.LOG_PREFIX + StumblerService.class.getSimpleName();
    public static final String ACTION_BASE = AppGlobals.ACTION_NAMESPACE;
    public static final String ACTION_START_PASSIVE = ACTION_BASE + ".START_PASSIVE";
    public static final String ACTION_EXTRA_MOZ_API_KEY = ACTION_BASE + ".MOZKEY";
    public static final String ACTION_NOT_FROM_HOST_APP = ACTION_BASE + ".NOT_FROM_HOST";
    public static final AtomicBoolean sFirefoxStumblingEnabled = new AtomicBoolean();
    private final ScanManager mScanManager = new ScanManager();
    private final StumblerBundleReceiver mStumblerBundleReceiver = new StumblerBundleReceiver();
    private final Reporter mReporter = new Reporter(mStumblerBundleReceiver);
    private boolean mIsBound;
    private final IBinder mBinder = new StumblerBinder();

    public StumblerService() {
        this("StumblerService");
    }

    public StumblerService(String name) {
        super(name);
    }

    // Service binding is not used in stand-alone passive mode.
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

    // Service binding is not used in stand-alone passive mode.
    @Override
    public IBinder onBind(Intent intent) {
        mIsBound = true;
        if (AppGlobals.isDebug) {
            Log.d(LOG_TAG, "onBind");
        }
        return mBinder;
    }

    // Service binding is not used in stand-alone passive mode.
    @Override
    public boolean onUnbind(Intent intent) {
        if (AppGlobals.isDebug) {
            Log.d(LOG_TAG, "onUnbind");
        }
        mIsBound = false;
        return true;
    }

    // Service binding is not used in stand-alone passive mode.
    @Override
    public void onRebind(Intent intent) {
        mIsBound = true;
        if (AppGlobals.isDebug) {
            Log.d(LOG_TAG,"onRebind");
        }
    }

    public boolean isScanning() {
        return mScanManager.isScanning();
    }

    public void startScanning() {
        mScanManager.startScanning(this);
    }

    public void stopScanning() {
        if (mScanManager.stopScanning()) {
            mReporter.flush();
            if (!mIsBound) {
                stopSelf();
            }
        }
    }

    // This is optional, not used in Fennec, and is for clients to specify a (potentially long) list
    // of blocklisted SSIDs/BSSIDs
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
    // Safe to call more than once, ensure added code complies with that intent.
    private void init() {
        Prefs.createGlobalInstance(this);
        NetworkUtils.createGlobalInstance(this);
        DataStorageManager.createGlobalInstance(this, this);

        if (!CellScanner.isCellScannerImplSet()) {
            CellScanner.setCellScannerImpl(new CellScannerNoWCDMA(this));
        }

        mReporter.startup(this);
    }

    // Called from the main thread.
    @Override
    public void onCreate() {
        super.onCreate();
        setIntentRedelivery(true);
    }

    // Called from the main thread
    @Override
    public void onDestroy() {
        super.onDestroy();

        if (!mScanManager.isScanning()) {
            return;
        }

        // Used to move these disk I/O ops off the calling thread. The current operations here are synchronized,
        // however instead of creating another thread (if onDestroy grew to have concurrency complications)
        // we could be messaging the stumbler thread to perform a shutdown function.
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                if (AppGlobals.isDebug) {
                    Log.d(LOG_TAG, "onDestroy");
                }

                if (!sFirefoxStumblingEnabled.get()) {
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

    // This is the entry point for the stumbler thread.
    @Override
    protected void onHandleIntent(Intent intent) {
        // Do init() in all cases, there is no cost, whereas it is easy to add code that depends on this.
        init();

        if (intent == null) {
            return;
        }

        final boolean isScanEnabledInPrefs = Prefs.getInstance().getFirefoxScanEnabled();

        if (!isScanEnabledInPrefs && intent.getBooleanExtra(ACTION_NOT_FROM_HOST_APP, false)) {
            stopSelf();
            return;
        }

        if (!DataStorageManager.getInstance().isDirEmpty()) {
            // non-empty on startup, schedule an upload
            // This is the only upload trigger in Firefox mode
            // Firefox triggers this ~4 seconds after startup (after Gecko is loaded), add a small delay to avoid
            // clustering with other operations that are triggered at this time.
            final int secondsToWait = 2;
            UploadAlarmReceiver.scheduleAlarm(this, secondsToWait, false /* no repeat*/);
        }

        if (!isScanEnabledInPrefs) {
            Prefs.getInstance().setFirefoxScanEnabled(true);
        }

        String apiKey = intent.getStringExtra(ACTION_EXTRA_MOZ_API_KEY);
        if (apiKey != null) {
            Prefs.getInstance().setMozApiKey(apiKey);
        }

        if (!mScanManager.isScanning()) {
            mScanManager.setPassiveMode(true);
            startScanning();
        }
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
