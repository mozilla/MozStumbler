package org.mozilla.mozstumbler;

import java.util.Calendar;

import org.mozilla.mozstumbler.preferences.Prefs;
import org.mozilla.mozstumbler.ActivityRecognitionIntentService;

import android.annotation.TargetApi;
import android.app.AlarmManager;
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
                int activityType = intent.getIntExtra("activity_type", 0);
                int confidence = intent.getIntExtra("confidence", -1);
                long time = intent.getLongExtra("time", 0);

                if (confidence < 50) {
                    return;
                }

                boolean active =
                  activityType == DetectedActivity.IN_VEHICLE ||
                  activityType == DetectedActivity.ON_BICYCLE ||
                  activityType == DetectedActivity.ON_FOOT;

                try {
                    if (active) {
                        mBinder.startScanning();
                    } else {
                        mBinder.stopScanning();
                    }
                } catch (RemoteException e) {
                    Log.e(LOGTAG, "", e);
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
}
