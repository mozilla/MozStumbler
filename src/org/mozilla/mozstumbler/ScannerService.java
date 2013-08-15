package org.mozilla.mozstumbler;

import java.util.Calendar;

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

public final class ScannerService extends Service {
    public static final String  MESSAGE_TOPIC   = "org.mozilla.mozstumbler.serviceMessage";

    private static final String LOGTAG          = ScannerService.class.getName();
    private static final int    NOTIFICATION_ID = 0;
    private static final int    WAKE_TIMEOUT    = 5 * 1000;

    private Scanner             mScanner;
    private Reporter            mReporter;
    private LooperThread        mLooper;
    private PendingIntent       mWakeIntent;
    private BroadcastReceiver   mBatteryLowReceiver;

    private final ScannerServiceInterface.Stub mBinder = new ScannerServiceInterface.Stub() {
        @Override
        public boolean isScanning() throws RemoteException {
            return mScanner.isScanning();
        };

        @Override
        public void startScanning() throws RemoteException {
            if (mScanner.isScanning()) {
                return;
            }

            mLooper.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        Log.d(LOGTAG, "Running looper...");


                        mScanner.startScanning();

                        // keep us awake.
                        Context cxt = getApplicationContext();
                        Calendar cal = Calendar.getInstance();
                        Intent intent = new Intent(cxt, ScannerService.class);
                        mWakeIntent = PendingIntent.getService(cxt, 0, intent, 0);
                        AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                        alarm.setRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), WAKE_TIMEOUT, mWakeIntent);

                        mReporter.sendReports(false);
                    } catch (Exception e) {
                        Log.d(LOGTAG, "looper shat itself : " + e);
                    }
                }
            });
        };

        @Override
        public void stopScanning() throws RemoteException {
            if (!mScanner.isScanning()) {
                return;
            }

            mLooper.post(new Runnable() {
                @Override
                public void run() {
                    AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                    alarm.cancel(mWakeIntent);

                    NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    nm.cancel(NOTIFICATION_ID);

                    mScanner.stopScanning();

                    mReporter.sendReports(true);
                }
            });
        }

        @Override
        public int numberOfReportedLocations() throws RemoteException {
            return mReporter.numberOfReportedLocations();
        }
    };

    private final ScannerServiceInterface.Stub mBinder = new ScannerServiceInterface.Stub() {
        @Override
        public boolean isScanning() throws RemoteException {
            return mScanner.isScanning();
        };

        @Override
        public void startScanning() throws RemoteException {
            if (mScanner.isScanning()) {
                return;
            }

            mLooper.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        Log.d(LOGTAG, "Running looper...");

                        String title = getResources().getString(R.string.service_name);
                        String text = getResources().getString(R.string.service_scanning);
                        postNotification(title, text, Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT);

                        mScanner.startScanning();

                        // keep us awake.
                        Context cxt = getApplicationContext();
                        Calendar cal = Calendar.getInstance();
                        Intent intent = new Intent(cxt, ScannerService.class);
                        mWakeIntent = PendingIntent.getService(cxt, 0, intent, 0);
                        AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                        alarm.setRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), WAKE_TIMEOUT, mWakeIntent);

                        mReporter.sendReports(false);
                    } catch (Exception e) {
                        Log.d(LOGTAG, "looper shat itself : " + e);
                    }
                }
            });
        };

        @Override
        public void stopScanning() throws RemoteException {
            if (!mScanner.isScanning()) {
                return;
            }

            mLooper.post(new Runnable() {
                @Override
                public void run() {
                    AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                    alarm.cancel(mWakeIntent);

                    NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    nm.cancel(NOTIFICATION_ID);

                    mScanner.stopScanning();

                    mReporter.sendReports(true);
                }
            });
        }

        @Override
        public int numberOfReportedLocations() throws RemoteException {
            return mReporter.numberOfReportedLocations();
        }
    };

    private final class LooperThread extends Thread {
        private Handler mHandler;

        @Override
        public void run() {
            Looper.prepare();
            mHandler = new Handler();
            Looper.loop();
        }

        void post(Runnable runnable) {
            if (mHandler != null) {
                mHandler.post(runnable);
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(LOGTAG, "onCreate");

        mBatteryLowReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(LOGTAG, "Got battery low broadcast!");
                try {
                    if (mBinder.isScanning()) {
                        mBinder.stopScanning();

                        String title = getResources().getString(R.string.service_name);
                        String batteryLowWarning = getResources().getString(R.string.battery_low_warning);
                        postNotification(title, batteryLowWarning, Notification.FLAG_AUTO_CANCEL);
                    }
                } catch (RemoteException e) {
                    // TODO Copy-pasted catch block
                    e.printStackTrace();
                }
            }
        };

        registerReceiver(mBatteryLowReceiver, new IntentFilter(Intent.ACTION_BATTERY_LOW));

        Prefs prefs = new Prefs(this);
        mReporter = new Reporter(this, prefs);
        mScanner = new Scanner(this, mReporter);
        mLooper = new LooperThread();
        mLooper.start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(LOGTAG, "onDestroy");

        unregisterReceiver(mBatteryLowReceiver);
        mBatteryLowReceiver = null;

        mLooper.interrupt();
        mLooper = null;
        mScanner = null;

        mReporter.shutdown();
        mReporter = null; 

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.cancel(NOTIFICATION_ID);
    }

    private void postNotification(final String title, final String text, final int flags) {
        mLooper.post(new Runnable() {
            @Override
            public void run() {
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                Context ctx = getApplicationContext();
                Intent notificationIntent = new Intent(ctx, MainActivity.class);
                notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_FROM_BACKGROUND);

                PendingIntent contentIntent = PendingIntent.getActivity(ctx, NOTIFICATION_ID, notificationIntent,
                        PendingIntent.FLAG_CANCEL_CURRENT);

                int icon = R.drawable.ic_status_scanning;
                Notification n = buildNotification(ctx, icon, title, text, contentIntent, flags);
                nm.notify(NOTIFICATION_ID, n);
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // keep running!
        return Service.START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(LOGTAG, "onBind");
        return mBinder;
    }

    @SuppressWarnings("deprecation")
    private static Notification buildNotification(Context context, int icon,
                                                  String contentTitle,
                                                  String contentText,
                                                  PendingIntent contentIntent,
                                                  int flags) {
        Notification n = new Notification(icon, contentTitle, 0);
        n.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
        n.flags |= flags;
        return n;
    }
}
