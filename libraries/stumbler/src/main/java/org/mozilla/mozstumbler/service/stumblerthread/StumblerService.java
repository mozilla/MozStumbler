/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.AsyncTask;
import android.support.v4.content.LocalBroadcastManager;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.DataStorageManager;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.IDataStorageManager;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.StorageIsEmptyTracker;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.ScanManager;
import org.mozilla.mozstumbler.service.uploadthread.UploadAlarmReceiver;
import org.mozilla.mozstumbler.service.utils.NetworkInfo;
import org.mozilla.mozstumbler.service.utils.PersistentIntentService;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.log.ILogger;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

import java.util.concurrent.atomic.AtomicBoolean;

// In stand-alone service mode (a.k.a passive scanning mode), this is created from PassiveServiceReceiver (by calling startService).
// The StumblerService is a sticky unbound service in this usage.
//
public class StumblerService extends PersistentIntentService
        implements StorageIsEmptyTracker {

    private static final String LOG_TAG = LoggerUtil.makeLogTag(StumblerService.class);
    private static ILogger Log = (ILogger) ServiceLocator.getInstance().getService(ILogger.class);

    public static final String ACTION_BASE = AppGlobals.ACTION_NAMESPACE;
    public static final String ACTION_START_PASSIVE = ACTION_BASE + ".START_PASSIVE";
    public static final String ACTION_EXTRA_MOZ_API_KEY = ACTION_BASE + ".MOZKEY";
    public static final String ACTION_EXTRA_USER_AGENT = ACTION_BASE + ".USER_AGENT";
    public static final String ACTION_NOT_FROM_HOST_APP = ACTION_BASE + ".NOT_FROM_HOST";
    public static final AtomicBoolean sFirefoxStumblingEnabled = new AtomicBoolean();
    // This is a delay before the single-shot upload is attempted. The number is arbitrary
    // and used to avoid startup tasks bunching up.
    private static final int DELAY_IN_SEC_BEFORE_STARTING_UPLOAD_IN_PASSIVE_MODE = 2;
    // This is the frequency of the repeating upload alarm in active scanning mode.
    private static final int FREQUENCY_IN_SEC_OF_UPLOAD_IN_ACTIVE_MODE = 5 * 60;
    // Used to guard against attempting to upload too frequently in passive mode.
    private static final long PASSIVE_UPLOAD_FREQ_GUARD_MSEC = 5 * 60 * 1000;
    protected final ScanManager mScanManager = new ScanManager();
    protected final IReporter mReporter = new Reporter();


    private final AtomicBoolean initializeIntentFilters = new AtomicBoolean(false);

    private final BroadcastReceiver visibleCountRequestReceiver = new BroadcastReceiver() {
        // This captures state change from the ScanManager
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            } else if (!intent.getAction().startsWith(StumblerServiceIntentActions.SVC_REQ_NS)) {
                return;
            }

            Context ctx = StumblerService.this.getApplicationContext();

            if (intent.getAction().equals(StumblerServiceIntentActions.SVC_REQ_VISIBLE_AP)) {
                broadcastCount(ctx, StumblerServiceIntentActions.SVC_RESP_VISIBLE_AP, getVisibleAPCount());
            } else if (intent.getAction().equals(StumblerServiceIntentActions.SVC_REQ_VISIBLE_CELL)) {
                broadcastCount(ctx, StumblerServiceIntentActions.SVC_RESP_VISIBLE_CELL, getVisibleCellCount());
            }

        };
    };

    /*
    Make a blocking synchronous response to requests for metrics.
     */
    static void broadcastCount(Context ctx, String svcRespVisibleAp, int visibleAPCount) {
        Intent intent = new Intent(svcRespVisibleAp);
        intent.putExtra("count", visibleAPCount);
        LocalBroadcastManager.getInstance(ctx).sendBroadcastSync(intent);
    }


    public StumblerService() {
        this("StumblerService");
    }

    public StumblerService(String name) {
        super(name);
    }

    private synchronized boolean isStopped() {
        return mScanManager.isStopped();
    }

    public synchronized boolean isScanning() {
        return mScanManager.isScanning();
    }

    public synchronized void startScanning() {
        mScanManager.startScanning();
    }

    public synchronized Prefs getPrefs(Context c) {
        return Prefs.getInstance(c);
    }

    public synchronized Location getLocation() {
        return mScanManager.getLocation();
    }

    private synchronized int getVisibleAPCount() {
        return mScanManager.getVisibleAPCount();
    }


    private synchronized int getVisibleCellCount() {
        return mScanManager.getVisibleCellInfoCount();
    }

    // Previously this was done in onCreate(). Moved out of that so that in the passive standalone service
    // use (i.e. Fennec), init() can be called from this class's dedicated thread.
    // Safe to call more than once, ensure added code complies with that intent.
    protected void init() {

        if (!initializeIntentFilters.getAndSet(true)) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(StumblerServiceIntentActions.SVC_REQ_VISIBLE_AP);
            filter.addAction(StumblerServiceIntentActions.SVC_REQ_VISIBLE_CELL);


            LocalBroadcastManager.getInstance(getApplicationContext())
                    .registerReceiver(visibleCountRequestReceiver,
                            filter);
        }

        // Don't remove, ensures that a Prefs instance is available for internal classes
        // that call Prefs.getInstanceWithoutContext()
        Prefs.getInstance(this);
        DataStorageManager.createGlobalInstance(this, this);

        mReporter.startup(this);
        mScanManager.initContext(this.getApplicationContext());
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

        if (isStopped()) {
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
                    Prefs.getInstance(StumblerService.this).setFirefoxScanEnabled(false);
                }

                if (DataStorageManager.getInstance() != null) {
                    DataStorageManager.getInstance().saveCachedReportsToDisk();
                }
                return null;
            }
        }.execute();

        mReporter.shutdown();
        mScanManager.stopScanning();
    }

    // This is the entry point for the stumbler thread.
    @Override
    public void onHandleIntent(Intent intent) {
        // Do init() in all cases, there is no cost, whereas it is easy to add code that depends on this.
        init();

        // Post-init(), set the mode to passive.
        setPassiveMode();

        if (intent == null) {
            return;
        }

        final boolean isScanEnabledInPrefs = Prefs.getInstance(this).getFirefoxScanEnabled();

        if (!isScanEnabledInPrefs && intent.getBooleanExtra(ACTION_NOT_FROM_HOST_APP, false)) {
            stopSelf();
            return;
        }

        boolean hasFilesWaiting = !DataStorageManager.getInstance().isDirEmpty();
        if (AppGlobals.isDebug) {
            Log.d(LOG_TAG, "Files waiting:" + hasFilesWaiting);
        }
        if (hasFilesWaiting) {
            // non-empty on startup, schedule an upload
            // This is the only upload trigger in Firefox mode
            // Firefox triggers this ~4 seconds after startup (after Gecko is loaded), add a small delay to avoid
            // clustering with other operations that are triggered at this time.
            final long lastAttemptedTime = Prefs.getInstance(this).getLastAttemptedUploadTime();
            final long timeNow = System.currentTimeMillis();

            if (timeNow - lastAttemptedTime < PASSIVE_UPLOAD_FREQ_GUARD_MSEC) {
                if (AppGlobals.isDebug) {
                    Log.d(LOG_TAG, "Upload attempt too frequent.");
                }
            } else {
                Prefs.getInstance(this).setLastAttemptedUploadTime(timeNow);
                UploadAlarmReceiver.scheduleAlarm(this, DELAY_IN_SEC_BEFORE_STARTING_UPLOAD_IN_PASSIVE_MODE, false /* no repeat*/);
            }
        }

        if (!isScanEnabledInPrefs) {
            Prefs.getInstance(this).setFirefoxScanEnabled(true);
        }

        String apiKey = intent.getStringExtra(ACTION_EXTRA_MOZ_API_KEY);
        if (apiKey != null && !apiKey.equals(Prefs.getInstance(this).getMozApiKey())) {
            Prefs.getInstance(this).setMozApiKey(apiKey);
        }

        String userAgent = intent.getStringExtra(ACTION_EXTRA_USER_AGENT);
        if (userAgent != null && !userAgent.equals(Prefs.getInstance(this).getUserAgent())) {
            Prefs.getInstance(this).setUserAgent(userAgent);
        }

        startScanning();
    }

    public void setPassiveMode() {
        mScanManager.setPassiveMode(true);
    }

    // Note that in passive mode, having data isn't an upload trigger, it is triggered by the start intent
    public synchronized void notifyStorageStateEmpty(boolean isEmpty) {
        if (isEmpty) {
            UploadAlarmReceiver.cancelAlarm(this);
        } else if (!mScanManager.isPassiveMode()) {
            UploadAlarmReceiver.scheduleAlarm(this, FREQUENCY_IN_SEC_OF_UPLOAD_IN_ACTIVE_MODE, true /* repeating */);
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        handleLowMemoryNotification();
    }

    @TargetApi(14)
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        handleLowMemoryNotification();
    }

    public void handleLowMemoryNotification() {
        IDataStorageManager manager = DataStorageManager.getInstance();
        if (manager == null) {
            return;
        }
        manager.saveCachedReportsToDisk();
    }
}
