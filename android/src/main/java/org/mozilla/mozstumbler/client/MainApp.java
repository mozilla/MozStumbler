/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.StrictMode;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.acra.ACRA;
import org.acra.annotation.ReportsCrashes;
import org.acra.sender.HttpSender;
import org.mozilla.mozstumbler.BuildConfig;
import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.subactivities.DeveloperActivity;
import org.mozilla.mozstumbler.client.subactivities.LogActivity;
import org.mozilla.mozstumbler.client.util.NotificationUtil;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.core.http.IHttpUtil;
import org.mozilla.mozstumbler.service.core.http.ILocationService;
import org.mozilla.mozstumbler.service.core.logging.MockAcraLog;
import org.mozilla.mozstumbler.service.stumblerthread.Reporter;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.DataStorageConstants;
import org.mozilla.mozstumbler.service.stumblerthread.motiondetection.MotionSensor;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.ISimulatorService;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.ScanManager;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.WifiScanner;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.cellscanner.CellScanner;
import org.mozilla.mozstumbler.service.uploadthread.AsyncUploadParam;
import org.mozilla.mozstumbler.service.uploadthread.AsyncUploader;
import org.mozilla.mozstumbler.service.utils.NetworkInfo;
import org.mozilla.mozstumbler.svclocator.ServiceConfig;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.ISystemClock;
import org.mozilla.mozstumbler.svclocator.services.log.ILogger;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;
import org.mozilla.osmdroid.tileprovider.constants.TileFilePath;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mozilla.mozstumbler.service.stumblerthread.scanners.ScanManager.*;

@ReportsCrashes(
        formKey = "",
        httpMethod = HttpSender.Method.PUT,
        reportType = HttpSender.Type.JSON,
        formUri = BuildConfig.ACRA_URI,
        formUriBasicAuthLogin = BuildConfig.ACRA_USER,
        formUriBasicAuthPassword = BuildConfig.ACRA_PASS)
