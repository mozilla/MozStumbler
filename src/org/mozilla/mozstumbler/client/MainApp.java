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
import android.os.Build;
import android.os.IBinder;
import android.os.StrictMode;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import java.io.File;
import org.mozilla.mozstumbler.BuildConfig;
import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.cellscanner.DefaultCellScanner;
import org.mozilla.mozstumbler.client.mapview.MapActivity;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.StumblerService;
import org.mozilla.mozstumbler.service.datahandling.DataStorageManager;
import org.mozilla.mozstumbler.service.scanners.GPSScanner;
import org.mozilla.mozstumbler.service.scanners.WifiScanner;
import org.mozilla.mozstumbler.service.scanners.cellscanner.CellScanner;
import org.mozilla.mozstumbler.service.sync.AsyncUploader;
import org.mozilla.mozstumbler.service.utils.NetworkUtils;

public class MainApp extends Application {

    private final String LOG_TAG = MainApp.class.getSimpleName();
    private StumblerService mStumblerService;
    private ServiceConnection mConnection;
    private ServiceBroadcastReceiver mReceiver;
    private MainActivity mMainActivity;
    private final long MAX_BYTES_DISK_STORAGE = 1000 * 1000 * 20; // 20MB for MozStumbler by default, is ok?
    private final int MAX_WEEKS_OLD_STORED = 4;
    private static final String INTENT_TURN_OFF = "org.mozilla.mozstumbler.turnMeOff";
    private static final int    NOTIFICATION_ID = 1;

    public Prefs getPrefs() {
        return Prefs.getInstance();
    }

    public StumblerService getService() {
        return mStumblerService;
    }

    public void setMainActivity(MainActivity mainActivity) {
        mMainActivity = mainActivity;
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
          oldPrefs.renameTo(new File(dir, Prefs.PREFS_FILE));
        }

        Prefs.createGlobalInstance(this);
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
                StumblerService.StumblerBinder serviceBinder = (StumblerService.StumblerBinder) binder;
                mStumblerService = serviceBinder.getService();

                DataStorageManager.getInstance().setMaxStorageOnDisk(MAX_BYTES_DISK_STORAGE);
                DataStorageManager.getInstance().setMaxWeeksStored(MAX_WEEKS_OLD_STORED);

                MapActivity.createGpsTrackLocationReceiver(MainApp.this);

                Log.d(LOG_TAG, "Service connected");
                if (mMainActivity != null) {
                    mMainActivity.updateUiOnMainThread();
                }
            }

            public void onServiceDisconnected(ComponentName className) {
                mStumblerService = null;
                Log.d(LOG_TAG, "Service disconnected", new Exception());
            }
        };

        Intent intent = new Intent(this, StumblerService.class);
        startService(intent);
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
        Log.d(LOG_TAG, "onStop");
    }

    private void startScanning() {
        mStumblerService.startForeground(NOTIFICATION_ID, buildNotification());
        mStumblerService.startScanning();
    }

    private void stopScanning() {
        mStumblerService.stopForeground(true);
        mStumblerService.stopScanning();

        AsyncUploader.UploadSettings settings =
            new AsyncUploader.UploadSettings(Prefs.getInstance().getWifiScanAlways(), Prefs.getInstance().getUseWifiOnly());
        AsyncUploader uploader = new AsyncUploader(settings, null /* don't need to listen for completion */);
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
                intentFilter.addAction(MainActivity.ACTION_UNPAUSE_SCANNING);
                intentFilter.addAction(MainActivity.ACTION_UPDATE_UI);
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
            if (subject.equals(GPSScanner.SUBJECT_NEW_STATUS) && mMainActivity != null) {
                mMainActivity.mGpsFixes = intent.getIntExtra(GPSScanner.NEW_STATUS_ARG_FIXES ,0);
                mMainActivity.mGpsSats = intent.getIntExtra(GPSScanner.NEW_STATUS_ARG_SATS, 0);
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();

            if (action.equals(GPSScanner.ACTION_GPS_UPDATED)) {
                receivedGpsMessage(intent);
            } else if (action.equals(MainActivity.ACTION_UNPAUSE_SCANNING) &&
                    null != mStumblerService) {
                startScanning();
            }

            if (mMainActivity != null) {
                mMainActivity.updateUiOnMainThread();
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
}
