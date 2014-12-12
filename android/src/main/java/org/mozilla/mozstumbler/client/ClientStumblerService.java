/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client;

import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.stumblerthread.StumblerService;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.DataStorageManager;
import org.mozilla.mozstumbler.service.utils.BatteryCheckReceiver;
import org.mozilla.mozstumbler.service.utils.BatteryCheckReceiver.BatteryCheckCallback;

// Used as a bound service (with foreground priority) in Mozilla Stumbler, a.k.a. active scanning mode.
// -- In accordance with Android service docs -and experimental findings- this puts the service as low
//    as possible on the Android process kill list.
// -- Binding functions are commented in this class as being unused in the stand-alone service mode.
public class ClientStumblerService extends StumblerService {
    private static final String LOG_TAG = AppGlobals.makeLogTag(StumblerService.class.getSimpleName());
    private final IBinder mBinder = new StumblerBinder();
    private BatteryCheckReceiver mBatteryChecker;

    private final BatteryCheckCallback mBatteryCheckCallback = new BatteryCheckCallback() {
        private boolean waitForBatteryOkBeforeSendingNotification;
        @Override
        public void batteryCheckCallback(BatteryCheckReceiver receiver) {
            int minBattery = ClientPrefs.getInstance(ClientStumblerService.this).getMinBatteryPercent();
            boolean isLow = receiver.isBatteryNotChargingAndLessThan(minBattery);
            if (isLow && !waitForBatteryOkBeforeSendingNotification) {
                waitForBatteryOkBeforeSendingNotification = true;
                LocalBroadcastManager.getInstance(ClientStumblerService.this).
                        sendBroadcast(new Intent(MainApp.ACTION_LOW_BATTERY));
            } else if (receiver.isBatteryNotChargingAndGreaterThan(minBattery)) {
                waitForBatteryOkBeforeSendingNotification = false;
            }
        }
    };

    // Service binding is not used in stand-alone passive mode.
    public final class StumblerBinder extends Binder {
        // Only to be used in the non-standalone, non-passive case (Mozilla Stumbler). In the passive standalone usage
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

        if (mBatteryChecker != null) {
            mBatteryChecker.stop();
        }
    }

    @Override
    public synchronized void startScanning() {
        super.startScanning();

        if (mBatteryChecker == null) {
            mBatteryChecker = new BatteryCheckReceiver(this, mBatteryCheckCallback);
        }

        mBatteryChecker.start();
    }
}

