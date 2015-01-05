/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread.scanners;

import android.content.Context;
import android.content.Intent;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.AppGlobals.ActiveOrPassiveStumbling;
import org.mozilla.mozstumbler.service.utils.TelemetryWrapper;

public class GPSScanner implements LocationListener {
    public static final String ACTION_BASE = AppGlobals.ACTION_NAMESPACE + ".GPSScanner.";
    public static final String ACTION_GPS_UPDATED = ACTION_BASE + "GPS_UPDATED";
    public static final String ACTION_ARG_TIME = AppGlobals.ACTION_ARG_TIME;
    public static final String SUBJECT_LOCATION_LOST = "location_lost";
    public static final String SUBJECT_NEW_LOCATION = "new_location";
    public static final String NEW_LOCATION_ARG_LOCATION = "location";
    public static final int MIN_SAT_USED_IN_FIX = 3;

    private static final String LOG_TAG = AppGlobals.makeLogTag(GPSScanner.class.getSimpleName());
    private static final long ACTIVE_MODE_GPS_MIN_UPDATE_TIME_MS = 2000;
    private static final float ACTIVE_MODE_GPS_MIN_UPDATE_DISTANCE_M = 30;
    private static final long PASSIVE_GPS_MIN_UPDATE_FREQ_MS = 3000;
    private static final float PASSIVE_GPS_MOVEMENT_MIN_DELTA_M = 30;
    private long mTelemetry_lastStartedMs;

    private final StumblerFilter stumbleFilter = new StumblerFilter();
    private final Context mContext;
    private GpsStatus.Listener mGPSListener;
    private int mLocationCount;
    private Location mLocation = new Location("internal");
    private boolean mIsPassiveMode;
    private final ScanManager mScanManager;

    public GPSScanner(Context context, ScanManager scanManager) {
        mContext = context;
        mScanManager = scanManager;
    }

    public void start(final ActiveOrPassiveStumbling stumblingMode) {
        mIsPassiveMode = (stumblingMode == ActiveOrPassiveStumbling.PASSIVE_STUMBLING);
        if (mIsPassiveMode ) {
            startPassiveMode();
        } else {
            startActiveMode();
        }
    }

    private boolean isGpsAvailable(LocationManager locationManager) {
        if (locationManager == null ||
            locationManager.getProvider(LocationManager.GPS_PROVIDER) == null) {
            String msg = "No GPS available, scanning not started.";
            Log.d(LOG_TAG, msg);
            AppGlobals.guiLogError(msg);
            return false;
        }
        return true;
    }

    private void startPassiveMode() {
        LocationManager locationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
        if (!isGpsAvailable(locationManager)) {
            return;
        }
        locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 0, 0, this);

