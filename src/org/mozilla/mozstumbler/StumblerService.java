package org.mozilla.mozstumbler;

import org.mozilla.mozstumbler.sync.SyncUtils;

import android.app.AlarmManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.util.Calendar;

public final class StumblerService extends Service {
    public static final String  MESSAGE_TOPIC   = "org.mozilla.mozstumbler.serviceMessage"; // FIXME remove this

    private static final String LOGTAG          = StumblerService.class.getName();
    private Scanner             mScanner;
    private Reporter            mReporter;
    private LooperThread        mLooper;
    private boolean             mIsBound;
    private final IBinder       mBinder         = new StumblerBinder();

    public final class StumblerBinder extends Binder {
        StumblerService getService() {
            return StumblerService.this;
        }
    }

    public boolean isScanning() {
        return mScanner.isScanning();
    }

    public void startScanning() {
        if (mScanner.isScanning()) {
            return;
        }

        mLooper.post(new Runnable() {
            @Override
            public void run() {
                try {
                    mScanner.startScanning();
                } catch (Exception e) {
                    Log.d(LOGTAG, "looper shat itself : " + e);
                }
            }
        });
    }

    public void stopScanning() {
        if (mScanner.isScanning()) {
            mLooper.post(new Runnable() {
                @Override
                public void run() {
                    mScanner.stopScanning();
                    mReporter.flush();
                    SyncUtils.TriggerRefresh(false);
                }
            });
        }
    }

    public void checkPrefs() {
        mScanner.checkPrefs();
    }

    public int getLocationCount() {
        return mScanner.getLocationCount();
    }

    public double getLatitude() {
        return mScanner.getLatitude();
    }

    public double getLongitude() {
        return mScanner.getLongitude();
    }

    public int getWifiStatus() {
        return mScanner.getWifiStatus();
    }

    public int getAPCount() {
        return mScanner.getAPCount();
    }

    public int getVisibleAPCount() {
        return mScanner.getVisibleAPCount();
    }

    public int getCellInfoCount() {
        return mScanner.getCellInfoCount();
    }

    public int getCurrentCellInfoCount() {
        return mScanner.getCurrentCellInfoCount();
    }

    public boolean isGeofenced () {
        return mScanner.isGeofenced();
    }


    private final class LooperThread extends HandlerThread {
        private Handler mHandler;

        public LooperThread() {
            super("StumblerService");
        }

        public synchronized void waitUntilReady() {
            mHandler = new Handler(getLooper());
        }

        void post(Runnable runnable) {
            mHandler.post(runnable);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(LOGTAG, "onCreate");

        mScanner = new Scanner(this);
        mLooper = new LooperThread();
        mLooper.start();
        mLooper.waitUntilReady();
        mReporter = new Reporter(this, mLooper.getLooper());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(LOGTAG, "onDestroy");

        // Quit the handler thread's looper safely.
        try {
            mLooper.post(new Runnable() {
                @Override
                public void run() {
                    mReporter.shutdown();
                    mLooper.quit();
                }
            });
            Looper looper = mLooper.getLooper();
            if (looper != null) {
                looper.getThread().join(5000);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        mReporter = null;
        mLooper = null;
        mScanner = null;
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
