package org.mozilla.mozstumbler;

import java.util.Calendar;

import org.mozilla.mozstumbler.preferences.Prefs;
import org.mozilla.mozstumbler.ActivityRecognitionIntentService;

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
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.text.format.DateFormat;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient.ConnectionCallbacks;
import com.google.android.gms.common.GooglePlayServicesClient.OnConnectionFailedListener;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

public final class ScannerService extends Service {
    public static final String  MESSAGE_TOPIC   = "org.mozilla.mozstumbler.serviceMessage";

    private static final String LOGTAG          = ScannerService.class.getName();
    private static final int    NOTIFICATION_ID = 1;
    private static final int    WAKE_TIMEOUT    = 5 * 1000;
    private static final int    ACTIVITY_DETECTION_INTERVAL = 30 * 1000;
    private Scanner             mScanner;
    private Reporter            mReporter;
    private LooperThread        mLooper;
    private PendingIntent       mWakeIntent;
    private PendingIntent       mActivityRecognitionPendingIntent;
    private BroadcastReceiver   mBatteryLowReceiver;
    private BroadcastReceiver   mActivityRecognitionReceiver;
    private ActivityRecognitionClient mActivityRecognitionClient;
    private int                 mDetectedActivity = DetectedActivity.UNKNOWN;

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

            mLooper.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        CharSequence title = getResources().getText(R.string.service_name);
                        CharSequence text = getResources().getText(R.string.service_scanning);
                        postNotification(title, text);

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
        }

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

                    cancelNotification();
                    stopForeground(true);

                    mScanner.stopScanning();

                    mReporter.sendReports(true);
                }
            });
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
        public int getAPCount() throws RemoteException {
            return mScanner.getAPCount();
        }

        @Override
        public int getVisibleAPCount() throws RemoteException {
            return mScanner.getVisibleAPCount();
        }

        @Override
        public long getLastUploadTime() throws RemoteException {
            return mReporter.getLastUploadTime();
        }

        @Override
        public long getReportsSent () throws RemoteException {
            return mReporter.getReportsSent();
        }

        @Override
        public String getDetectedActivity() throws RemoteException {
            return getResources().getString(getStringIdForDetectedActivity());
        }

        private int getStringIdForDetectedActivity() {
            switch (mDetectedActivity) {
                case DetectedActivity.IN_VEHICLE:
                    return R.string.detected_activity_in_vehicle;
                case DetectedActivity.ON_BICYCLE:
                    return R.string.detected_activity_on_bicycle;
                case DetectedActivity.ON_FOOT:
                    return R.string.detected_activity_on_foot;
                case DetectedActivity.TILTING:
                    return R.string.detected_activity_tilting;
                case DetectedActivity.STILL:
                    return R.string.detected_activity_still;
                default:
                case DetectedActivity.UNKNOWN:
                    return R.string.detected_activity_unknown;
            }
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

    private void startActivityTracking() {

        int status = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (status != ConnectionResult.SUCCESS) {
            // TODO: What should we do here?
            Log.d(LOGTAG, "No google play services!");
            return;
        }
  
        mActivityRecognitionClient = new ActivityRecognitionClient(this, new ConnectionCallbacks() {

            @Override
            public void onConnected(Bundle bundle) {
                Intent intent = new Intent(ScannerService.this, ActivityRecognitionIntentService.class);
                mActivityRecognitionPendingIntent = PendingIntent.getService(ScannerService.this,
                                                                             0,
                                                                             intent,
                                                                             PendingIntent.FLAG_UPDATE_CURRENT);

                mActivityRecognitionClient.requestActivityUpdates(ACTIVITY_DETECTION_INTERVAL,
                                                                  mActivityRecognitionPendingIntent);
            }

            @Override
            public void onDisconnected() {
                mActivityRecognitionPendingIntent = null;

            }

        }, new OnConnectionFailedListener() {
                @Override
                public void onConnectionFailed(ConnectionResult result) {

                }
        });

        mActivityRecognitionClient.connect();
    }

    private void stopActivityTracking() {
        if (mActivityRecognitionClient == null || !mActivityRecognitionClient.isConnected()) {
            return;
        }

        mActivityRecognitionClient.removeActivityUpdates(mActivityRecognitionPendingIntent);
        mActivityRecognitionClient.disconnect();
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
                    }
                } catch (RemoteException e) {
                    Log.e(LOGTAG, "", e);
                }
            }
        };
        registerReceiver(mBatteryLowReceiver, new IntentFilter(Intent.ACTION_BATTERY_LOW));

        mActivityRecognitionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int activityType = intent.getIntExtra("activity_type", DetectedActivity.UNKNOWN);
                int confidence = intent.getIntExtra("confidence", -1);

                if (confidence < 50) {
                    return;
                }

                try {
                    switch (activityType) {
                        case DetectedActivity.IN_VEHICLE:
                        case DetectedActivity.ON_BICYCLE:
                        case DetectedActivity.ON_FOOT:
                            mBinder.startScanning();
                            break;

                        case DetectedActivity.TILTING:
                            // Tilting is a hint that the user is changing activity.
                            break;

                        case DetectedActivity.STILL:
                            mBinder.stopScanning();
                            break;

                        case DetectedActivity.UNKNOWN:
                            break;

                        default:
                            Log.e(LOGTAG, "", new IllegalArgumentException("Unknown activity type: " + activityType));
                            break;
                    }
                } catch (RemoteException e) {
                    Log.e(LOGTAG, "", e);
                }

                if (mDetectedActivity != activityType) {
                    mDetectedActivity = activityType;

                    // Update UI
                    Intent i = new Intent(ScannerService.MESSAGE_TOPIC);
                    i.putExtra(Intent.EXTRA_SUBJECT, "Scanner");
                    sendBroadcast(i);
                }
            }
        };
        registerReceiver(mActivityRecognitionReceiver,
                         new IntentFilter("receive_recognition"));

        Prefs prefs = new Prefs(this);
        mReporter = new Reporter(this, prefs);
        mScanner = new Scanner(this);
        mLooper = new LooperThread();
        mLooper.start();

        startActivityTracking();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(LOGTAG, "onDestroy");

        unregisterReceiver(mBatteryLowReceiver);
        mBatteryLowReceiver = null;

        unregisterReceiver(mActivityRecognitionReceiver);
        mActivityRecognitionReceiver = null;

        mLooper.interrupt();
        mLooper = null;
        mScanner = null;

        mReporter.shutdown();
        mReporter = null; 

        stopActivityTracking();
        cancelNotification();
    }

    private void postNotification(final CharSequence title, final CharSequence text) {
        mLooper.post(new Runnable() {
            @Override
            public void run() {
                Context ctx = getApplicationContext();
                Intent notificationIntent = new Intent(ctx, MainActivity.class);
                notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_FROM_BACKGROUND);
                PendingIntent contentIntent = PendingIntent.getActivity(ctx, NOTIFICATION_ID, notificationIntent,
                        PendingIntent.FLAG_CANCEL_CURRENT);
                int icon = R.drawable.ic_status_scanning;
                Notification n = buildNotification(ctx, icon, title, text, contentIntent);
                startForeground(NOTIFICATION_ID, n);
            }
        });
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
        Log.d(LOGTAG, "onBind");
        return mBinder;
    }

    private static Notification buildNotification(Context context, int icon,
                                                  CharSequence contentTitle,
                                                  CharSequence contentText,
                                                  PendingIntent contentIntent) {
        return new NotificationCompat.Builder(context)
                .setSmallIcon(icon)
                .setContentTitle(contentTitle)
                .setContentText(contentText)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build();
    }
}
