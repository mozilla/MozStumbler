package org.mozilla.mozstumbler.service.stumblerthread.motiondetection;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.SensorManager;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.Toast;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.core.logging.ClientLog;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.GPSScanner;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.WifiScanner;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MotionSensor {

    public static final String ACTION_USER_MOTION_DETECTED = AppGlobals.ACTION_NAMESPACE + ".USER_MOVE";
    private static final String LOG_TAG = LoggerUtil.makeLogTag(MotionSensor.class);
    /// Testing code
    private final SensorManager mSensorManager;
    private final Context mAppContext;
    private IMotionSensor motionSensor;
    private static MotionSensor sDebugInstance;

    private List<String> mWifiListWhenStarted = new ArrayList<String>();

WifiScanner mScanner;

    public MotionSensor(Context appCtx) {
        mAppContext = appCtx;
        mQuickCheck = new QuickCheckGPSLocationChanged(mAppContext);

        mSensorManager = (SensorManager) mAppContext.getSystemService(Context.SENSOR_SERVICE);
        if (mSensorManager == null) {
            AppGlobals.guiLogInfo("No sensor manager.");
            return;
        }

        sDebugInstance = this;

        motionSensor = SignificantMotionSensor.getSensor(mAppContext);

        // If no TYPE_SIGNIFICANT_MOTION is available, use alternate means to sense motion
        if (motionSensor == null) {
            motionSensor = new LegacyMotionSensor(mAppContext);
            AppGlobals.guiLogInfo("Device has legacy motion sensor.");
        }

    }

    public static void debugMotionDetected() {
        if (!sDebugInstance.motionSensor.isActive()) {
            return;
        }
        AppGlobals.guiLogInfo("TEST: Major motion detected.");
        LocalBroadcastManager.getInstance(sDebugInstance.mAppContext).sendBroadcastSync(new Intent(ACTION_USER_MOTION_DETECTED));
    }

    public static boolean hasSignificantMotionSensor(Context appCtx) {
        return SignificantMotionSensor.getSensor(appCtx) != null;
    }

    void unregister() {
        try {
            LocalBroadcastManager.getInstance(mAppContext).unregisterReceiver(mGetWifisOnStart);
        } catch (Exception ex) {}
        try {
            LocalBroadcastManager.getInstance(mAppContext).unregisterReceiver(mCompareWifisOnStop);
        } catch (Exception ex) {}
    }

    BroadcastReceiver mGetWifisOnStart = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            mWifiListWhenStarted = new ArrayList<String>();
            List<ScanResult> results = intent.getParcelableArrayListExtra(WifiScanner.ACTION_WIFIS_SCANNED_ARG_RESULTS);
            for (ScanResult r : results){
                mWifiListWhenStarted.add(r.BSSID);
            }
            mScanner.stop();
            unregister();
        }
    };

    BroadcastReceiver mCompareWifisOnStop = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            List<ScanResult> results = intent.getParcelableArrayListExtra(WifiScanner.ACTION_WIFIS_SCANNED_ARG_RESULTS);
            int common = 0;
            for (ScanResult r : results) {
                if (mWifiListWhenStarted.contains(r.BSSID)) {
                    common++;
                }
            }
            double kPercentMatch = 0.5;
            // if more than 50% match, then we assume the user hasn't moved
            if (common >= (Math.max(mWifiListWhenStarted.size(), results.size()) * kPercentMatch)) {
                Intent sendIntent = new Intent(LocationChangeSensor.ACTION_LOCATION_NOT_CHANGING);
                LocalBroadcastManager.getInstance(mAppContext).sendBroadcastSync(sendIntent);
                Toast.makeText(mAppContext, "same wifi", Toast.LENGTH_SHORT).show();
                AppGlobals.guiLogInfo("Same wifi (" + common + "," + mWifiListWhenStarted.size() + "," +
                        results.size() + ")", "green", true, false);
            }
            else {
                AppGlobals.guiLogInfo("Different wifi", "green", true, false);
                AppGlobals.guiLogInfo("info (" + common + "," + mWifiListWhenStarted.size() + "," +
                        results.size() + ")", "green", true, false);

               mQuickCheck.start();
            }
            // unregister and stop the scanner
            mScanner.stop();
            unregister();
        }
    };

    public void start() {
        if (motionSensor.isActive()) {
            return;
        }
        motionSensor.start();

        if (mScanner == null) {
            mScanner = new WifiScanner(mAppContext);
        }
        unregister();
        handler.removeCallbacks(runnable);
        LocalBroadcastManager.getInstance(mAppContext).registerReceiver(mGetWifisOnStart,
                new IntentFilter(WifiScanner.ACTION_WIFIS_SCANNED));
        mScanner.start(AppGlobals.ActiveOrPassiveStumbling.ACTIVE_STUMBLING);

        mQuickCheck.updateLocation();
    }

    Handler handler = new Handler();
    Runnable runnable = new Runnable() {
        public void run() {
            if (mScanner != null) {
                mScanner.stop();
            }
            unregister();
        }
    };

    public void stop() {
        if (!motionSensor.isActive()) {
            return;
        }

        motionSensor.stop();

        if (mScanner != null) {
            LocalBroadcastManager.getInstance(mAppContext).registerReceiver(mCompareWifisOnStop,
                    new IntentFilter(WifiScanner.ACTION_WIFIS_SCANNED));
            mScanner.start(AppGlobals.ActiveOrPassiveStumbling.ACTIVE_STUMBLING);
        }

        final long kWaitForWifiResultsMs = 20 * 1000;
        handler.removeCallbacks(runnable);
        handler.postDelayed(runnable, kWaitForWifiResultsMs);
    }

    QuickCheckGPSLocationChanged mQuickCheck;
    private static class QuickCheckGPSLocationChanged {
        final Context mContext;
        final Handler handler = new Handler();
        private Location mLastLocation;
        private final long mMinMotionChangeDistanceMeters = 30;

        LocationListener mListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                stop();
                if (mLastLocation == null) {
                    return;
                }
                double dist = mLastLocation.distanceTo(location);
                if (dist < mMinMotionChangeDistanceMeters) {
                    Intent sendIntent = new Intent(LocationChangeSensor.ACTION_LOCATION_NOT_CHANGING);
                    LocalBroadcastManager.getInstance(mContext).sendBroadcastSync(sendIntent);
                    Toast.makeText(mContext, "not moved", Toast.LENGTH_SHORT).show();
                    AppGlobals.guiLogInfo("not moved", "green", true, false);
                }
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {}
            public void onProviderEnabled(String provider) {}
            public void onProviderDisabled(String provider) {}
        };

        public QuickCheckGPSLocationChanged(Context mAppContext) {
            mContext = mAppContext;
        }

        void updateLocation() {
            stop();

            LocationManager lm = (LocationManager)mContext.getSystemService(Context.LOCATION_SERVICE);
            mLastLocation = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        }

        void start() {
            stop();

            if (mLastLocation == null) {
                return;
            }
            LocationManager lm = (LocationManager)mContext.getSystemService(Context.LOCATION_SERVICE);
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mListener);

            handler.postDelayed(new Runnable() {
                public void run() {
                    stop();
                }
            }, 20 * 1000);
        }

        void stop() {
            LocationManager lm = (LocationManager)mContext.getSystemService(Context.LOCATION_SERVICE);
            lm.removeUpdates(mListener);
            handler.removeCallbacks(null);
        }
    }

}
