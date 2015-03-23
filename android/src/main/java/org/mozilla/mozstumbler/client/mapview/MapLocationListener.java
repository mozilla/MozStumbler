/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.mapview;

import android.content.Context;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.widget.Toast;

import org.mozilla.mozstumbler.client.MainApp;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.core.logging.ClientLog;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.GPSScanner;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

import java.lang.ref.WeakReference;

class MapLocationListener {
    private static final String LOG_TAG = LoggerUtil.makeLogTag(MapLocationListener.class);
    private final WeakReference<MapFragment> mMapActivity;
    private final LocationUpdateListener mNetworkLocationListener =
            new LocationUpdateListener(LocationManager.NETWORK_PROVIDER, 1000, null);
    private final ReceivedLocationCallback mReceivedGpsLocation = new ReceivedLocationCallback() {
        @Override
        public void receivedLocation() {
            // Once the gps begins receiving locations, stop the network listener
            enableLocationListener(false, mNetworkLocationListener);
        }
    };
    private final LocationUpdateListener mGpsLocationListener =
            new LocationUpdateListener(LocationManager.GPS_PROVIDER, 5000, mReceivedGpsLocation);
    private final LocationUpdateListener mPassiveLocationListener =
            new LocationUpdateListener(LocationManager.PASSIVE_PROVIDER, 1000, null);
    private LocationManager mLocationManager;
    private final GpsStatus.Listener mSatelliteListener = new GpsStatus.Listener() {
        public void onGpsStatusChanged(int event) {
            if (event == GpsStatus.GPS_EVENT_SATELLITE_STATUS && mLocationManager != null) {
                GpsStatus gpsStatus = mLocationManager.getGpsStatus(null);
                Iterable<GpsSatellite> sats = gpsStatus.getSatellites();
                int satellites = 0;
                int fixes = 0;
                for (GpsSatellite sat : sats) {
                    satellites++;
                    if (sat.usedInFix()) {
                        fixes++;
                    }
                }
                if (mMapActivity.get() != null) {
                    mMapActivity.get().updateGPSInfo(satellites, fixes);
                }
                if (fixes < GPSScanner.MIN_SAT_USED_IN_FIX) {
                    enableLocationListener(true, mNetworkLocationListener);
                }
            }
        }
    };

    MapLocationListener(MapFragment mapFragment) {
        mMapActivity = new WeakReference<MapFragment>(mapFragment);
        Context context = mapFragment.getActivity().getApplicationContext();

        mLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (mLocationManager == null) {
            // Ugly non-localized message, which is fine, the app is not usable on any device that shows this toast.
            Toast.makeText(context, "Error: no LOCATION_SERVICE", Toast.LENGTH_LONG).show();
            return;
        }

        mLocationManager.addGpsStatusListener(mSatelliteListener);

        Location lastLoc = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (lastLoc == null) {
            lastLoc = mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }
        if (lastLoc != null && mMapActivity.get() != null) {
            ClientLog.d(LOG_TAG, "set last known location");
            mMapActivity.get().setUserPositionAt(lastLoc);
        }

        MainApp app = mapFragment.getApplication();
        if (app == null) {
            return;
        }
        if (app.isIsScanningPausedDueToNoMotion()) {
            pauseGpsUpdates(true);
        } else {
            enableLocationListener(true, mNetworkLocationListener);
            enableLocationListener(true, mGpsLocationListener);
        }
        enableLocationListener(true, mPassiveLocationListener);
    }

    private void enableLocationListener(boolean isEnabled, LocationUpdateListener listener) {
        if (mLocationManager == null) {
            return;
        }

        try {
            if (isEnabled && !listener.mIsActive) {
                mLocationManager.requestLocationUpdates(listener.mType, listener.mFreqMs, 0, listener);
            } else if (!isEnabled && listener.mIsActive) {
                mLocationManager.removeUpdates(listener);
            }
        } catch (IllegalArgumentException ex) {
            AppGlobals.guiLogError("enableLocationListener failed");
        }

        listener.mIsActive = isEnabled;
    }

    public void removeListener() {
        if (mLocationManager == null) {
            return;
        }

        mLocationManager.removeGpsStatusListener(mSatelliteListener);
        enableLocationListener(false, mGpsLocationListener);
        enableLocationListener(false, mNetworkLocationListener);
        enableLocationListener(false, mPassiveLocationListener);
    }

    public void pauseGpsUpdates(boolean isGpsOff) {
        enableLocationListener(!isGpsOff, mGpsLocationListener);
        enableLocationListener(true, mNetworkLocationListener);
    }

    interface ReceivedLocationCallback {
        void receivedLocation();
    }

    private class LocationUpdateListener implements LocationListener {
        final ReceivedLocationCallback mCallback;
        final long mFreqMs;
        final String mType;
        boolean mIsActive;

        public LocationUpdateListener(String type, long freq, ReceivedLocationCallback callback) {
            mCallback = callback;
            mType = type;
            mFreqMs = freq;
        }

        public void onLocationChanged(Location location) {
            if (mMapActivity.get() != null) {
                mMapActivity.get().setUserPositionAt(location);
            }
            if (mCallback != null) {
                mCallback.receivedLocation();
            }
        }

        public void onStatusChanged(String s, int i, Bundle bundle) {
        }

        public void onProviderEnabled(String s) {
        }

        public void onProviderDisabled(String s) {
        }
    }
}