        final int timeDiffSec = Long.valueOf((System.currentTimeMillis() - mTelemetry_lastStartedMs) / 1000).intValue();
        if (mTelemetry_lastStartedMs > 0 && timeDiffSec > 0) {
            TelemetryWrapper.addToHistogram(AppGlobals.TELEMETRY_TIME_BETWEEN_STARTS_SEC, timeDiffSec);
        }
        mTelemetry_lastStartedMs = System.currentTimeMillis();

    }

    private void startActiveMode() {
        LocationManager lm = getLocationManager();
        if (!isGpsAvailable(lm)) {
            return;
        }

        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                ACTIVE_MODE_GPS_MIN_UPDATE_TIME_MS,
                ACTIVE_MODE_GPS_MIN_UPDATE_DISTANCE_M,
                this);

        reportLocationLost();

        mGPSListener = new GpsStatus.Listener() {
                public void onGpsStatusChanged(int event) {
                if (event == GpsStatus.GPS_EVENT_SATELLITE_STATUS) {
                    GpsStatus status = getLocationManager().getGpsStatus(null);
                    Iterable<GpsSatellite> sats = status.getSatellites();

                    int satellites = 0;
                    int fixes = 0;

                    for (GpsSatellite sat : sats) {
                        satellites++;
                        if (sat.usedInFix()) {
                            fixes++;
                        }
                    }

                    if (fixes < MIN_SAT_USED_IN_FIX) {
                        reportLocationLost();
                    }
                } else if (event == GpsStatus.GPS_EVENT_STOPPED) {
                    reportLocationLost();
                }
            }
        };

        lm.addGpsStatusListener(mGPSListener);
    }

    public void stop() {
        LocationManager lm = getLocationManager();
        lm.removeUpdates(this);
        reportLocationLost();

        if (mGPSListener != null) {
          lm.removeGpsStatusListener(mGPSListener);
          mGPSListener = null;
        }
    }

    public int getLocationCount() {
        return mLocationCount;
    }

    public double getLatitude() {
        return mLocation.getLatitude();
    }

    public double getLongitude() {
        return mLocation.getLongitude();
    }

    public Location getLocation() {
        return mLocation;
    }

    private void sendToLogActivity(String msg) {
        AppGlobals.guiLogInfo(msg, "#33ccff", false, false);
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location == null) { // TODO: is this even possible??
            reportLocationLost();
            return;
        }

        String logMsg = (mIsPassiveMode)? "[Passive] " : "[Active] ";

        String provider = location.getProvider();
        if (!provider.toLowerCase().contains("gps")) {
            sendToLogActivity(logMsg + "Discard fused/network location.");
            // only interested in GPS locations
            return;
        }

        final long timeDeltaMs = location.getTime() - mLocation.getTime();

        // Seem to get greater likelihood of non-fused location with higher update freq.
        // Check dist and time threshold here, not set on the listener.
        if (mIsPassiveMode) {
            final boolean hasMoved = location.distanceTo(mLocation) > PASSIVE_GPS_MOVEMENT_MIN_DELTA_M;

            if (timeDeltaMs < PASSIVE_GPS_MIN_UPDATE_FREQ_MS || !hasMoved) {
                return;
            }
        }

        logMsg += location.toString();
        sendToLogActivity(logMsg);

        if (stumbleFilter.blockLocation(location)) {
            Log.w(LOG_TAG, "Blocked location: " + location);
            reportLocationLost();
            return;
        }

        if (AppGlobals.isDebug) {
            Log.d(LOG_TAG, "New location: " + location);
        }

        mLocation = location;

        reportNewLocationReceived(location);
        mLocationCount++;

        if (mIsPassiveMode) {
            mScanManager.newPassiveGpsLocation();
        }

        if (timeDeltaMs > 0) {
            TelemetryWrapper.addToHistogram(AppGlobals.TELEMETRY_TIME_BETWEEN_RECEIVED_LOCATIONS_SEC,
                Long.valueOf(timeDeltaMs).intValue() / 1000);
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider)) {
            reportLocationLost();
        }
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        if ((status != LocationProvider.AVAILABLE) &&
            (LocationManager.GPS_PROVIDER.equals(provider))) {
            reportLocationLost();
        }
    }

    private LocationManager getLocationManager() {
        return (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
    }

    private void reportNewLocationReceived(Location location) {
        Intent i = new Intent(ACTION_GPS_UPDATED);
        i.putExtra(Intent.EXTRA_SUBJECT, SUBJECT_NEW_LOCATION);
        i.putExtra(NEW_LOCATION_ARG_LOCATION, location);
        i.putExtra(ACTION_ARG_TIME, System.currentTimeMillis());
        LocalBroadcastManager.getInstance(mContext).sendBroadcastSync(i);
    }

    private void reportLocationLost() {
        Intent i = new Intent(ACTION_GPS_UPDATED);
        i.putExtra(Intent.EXTRA_SUBJECT, SUBJECT_LOCATION_LOST);
        i.putExtra(ACTION_ARG_TIME, System.currentTimeMillis());
        LocalBroadcastManager.getInstance(mContext).sendBroadcastSync(i);
    }

}
