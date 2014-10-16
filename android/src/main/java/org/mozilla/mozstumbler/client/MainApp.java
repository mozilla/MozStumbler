/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.IBinder;
import android.os.StrictMode;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.acra.ACRA;
import org.acra.sender.HttpSender;
import org.mozilla.mozstumbler.BuildConfig;
import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.cellscanner.DefaultCellScanner;
import org.mozilla.mozstumbler.client.navdrawer.MainDrawerActivity;
import org.mozilla.mozstumbler.client.subactivities.DeveloperActivity;
import org.mozilla.mozstumbler.client.subactivities.LogActivity;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.core.logging.MockAcraLog;
import org.mozilla.mozstumbler.service.stumblerthread.StumblerService;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.DataStorageManager;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.WifiScanner;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.cellscanner.CellScanner;
import org.mozilla.mozstumbler.service.uploadthread.AsyncUploadParam;
import org.mozilla.mozstumbler.service.uploadthread.AsyncUploader;
import org.mozilla.mozstumbler.service.utils.NetworkInfo;
import org.osmdroid.tileprovider.constants.TileFilePath;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.acra.annotation.ReportsCrashes;

@ReportsCrashes(
    formKey="",
    httpMethod = HttpSender.Method.PUT,
    reportType = HttpSender.Type.JSON,
    formUri = BuildConfig.ACRA_URI,
    formUriBasicAuthLogin = BuildConfig.ACRA_USER,
    formUriBasicAuthPassword = BuildConfig.ACRA_PASS)
