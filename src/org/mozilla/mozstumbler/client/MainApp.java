/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client;

import android.annotation.TargetApi;
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
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import org.mozilla.mozstumbler.BuildConfig;
import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.cellscanner.DefaultCellScanner;
import org.mozilla.mozstumbler.client.mapview.ObservedLocationsReceiver;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.DataStorageManager;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.GPSScanner;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.WifiScanner;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.cellscanner.CellScanner;
import org.mozilla.mozstumbler.service.uploadthread.AsyncUploader;
import org.mozilla.mozstumbler.service.utils.NetworkUtils;

public class MainApp extends Application {

    private final String LOG_TAG = AppGlobals.LOG_PREFIX + MainApp.class.getSimpleName();
    private ClientStumblerService mStumblerService;
    private ServiceConnection mConnection;
    private ServiceBroadcastReceiver mReceiver;
    private WeakReference<IMainActivity> mMainActivity = new WeakReference<IMainActivity>(null);
    private final long MAX_BYTES_DISK_STORAGE = 1000 * 1000 * 20; // 20MB for MozStumbler by default, is ok?
    private final int MAX_WEEKS_OLD_STORED = 4;
    public static final String INTENT_TURN_OFF = "org.mozilla.mozstumbler.turnMeOff";
    private static final int    NOTIFICATION_ID = 1;

    public ClientPrefs getPrefs() {
        return ClientPrefs.getInstance();
    }

    public ClientStumblerService getService() {
        return mStumblerService;
    }

    public void setMainActivity(IMainActivity mainActivity) {
        mMainActivity = new WeakReference<IMainActivity>(mainActivity);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        AppGlobals.isDebug = BuildConfig.DEBUG;
        AppGlobals.appVersionName = BuildConfig.VERSION_NAME;
        AppGlobals.appVersionCode = BuildConfig.VERSION_CODE;
        AppGlobals.appName = this.getResources().getString(R.string.app_name);

        final String OLD_NAME = "org.mozilla.mozstumbler.preferences.Prefs.xml";
        File dir = this.getDir("shared_prefs", Context.MODE_PRIVATE);
        File oldPrefs = new File(dir, OLD_NAME);
        if (oldPrefs.exists()) {
          oldPrefs.renameTo(new File(dir, ClientPrefs.getPrefsFileNameForUpgrade()));
        }

        ClientPrefs.createGlobalInstance(this);
        NetworkUtils.createGlobalInstance(this);
        LogActivity.LogMessageReceiver.createGlobalInstance(this);
        CellScanner.setCellScannerImpl(new DefaultCellScanner(this));

        if (AppGlobals.isDebug) {
            enableStrictMode();
        }

        mReceiver = new ServiceBroadcastReceiver();
        mReceiver.register();

        mConnection = new ServiceConnection() {
            public void onServiceConnected(ComponentName className, IBinder binder) {
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
    }

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
        // This will create, and register the receiver
        ObservedLocationsReceiver.getInstance(this.getApplicationContext());
        
        mStumblerService.startForeground(NOTIFICATION_ID, buildNotification());
        mStumblerService.startScanning();
    }

    public void stopScanning() {
        mStumblerService.stopForeground(true);
        mStumblerService.stopScanning();

        AsyncUploader.UploadSettings settings =
            new AsyncUploader.UploadSettings(ClientPrefs.getInstance().getWifiScanAlways(),
                    ClientPrefs.getInstance().getUseWifiOnly());
        AsyncUploader uploader = new AsyncUploader(settings, null /* don't need to listen for completion */);
        uploader.setNickname(ClientPrefs.getInstance().getNickname());
        uploader.execute();
    }

    public void toggleScanning(MainActivity caller) {
        boolean scanning = mStumblerService.isScanning();

        if (scanning) {
            stopScanning();
        } else {
            startScanning();
            caller.checkGps();
        }
    }

    @TargetApi(9)
    private void enableStrictMode() {
        if (Build.VERSION.SDK_INT < 9) {
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
                intentFilter.addAction(GPSScanner.ACTION_GPS_UPDATED);
                intentFilter.addAction(GPSScanner.ACTION_NMEA_RECEIVED);
                intentFilter.addAction(MainActivity.ACTION_UPDATE_UI);
                intentFilter.addAction(INTENT_TURN_OFF);
                LocalBroadcastManager.getInstance(MainApp.this).registerReceiver(this, intentFilter);
            }
        }

        public void unregister() {
            if (mReceiverIsRegistered) {
                LocalBroadcastManager.getInstance(MainApp.this).unregisterReceiver(this);
                mReceiverIsRegistered = false;
            }
        }

        private void receivedGpsMessage(Intent intent) {
            String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);
            if (subject.equals(GPSScanner.SUBJECT_NEW_STATUS) && mMainActivity.get() != null) {
                mMainActivity.get().setGpsFixes(intent.getIntExtra(GPSScanner.NEW_STATUS_ARG_FIXES, 0));
                mMainActivity.get().setGpsSats(intent.getIntExtra(GPSScanner.NEW_STATUS_ARG_SATS, 0));
            }
        }

        private void receivedNmeaMessage(Intent intent) {
            String nmea_data = intent.getStringExtra(GPSScanner.NMEA_DATA);


            if (nmea_data != null) {
                // TODO: we should probably have some kind of visual
                // indicator to note that GPS NMEA data is being
                // actively received.
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();

            if (action.equals(GPSScanner.ACTION_GPS_UPDATED)) {
                receivedGpsMessage(intent);
            }

            if (action.equals(GPSScanner.ACTION_NMEA_RECEIVED)) {
                receivedNmeaMessage(intent);
            }

            if (mMainActivity.get() != null) {
                mMainActivity.get().updateUiOnMainThread();
            }
        }
    }

    private Notification buildNotification() {
        Context context = getApplicationContext();
        Intent turnOffIntent = new Intent(INTENT_TURN_OFF);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, turnOffIntent, 0);

        Intent notificationIntent = new Intent(context, MainActivity.class);
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
                        getString(R.string.stop_scanning), pendingIntent)
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
                if (db != null) {
                    db.close();
                }
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
            cursor.close();
            if (db != null) {
                db.close();
            }
            dbFile.delete();
        }
    }
}
