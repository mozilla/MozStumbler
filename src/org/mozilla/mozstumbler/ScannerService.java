package org.mozilla.mozstumbler;

import java.util.Calendar;

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
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

        mLooper.interrupt();
        mLooper = null;
        mScanner = null;

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.cancel(NOTIFICATION_ID);
    }

    private void postNotification() {
        mLooper.post(new Runnable() {
            @Override
            public void run() {
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                Context ctx = getApplicationContext();
                Intent notificationIntent = new Intent(ctx, MainActivity.class);
                notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_FROM_BACKGROUND);

                PendingIntent contentIntent = PendingIntent.getActivity(ctx, NOTIFICATION_ID, notificationIntent,
                        PendingIntent.FLAG_CANCEL_CURRENT);

                Resources res = ctx.getResources();
                String title = res.getString(R.string.service_name);
                String text = res.getString(R.string.service_scanning);

                int icon = R.drawable.ic_status_scanning;
                Notification n = buildNotification(ctx, icon, title, text, contentIntent);
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

        return new ScannerServiceInterface.Stub() {
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
                        postNotification();
                        mScanner.startScanning();

                        // keep us awake.
                        Context cxt = getApplicationContext();
                        Calendar cal = Calendar.getInstance();
                        Intent intent = new Intent(cxt, ScannerService.class);
                        mWakeIntent = PendingIntent.getService(cxt, 0, intent, 0);
                        AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                        alarm.setRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), WAKE_TIMEOUT, mWakeIntent);
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
                    }
                });
            }

            @Override
            public int numberOfReportedLocations() throws RemoteException {
                return mReporter.numberOfReportedLocations();
            }
        };
    }

    @SuppressWarnings("deprecation")
    private static Notification buildNotification(Context context, int icon,
                                                  String contentTitle,
                                                  String contentText,
                                                  PendingIntent contentIntent) {
        Notification n = new Notification(icon, contentTitle, 0);
        n.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
        n.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
        return n;
    }

    private void sendToast(String message) {
        Intent i = new Intent(MESSAGE_TOPIC);
        i.putExtra(Intent.EXTRA_SUBJECT, "Notification");
        i.putExtra(Intent.EXTRA_TEXT, message);
        sendBroadcast(i);
    }
}
