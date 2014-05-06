package org.mozilla.mozstumbler;

import org.mozilla.mozstumbler.sync.SyncUtils;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.util.Calendar;

public final class ScannerService extends Service {
    public static final String  MESSAGE_TOPIC   = "org.mozilla.mozstumbler.serviceMessage";

    private static final String LOGTAG          = ScannerService.class.getName();
    private static final int    NOTIFICATION_ID = 1;
    private static final int    WAKE_TIMEOUT    = 5 * 1000;
    private static final String INTENT_TURN_OFF = MESSAGE_TOPIC + ".TURN_ME_OFF";
    private Scanner             mScanner;
    private Reporter            mReporter;
    private BroadcastReceiver   mTurnOffReceiver;
    private boolean             mIsBound;

    private final ScannerServiceInterface.Stub mBinder = new ScannerServiceInterface.Stub() {
        @Override
        public boolean isScanning() throws RemoteException {
            return mScanner.isScanning();
        }

        @Override
        public void startScanning() throws RemoteException {
            if (mScanner.isScanning()) {
                return;
            }

            CharSequence title = getResources().getText(R.string.service_name);
            CharSequence text = getResources().getText(R.string.service_scanning);
            postNotification(title, text);
            mScanner.startScanning();
        }

        @Override
        public void stopScanning() throws RemoteException {
            if (mScanner.isScanning()) {
                cancelNotification();
                stopForeground(true);

                mScanner.stopScanning();
                mReporter.flush();
                SyncUtils.TriggerRefresh(false);
                if (!mIsBound) {
                    stopSelf();
                }
            }
        }

        @Override
        public void checkPrefs() {
            mScanner.checkPrefs();
        }

        @Override
        public int getLocationCount() throws RemoteException {
            return mScanner.getLocationCount();
        }

        @Override
        public double getLatitude() throws RemoteException {
            return mScanner.getLatitude();
        }

        @Override
        public double getLongitude() throws RemoteException {
            return mScanner.getLongitude();
        }

        @Override
        public int getWifiStatus() throws RemoteException {
            return mScanner.getWifiStatus();
        }

        @Override
        public int getAPCount() throws RemoteException {
            return mScanner.getAPCount();
        }

        @Override
        public int getVisibleAPCount() throws RemoteException {
            return mScanner.getVisibleAPCount();
        }

        @Override
        public int getCellInfoCount() throws RemoteException {
            return mScanner.getCellInfoCount();
        }

        @Override
        public int getCurrentCellInfoCount() throws RemoteException {
            return mScanner.getCurrentCellInfoCount();
        }

        @Override
        public boolean isGeofenced () throws RemoteException {
            return mScanner.isGeofenced();
        }

    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(LOGTAG, "onCreate");

        mTurnOffReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(LOGTAG,"Got a request to turn off!");
                try {
                    if (mBinder.isScanning()) {
                        mBinder.stopScanning();
                    }
                } catch (RemoteException e) {
                    Log.e(LOGTAG, "", e);
                }
            }
        };

        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_BATTERY_LOW);
        intentFilter.addAction(INTENT_TURN_OFF);
        registerReceiver(mTurnOffReceiver, intentFilter);

        mScanner = new Scanner(this);
        mReporter = new Reporter(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(LOGTAG, "onDestroy");

        unregisterReceiver(mTurnOffReceiver);
        mTurnOffReceiver = null;

        cancelNotification();

        mReporter.shutdown();
        mReporter = null;
        mScanner = null;
    }

    private void postNotification(final CharSequence title, final CharSequence text) {
        Context ctx = getApplicationContext();
        Intent notificationIntent = new Intent(ctx, MainActivity.class);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_FROM_BACKGROUND);
        PendingIntent contentIntent = PendingIntent.getActivity(ctx, NOTIFICATION_ID, notificationIntent,
                PendingIntent.FLAG_CANCEL_CURRENT);
        int icon = R.drawable.ic_status_scanning;
        Notification n = buildNotification(ctx, icon, title, text, contentIntent);
        startForeground(NOTIFICATION_ID, n);
    }

    private void cancelNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.cancel(NOTIFICATION_ID);
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

    private static Notification buildNotification(Context context, int icon,
                                                  CharSequence contentTitle,
                                                  CharSequence contentText,
                                                  PendingIntent contentIntent) {
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context,0,new Intent(INTENT_TURN_OFF),0);
        return new NotificationCompat.Builder(context)
                                     .setSmallIcon(icon)
                                     .setContentTitle(contentTitle)
                                     .setContentText(contentText)
                                     .setContentIntent(contentIntent)
                                     .setOngoing(true)
                                     .setPriority(NotificationCompat.PRIORITY_LOW)
                                     .addAction(R.drawable.ic_action_cancel, context.getResources().getString(R.string.stop_scanning), pendingIntent)
                                     .build();
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