public class MainApp extends Application
        implements AsyncUploader.AsyncUploaderListener {
    public static final AtomicBoolean isUploading = new AtomicBoolean();

    public static final String NOTIFICATION_STOP = "org.mozilla.mozstumbler.notification.stop";

    public static final String ACTION_BASE = AppGlobals.ACTION_NAMESPACE + ".MainApp.";
    public static final String ACTION_LOW_BATTERY = ACTION_BASE + ".LOW_BATTERY";
    private static boolean sHasBootedOnce;
    private final String LOG_TAG = LoggerUtil.makeLogTag(MainApp.class);
    private ClientStumblerService mStumblerService;
    private ServiceConnection mConnection;
    private ServiceBroadcastReceiver mReceiver;
    private WeakReference<IMainActivity> mMainActivity = new WeakReference<IMainActivity>(null);
    private boolean mIsScanningPausedDueToNoMotion;

    // These track the state of the currently running service
    private ScanManager.ScannerState scannerState = ScannerState.STOPPED;


    boolean isStumblerStopped() {
        return scannerState == ScannerState.STOPPED;
    }

    private final BroadcastReceiver scannerStateReceiver = new BroadcastReceiver() {
        // This captures state change from the ScanManager
        @Override
        public void onReceive(Context context, Intent intent) {

            if (intent == null) {
                return;
            }
            ScanManager.ScannerState tmpScannerState = ScanManager.ScannerState.fromString(intent.getAction());

            if (tmpScannerState != null) {
                scannerState = tmpScannerState;
            }

            Log.i(LOG_TAG, "Received and updated MainApp scannerState = " + scannerState);
        };
    };


    private final BroadcastReceiver mReceivePausedOrUnpausedState = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            mIsScanningPausedDueToNoMotion =
                intent.getAction().equals(ACTION_SCAN_PAUSED_USER_MOTIONLESS);

            new Handler(context.getMainLooper()).post(new Runnable() {
                public void run() {
                    updateMotionDetected();
                }
            });
        }
    };

    public static ServiceConfig defaultServiceConfig() {
        /*
         This will configure the service map with all services required for runtime.

         Note that the logger checks the buildconfig type to determine whether or not to use the DebugLogger
         or the ProductionLogger.
         */

        ServiceConfig result = new ServiceConfig();
        // All classes here must have an argument free constructor.
        result.put(IHttpUtil.class,
                ServiceConfig.load("org.mozilla.mozstumbler.service.core.http.HttpUtil"));
        result.put(ISystemClock.class,
                ServiceConfig.load("org.mozilla.mozstumbler.svclocator.services.SystemClock"));
        result.put(ILocationService.class,
                ServiceConfig.load("org.mozilla.mozstumbler.service.core.http.MLS"));
        result.put(ISimulatorService.class,
                ServiceConfig.load("org.mozilla.mozstumbler.service.stumblerthread.scanners.SimulatorService"));

        if (BuildConfig.BUILD_TYPE.equals("unittest")) {
            result.put(ILogger.class, ServiceConfig.load("org.mozilla.mozstumbler.svclocator.services.log.UnittestLogger"));
        } else if (BuildConfig.BUILD_TYPE.equals("debug")) {
            result.put(ILogger.class, ServiceConfig.load("org.mozilla.mozstumbler.svclocator.services.log.DebugLogger"));
        } else {
            result.put(ILogger.class, ServiceConfig.load("org.mozilla.mozstumbler.svclocator.services.log.ProductionLogger"));
        }

        return result;
    }

    public static boolean getAndSetHasBootedOnce() {
        boolean b = sHasBootedOnce;
        sHasBootedOnce = true;
        return b;
    }

    public ClientPrefs getPrefs(Context c) {
        return ClientPrefs.getInstance(c);
    }

    public void setMainActivity(IMainActivity mainActivity) {
        mMainActivity = new WeakReference<IMainActivity>(mainActivity);
    }

    private File getCacheDir(Context c) {
        File dir = c.getExternalCacheDir();

        if (dir == null) {
            dir = c.getCacheDir();
        }

        if (dir == null) {
            Log.i(LOG_TAG, "No tile storage available");
            return null;
        }

        return new File(dir, "osmdroid");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // The following line triggers the initialization of ACRA
        ACRA.init(this);

        // Bootstrap the service locator with the default list of services
        ServiceLocator.newRoot(defaultServiceConfig());

        // This must be called after init to get a copy of the
        // original ACRA.log instance so that we can toggle the logger on
        // and off.
        MockAcraLog.setOriginalLog();

        TileFilePath.directoryOverride = getCacheDir(getApplicationContext());

        AppGlobals.isDebug = BuildConfig.DEBUG;
        AppGlobals.isRobolectric = BuildConfig.ROBOLECTRIC;
        AppGlobals.appVersionName = BuildConfig.VERSION_NAME;
        AppGlobals.appVersionCode = BuildConfig.VERSION_CODE;
        AppGlobals.appName = this.getResources().getString(R.string.app_name);

        AppGlobals.hasSignificantMotionSensor = MotionSensor.hasSignificantMotionSensor(this.getApplicationContext());

        AsyncUploader.setGlobalUploadListener(this);

        final String OLD_NAME = "org.mozilla.mozstumbler.preferences.Prefs.xml";
        File dir = this.getDir("shared_prefs", Context.MODE_PRIVATE);
        File oldPrefs = new File(dir, OLD_NAME);
        if (oldPrefs.exists()) {
            oldPrefs.renameTo(new File(dir, ClientPrefs.getPrefsFileNameForUpgrade()));
        }

        // Doing this onCreate ensures that a ClientPrefs is instantiated for the whole app.
        // Don't remove this.
        ClientPrefs prefs = ClientPrefs.getInstance(this);

        prefs.setMozApiKey(BuildConfig.MOZILLA_API_KEY);
        String userAgent = System.getProperty("http.agent") + " " +
                AppGlobals.appName + "/" + AppGlobals.appVersionName;
        prefs.setUserAgent(userAgent);

        if (AppGlobals.isDebug) {
            checkSimulationPermission();
        }

        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.registerReceiver(mReceivePausedOrUnpausedState, new IntentFilter(ACTION_SCAN_UNPAUSED_USER_MOVED));
        lbm.registerReceiver(mReceivePausedOrUnpausedState, new IntentFilter(ACTION_SCAN_PAUSED_USER_MOTIONLESS));

        lbm.registerReceiver(scannerStateReceiver, new IntentFilter(ScannerState.STARTED.toString()));
        lbm.registerReceiver(scannerStateReceiver, new IntentFilter(ScannerState.STARTED_BUT_PAUSED_MOTIONLESS.toString()));
        lbm.registerReceiver(scannerStateReceiver, new IntentFilter(ScannerState.STOPPED.toString()));

        NetworkInfo.createGlobalInstance(this);
        LogActivity.LogMessageReceiver.createGlobalInstance(this);
        // This will create, and register the receiver
        ObservedLocationsReceiver.createGlobalInstance(this.getApplicationContext());

        enableStrictMode();

        mReceiver = new ServiceBroadcastReceiver();
        mReceiver.register();

        // Start the stumbling service last after all the intent listeners have been registered.

        mConnection = new ServiceConnection() {
            public void onServiceConnected(ComponentName className, IBinder binder) {
                // binder can be null, android is terrible.
                if (binder == null) {
                    return;
                }

                ClientStumblerService.StumblerBinder serviceBinder = (ClientStumblerService.StumblerBinder) binder;
                mStumblerService = serviceBinder.getServiceAndInitialize(Thread.currentThread(),
                        DataStorageConstants.CLIENT_MAX_BYTES_DISK_STORAGE, DataStorageConstants.CLIENT_MAX_WEEKS_OLD_STORED);

                Log.d(LOG_TAG, "Service connected");
                if (mMainActivity.get() != null) {
                    mMainActivity.get().updateUiOnMainThread(true);
                }
            }

            public void onServiceDisconnected(ComponentName className) {
                mStumblerService = null;
            }
        };

        Intent intent = new Intent(this, ClientStumblerService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);


    }

    private void checkSimulationPermission() {
        String permission = "android.permission.MOCK_LOCATION";
        Context appContext = this.getApplicationContext();
        int res = appContext.checkCallingOrSelfPermission(permission);
        if (res != PackageManager.PERMISSION_GRANTED) {
            if (ClientPrefs.getInstance(appContext).isSimulateStumble()) {
                ClientPrefs.getInstance(appContext).setSimulateStumble(false);
            }
            Log.i(LOG_TAG, "Simulation disabled as developer option is not enabled.");
        }
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
        Log.d(LOG_TAG, "onTerminate");
    }

    public void startScanning() {
        ClientStumblerService.startForegroundScanning(this.getApplicationContext());

        if (mMainActivity.get() != null) {
            mMainActivity.get().updateUiOnMainThread(false);
            mMainActivity.get().start();
        }
    }

    public void stopScanning() {
        mIsScanningPausedDueToNoMotion = false;

        ClientStumblerService.stopForegroundScanning(this.getApplicationContext());

        if (mMainActivity.get() != null) {
            mMainActivity.get().updateUiOnMainThread(false);
            mMainActivity.get().stop();
        }

        AsyncUploader uploader = new AsyncUploader();
        AsyncUploadParam param = new AsyncUploadParam(
                ClientPrefs.getInstance(this).getUseWifiOnly(),
                Prefs.getInstance(this).getNickname(),
                Prefs.getInstance(this).getEmail());

        uploader.execute(param);
    }

    @TargetApi(9)
    private void enableStrictMode() {
        if (!AppGlobals.isDebug) {
            return;
        }

        if (Build.VERSION.SDK_INT < 9) {
            return;
        }

        if (AppGlobals.isRobolectric) {
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

    public boolean isScanning() {
        return scannerState == ScannerState.STARTED;
    }

    public boolean isScanningOrPaused() {
         return scannerState == ScannerState.STARTED_BUT_PAUSED_MOTIONLESS ||
                 scannerState == ScannerState.STARTED;
    }

    public void showDeveloperDialog(Activity activity) {
        activity.startActivity(new Intent(activity, DeveloperActivity.class));
    }

    @Override
    public void onUploadProgress(boolean isUploading) {
        if (mMainActivity.get() != null) {
            mMainActivity.get().setUploadState(isUploading);
        }
    }

    public void keepScreenOnPrefChanged(boolean isEnabled) {
        if (mMainActivity.get() != null) {
            mMainActivity.get().keepScreenOn(isEnabled);
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();

        if (mStumblerService != null) {
            // TODO: change this to an intent broadcast
            mStumblerService.handleLowMemoryNotification();
        }
    }

    @TargetApi(14)
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);

        if (mStumblerService != null) {
            // TODO: change this to an intent broadcast
            mStumblerService.handleLowMemoryNotification();
        }
    }

    public void updateMotionDetected() {
        AppGlobals.guiLogInfo("Is motionless: " + mIsScanningPausedDueToNoMotion);

        NotificationUtil util = new NotificationUtil(this.getApplicationContext());
        util.setPaused(mIsScanningPausedDueToNoMotion);

        if (mMainActivity.get() != null) {
            mMainActivity.get().isPausedDueToNoMotion(mIsScanningPausedDueToNoMotion);
        }
    }

    public boolean isIsScanningPausedDueToNoMotion() {
        return mIsScanningPausedDueToNoMotion;
    }

    public boolean isStopped() {
        return scannerState == ScannerState.STOPPED;
    }

    private class ServiceBroadcastReceiver extends BroadcastReceiver {
        private boolean mReceiverIsRegistered;

        public void register() {
            if (!mReceiverIsRegistered) {
                mReceiverIsRegistered = true;

                IntentFilter intentFilter = new IntentFilter();
                intentFilter.addAction(WifiScanner.ACTION_WIFIS_SCANNED);
                intentFilter.addAction(CellScanner.ACTION_CELLS_SCANNED);
                intentFilter.addAction(Reporter.ACTION_NEW_BUNDLE);
                intentFilter.addAction(ACTION_SCAN_PAUSED_USER_MOTIONLESS);
                intentFilter.addAction(ACTION_LOW_BATTERY);
                LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(this, intentFilter);

                // This can't be a local broadcast as it comes from notification menu
                getApplicationContext().registerReceiver(this, new IntentFilter(NOTIFICATION_STOP));
            }
        }

        public void unregister() {
            if (mReceiverIsRegistered) {
                LocalBroadcastManager.getInstance(MainApp.this).unregisterReceiver(this);
                mReceiverIsRegistered = false;
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(NOTIFICATION_STOP)) {
                Log.d(LOG_TAG, "NOTIFICATION_STOP");
                stopScanning();
            } else if (action.equals(ACTION_LOW_BATTERY)) {
                AppGlobals.guiLogInfo("Stop, low battery detected.");
                stopScanning();
            }

            if (mMainActivity.get() != null) {
                boolean updateMetrics = action.equals(Reporter.ACTION_NEW_BUNDLE);
                mMainActivity.get().updateUiOnMainThread(updateMetrics);
            }
        }
    }
}