public class MainApp extends Application
        implements ObservedLocationsReceiver.ICountObserver,
        AsyncUploader.AsyncUploaderListener {
    public static final AtomicBoolean isUploading = new AtomicBoolean();
    private final String LOG_TAG = AppGlobals.LOG_PREFIX + MainApp.class.getSimpleName();
    private ClientStumblerService mStumblerService;
    private ServiceConnection mConnection;
    private ServiceBroadcastReceiver mReceiver;
    private WeakReference<IMainActivity> mMainActivity = new WeakReference<IMainActivity>(null);
    private final long MAX_BYTES_DISK_STORAGE = 1000 * 1000 * 20; // 20MB for MozStumbler by default, is ok?
    private final int MAX_WEEKS_OLD_STORED = 4;
    public static final String INTENT_TURN_OFF = "org.mozilla.mozstumbler.turnMeOff";
    private static final int NOTIFICATION_ID = 1;
    public static final String ACTION_BASE = AppGlobals.ACTION_NAMESPACE + ".MainApp.";
    public static final String ACTION_UI_UNPAUSE_SCANNING = ACTION_BASE + "UNPAUSE_SCANNING";
    public static final String ACTION_UI_PAUSE_SCANNING = ACTION_BASE + "PAUSE_SCANNING";

    public ClientPrefs getPrefs() {
        return ClientPrefs.getInstance();
    }

    public ClientStumblerService getService() {
        return mStumblerService;
    }

    public void setMainActivity(IMainActivity mainActivity) {
        mMainActivity = new WeakReference<IMainActivity>(mainActivity);
    }

    private File getCacheDir(Context c) {
        File dir = c.getExternalCacheDir();

        if (dir == null) {
            dir = c.getCacheDir();
        }

        if (dir == null) {
            Log.i(LOG_TAG, "No tile storage available");
            return null;
        }

        return new File(dir, "osmdroid");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // The following line triggers the initialization of ACRA
        ACRA.init(this);

        // This must be called after init to get a copy of the
        // original ACRA.log instance so that we can toggle the logger on
        // and off.
        MockAcraLog.setOriginalLog();

        TileFilePath.directoryOverride = getCacheDir(getApplicationContext());

        AppGlobals.isDebug = BuildConfig.DEBUG;
        AppGlobals.isRobolectric = BuildConfig.ROBOLECTRIC;
        AppGlobals.appVersionName = BuildConfig.VERSION_NAME;
        AppGlobals.appVersionCode = BuildConfig.VERSION_CODE;
        AppGlobals.appName = this.getResources().getString(R.string.app_name);

        AsyncUploader.setGlobalUploadListener(this);

        final String OLD_NAME = "org.mozilla.mozstumbler.preferences.Prefs.xml";
        File dir = this.getDir("shared_prefs", Context.MODE_PRIVATE);
        File oldPrefs = new File(dir, OLD_NAME);
        if (oldPrefs.exists()) {
            oldPrefs.renameTo(new File(dir, ClientPrefs.getPrefsFileNameForUpgrade()));
        }

        Prefs prefs = ClientPrefs.createGlobalInstance(this);
        prefs.setMozApiKey(BuildConfig.MOZILLA_API_KEY);
        String userAgent = System.getProperty("http.agent") + " " +
                AppGlobals.appName + "/" + AppGlobals.appVersionName;
        prefs.setUserAgent(userAgent);

        NetworkInfo.createGlobalInstance(this);
        LogActivity.LogMessageReceiver.createGlobalInstance(this);
        CellScanner.setCellScannerImpl(new DefaultCellScanner(this));
        // This will create, and register the receiver
        ObservedLocationsReceiver.createGlobalInstance(this.getApplicationContext(), this);

        enableStrictMode();

        mReceiver = new ServiceBroadcastReceiver();
        mReceiver.register();

        mConnection = new ServiceConnection() {
            public void onServiceConnected(ComponentName className, IBinder binder) {
                // binder can be null, android is terrible.
                if (binder == null) {
                    return;
                }

                ClientStumblerService.StumblerBinder serviceBinder = (ClientStumblerService.StumblerBinder) binder;
                mStumblerService = serviceBinder.getServiceAndInitialize(Thread.currentThread(),
                        MAX_BYTES_DISK_STORAGE, MAX_WEEKS_OLD_STORED);
                mStumblerService.setWifiBlockList(new WifiBlockLists());
                // Upgrade the db, if needed
                Map<String, Long> oldStats = getOldDbStats(MainApp.this);
                if (oldStats != null) {
                    long last_upload_time = oldStats.get("last_upload_time");
                    long observations_sent = oldStats.get("observations_sent");
                    long wifis_sent = oldStats.get("wifis_sent");
                    long cells_sent = oldStats.get("cells_sent");
                    try {
                        DataStorageManager.getInstance().writeSyncStats(last_upload_time, 0, observations_sent, wifis_sent, cells_sent);
                    } catch (IOException ex) {
                        Log.e(LOG_TAG, "Exception in DataStorageManager upgrading db:", ex);
                    }
                }

                Log.d(LOG_TAG, "Service connected");
                if (mMainActivity.get() != null) {
                    mMainActivity.get().updateUiOnMainThread();
                }
            }

            public void onServiceDisconnected(ComponentName className) {
                mStumblerService = null;
                Log.d(LOG_TAG, "Service disconnected", new Exception());
            }
        };

        Intent intent = new Intent(this, ClientStumblerService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        // Register a listener for a toggle event in the notification pulldown
        LocalBroadcastManager bManager = LocalBroadcastManager.getInstance(this);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_UI_UNPAUSE_SCANNING);
        intentFilter.addAction(ACTION_UI_PAUSE_SCANNING);
        bManager.registerReceiver(notificationDrawerEventReceiver, intentFilter);
    }

    private final BroadcastReceiver notificationDrawerEventReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            StumblerService service = getService();
            if (service == null) {
                return;
            }

            if (intent.getAction().equals(ACTION_UI_PAUSE_SCANNING) && service.isScanning()) {
                stopScanning();
            } else if (intent.getAction().equals(ACTION_UI_UNPAUSE_SCANNING) && !service.isScanning()) {
                startScanning();
            }
        }
    };

    @Override
    public void onTerminate() {
        super.onTerminate();
        if (mConnection != null) {
            unbindService(mConnection);
        }
        mConnection = null;
        mStumblerService = null;
        if (mReceiver != null) {
            mReceiver.unregister();
        }
        mReceiver = null;
        Log.d(LOG_TAG, "onTerminate");
    }

    public void startScanning() {
        mStumblerService.startForeground(NOTIFICATION_ID, buildNotification());
        mStumblerService.startScanning();
        if (mMainActivity.get() != null) {
            mMainActivity.get().updateUiOnMainThread();
        }
    }

    public void stopScanning() {
        mStumblerService.stopForeground(true);
        mStumblerService.stopScanning();
        if (mMainActivity.get() != null) {
            mMainActivity.get().updateUiOnMainThread();
        }

        AsyncUploader uploader = new AsyncUploader();
        AsyncUploadParam param = new AsyncUploadParam(
                ClientPrefs.getInstance().getUseWifiOnly(),
                Prefs.getInstance().getNickname(),
                Prefs.getInstance().getEmail());

        uploader.execute(param);

    }

    @TargetApi(9)
    private void enableStrictMode() {
        if (!AppGlobals.isDebug) {
            return;
        }

        if (Build.VERSION.SDK_INT < 9) {
            return;
        }

        if (AppGlobals.isRobolectric) {
            return;
        }

        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .permitDiskReads()
                .permitDiskWrites()
                .penaltyLog().build());

        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog().build());
    }

    private class ServiceBroadcastReceiver extends BroadcastReceiver {
        private boolean mReceiverIsRegistered;

        public void register() {
            if (!mReceiverIsRegistered) {
                mReceiverIsRegistered = true;

                IntentFilter intentFilter = new IntentFilter();
                intentFilter.addAction(WifiScanner.ACTION_WIFIS_SCANNED);
                intentFilter.addAction(CellScanner.ACTION_CELLS_SCANNED);
                LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(this, intentFilter);

                // This can't be a local broadcast as it comes from notification menu
                getApplicationContext().registerReceiver(this, new IntentFilter(INTENT_TURN_OFF));
            }
        }

        public void unregister() {
            if (mReceiverIsRegistered) {
                LocalBroadcastManager.getInstance(MainApp.this).unregisterReceiver(this);
                mReceiverIsRegistered = false;
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(INTENT_TURN_OFF)) {
                Log.d(LOG_TAG, "INTENT_TURN_OFF");
                stopScanning();
            }

            if (mMainActivity.get() != null) {
                mMainActivity.get().updateUiOnMainThread();
            }
        }
    }

    private Notification buildNotification() {
        Context context = getApplicationContext();
        PendingIntent turnOffIntent = PendingIntent.getBroadcast(context, 0, new Intent(INTENT_TURN_OFF), 0);

        Intent notificationIntent = new Intent(context, MainDrawerActivity.class);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_FROM_BACKGROUND);
        PendingIntent contentIntent = PendingIntent.getActivity(context, NOTIFICATION_ID,
                notificationIntent,
                PendingIntent.FLAG_CANCEL_CURRENT);

        return new NotificationCompat.Builder(context)
                .setSmallIcon(R.drawable.ic_status_scanning)
                .setContentTitle(getText(R.string.service_name))
                .setContentText(getText(R.string.service_scanning))
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(R.drawable.ic_action_cancel,
                        getString(R.string.stop_scanning), turnOffIntent)
                .build();

    }

    private Map<String, Long> getOldDbStats(Context context) {
        final File dbFile = new File(context.getFilesDir().getParent() + "/databases/" + "stumbler.db");
        if (!dbFile.exists()) {
            return null;
        }

        SQLiteDatabase db = null;
        Cursor cursor = null;

        try {
            db = SQLiteDatabase.openDatabase(dbFile.toString(), null, 0);
            cursor = db.rawQuery("select * from stats", null);
            if (cursor == null || !cursor.moveToFirst()) {
                db.close();
                return null;
            }

            Map<String, Long> kv = new HashMap<String, Long>();
            while (!cursor.isAfterLast()) {
                String key = cursor.getString(cursor.getColumnIndex("key"));
                Long value = cursor.getLong(cursor.getColumnIndex("value"));
                kv.put(key, value);
                cursor.moveToNext();
            }
            return kv;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (db != null) {
                db.close();
            }
            dbFile.delete();
        }
    }

    private int observationCount = 0;
    public void observedLocationCountIncrement() {
        observationCount++;
        if (mMainActivity.get() != null) {
            mMainActivity.get().displayObservationCount(observationCount);
        }
    }

    public void showDeveloperDialog(Activity activity) {
       if (getService() == null) {
           return;
       }

       // TODO stop doing this, move it to kml only
       if (getService().isScanning()) {
           stopScanning();
           if (mMainActivity.get() != null) {
               mMainActivity.get().updateUiOnMainThread();
           }
       }
       activity.startActivity(new Intent(activity, DeveloperActivity.class));
    }

    @Override
    public void onUploadProgress(boolean isUploading) {
        if (mMainActivity.get() != null) {
            mMainActivity.get().setUploadState(isUploading);
        }
    }

    public void keepScreenOnPrefChanged(boolean isEnabled) {
        if (mMainActivity.get() != null) {
            mMainActivity.get().keepScreenOnChanged(isEnabled);
        }
    }

    private static boolean sHasBootedOnce;
    public static boolean getAndSetHasBootedOnce() {
        boolean b = sHasBootedOnce;
        sHasBootedOnce = true;
        return b;
    }
}
