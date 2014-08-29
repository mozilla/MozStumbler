package org.mozilla.mozstumbler.client;

import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.stumblerthread.StumblerService;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.DataStorageManager;

// Used as a bound service (with foreground priority) in MozStumbler, a.k.a. active scanning mode.
// -- In accordance with Android service docs -and experimental findings- this puts the service as low
//    as possible on the Android process kill list.
// -- Binding functions are commented in this class as being unused in the stand-alone service mode.
public class ClientStumblerService extends StumblerService {
    private static final String LOG_TAG = AppGlobals.LOG_PREFIX + StumblerService.class.getSimpleName();
    private final IBinder mBinder = new StumblerBinder();

    // Service binding is not used in stand-alone passive mode.
    public final class StumblerBinder extends Binder {
        // Only to be used in the non-standalone, non-passive case (MozStumbler). In the passive standalone usage
        // of this class, everything, including initialization, is done on its dedicated thread
        // This function is written to enforce the contract of its usage, and will throw if called from the wrong thread
        public ClientStumblerService getServiceAndInitialize(Thread callingThread,
                                                             long maxBytesOnDisk,
                                                             int maxWeeksOld) {
            if (Looper.getMainLooper().getThread() != callingThread) {
                throw new RuntimeException("Only call from main thread");
            }
            DataStorageManager.createGlobalInstance(ClientStumblerService.this,
                    ClientStumblerService.this, maxBytesOnDisk, maxWeeksOld);
            init();
            return ClientStumblerService.this;
        }

        public ClientStumblerService getService() {
            return ClientStumblerService.this;
        }
    }

    // Service binding is not used in stand-alone passive mode.
    @Override
    public IBinder onBind(Intent intent) {
        if (AppGlobals.isDebug) {
            Log.d(LOG_TAG, "onBind");
        }
        return mBinder;
    }

    // Service binding is not used in stand-alone passive mode.
    @Override
    public boolean onUnbind(Intent intent) {
        if (AppGlobals.isDebug) {
            Log.d(LOG_TAG, "onUnbind");
        }
        return true;
    }

    // Service binding is not used in stand-alone passive mode.
    @Override
    public void onRebind(Intent intent) {
        if (AppGlobals.isDebug) {
            Log.d(LOG_TAG,"onRebind");
        }
    }

    public void stopScanning() {
        if (mScanManager.stopScanning()) {
            mReporter.flush();
        }
    }
}

