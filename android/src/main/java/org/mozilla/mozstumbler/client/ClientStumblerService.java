/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;

import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.util.NotificationUtil;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.stumblerthread.Reporter;
import org.mozilla.mozstumbler.service.stumblerthread.StumblerService;
import org.mozilla.mozstumbler.service.utils.BatteryCheckReceiver;
import org.mozilla.mozstumbler.service.utils.BatteryCheckReceiver.BatteryCheckCallback;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.log.ILogger;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

// Used as a bound service (with foreground priority) in Mozilla Stumbler, a.k.a. active scanning mode.
// -- In accordance with Android service docs -and experimental findings- this puts the service as low
//    as possible on the Android process kill list.
// -- Binding functions are commented in this class as being unused in the stand-alone service mode.
public class ClientStumblerService extends StumblerService {
    private static final String LOG_TAG = LoggerUtil.makeLogTag(StumblerService.class);
    private static final ILogger Log = (ILogger) ServiceLocator.getInstance().getService(ILogger.class);

    public static enum RequestChangeScannerState {
        START, STOP;

        public static final String NAMESPACE = "org.mozilla.mozstumbler.clientstumblerservice.state";

        @Override
        public String toString() {
            return NAMESPACE+ this.name();
        }

        public static RequestChangeScannerState fromString(String name)  {
            try {
                return RequestChangeScannerState.valueOf(name.substring(RequestChangeScannerState.NAMESPACE.length()));
            } catch (IllegalArgumentException iae) {
                return null;
            }
        }
    }

    private static final String START_FOREGROUND_SCANNING = RequestChangeScannerState.START.toString();
    private static final String STOP_FOREGROUND_SCANNING = RequestChangeScannerState.STOP.toString();

    private final IBinder mBinder = new StumblerBinder();
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
    private BatteryCheckReceiver mBatteryChecker;

    private final BroadcastReceiver startStopScanReceiver = new BroadcastReceiver() {
        // This captures state change from the ScanManager
        @Override
        public void onReceive(Context context, Intent intent) {

            if (intent == null) {
                return;
            }
            RequestChangeScannerState newRunState = RequestChangeScannerState.fromString(intent.getAction());

            if (newRunState != null) {
                if (newRunState.equals(RequestChangeScannerState.START)) {
                    startScanning();
                } else if (newRunState.equals(RequestChangeScannerState.STOP)) {
                    stopScanning();
                }
            }
        };
    };


    // Service binding is not used in stand-alone passive mode.
    @Override
    public IBinder onBind(Intent intent) {
        if (AppGlobals.isDebug) {
            Log.d(LOG_TAG, "onBind");
        }

        registerIntentFilters();
        return mBinder;
    }

    private void registerIntentFilters() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(START_FOREGROUND_SCANNING);
        intentFilter.addAction(STOP_FOREGROUND_SCANNING);
        LocalBroadcastManager
                .getInstance(getApplicationContext())
                .registerReceiver(startStopScanReceiver,
                        intentFilter);
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
            Log.d(LOG_TAG, "onRebind");
        }
    }

    public void stopScanning() {
        if (mScanManager.stopScanning()) {
            Intent flush = new Intent(Reporter.ACTION_FLUSH_TO_BUNDLE);
            LocalBroadcastManager.getInstance(this).sendBroadcastSync(flush);
        }

        if (mBatteryChecker != null) {
            mBatteryChecker.stop();
        }

        stopForeground(true);
    }

    @Override
    public synchronized void startScanning() {
        foregroundNotification();

        boolean passiveScanning = ClientPrefs.getInstance(ClientStumblerService.this).isScanningPassive();
        setPassiveMode(passiveScanning);

        super.startScanning();

        if (mBatteryChecker == null) {
            mBatteryChecker = new BatteryCheckReceiver(this, mBatteryCheckCallback);
        }

        mBatteryChecker.start();
    }

    private void foregroundNotification() {
        NotificationUtil nm = new NotificationUtil(this.getApplicationContext());
        Notification notification = nm.buildNotification(getString(R.string.stop_scanning));
        startForeground(NotificationUtil.NOTIFICATION_ID, notification);
    }

    public static void startForegroundScanning(Context ctx) {
        Intent startIntent = new Intent(ctx, ClientStumblerService.class);
        startIntent.setAction(START_FOREGROUND_SCANNING);

        // TODO: change this eventually to use startService and adjust the BroadcastReceiver
        // code and move it into an onHandleIntent block
        LocalBroadcastManager.getInstance(ctx).sendBroadcastSync(startIntent);
    }

    public static void stopForegroundScanning(Context ctx) {
        Intent startIntent = new Intent(ctx, ClientStumblerService.class);
        startIntent.setAction(STOP_FOREGROUND_SCANNING);

        // TODO: change this eventually to use stopService
        LocalBroadcastManager.getInstance(ctx).sendBroadcastSync(startIntent);
    }

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
            ClientDataStorageManager.createGlobalInstance(ClientStumblerService.this,
                    ClientStumblerService.this, maxBytesOnDisk, maxWeeksOld);
            init();
            return ClientStumblerService.this;
        }

        public ClientStumblerService getService() {
            return ClientStumblerService.this;
        }
    }
}

