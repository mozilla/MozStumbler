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

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.core.logging.Log;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.GPSScanner;

import java.lang.ref.WeakReference;

class MapLocationListener  {
    private final WeakReference<MapFragment> mMapActivity;
    private LocationManager mLocationManager;
    private static final String LOG_TAG = AppGlobals.makeLogTag(MapLocationListener.class.getSimpleName());

    interface ReceivedLocationCallback {
        void receivedLocation();
    }

    private final ReceivedLocationCallback mReceivedGpsLocation = new ReceivedLocationCallback() {
        @Override
        public void receivedLocation() {
            // Once the gps begins receiving locations, stop the network listener
            enableLocationListener(false, mNetworkLocationListener);
        }
    };

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

        public void onStatusChanged(String s, int i, Bundle bundle) {}
        public void onProviderEnabled(String s) {}
        public void onProviderDisabled(String s) {}
    }

    private final LocationUpdateListener mGpsLocationListener =
            new LocationUpdateListener(LocationManager.GPS_PROVIDER, 5000, mReceivedGpsLocation);
    private final LocationUpdateListener mNetworkLocationListener =
            new LocationUpdateListener(LocationManager.NETWORK_PROVIDER, 1000, null);

    MapLocationListener(MapFragment mapFragment) {
        mMapActivity = new WeakReference<MapFragment>(mapFragment);
        mLocationManager = (LocationManager) mapFragment.getActivity().getApplicationContext().
                getSystemService(Context.LOCATION_SERVICE);

        final GpsStatus.Listener satelliteListener = new GpsStatus.Listener() {
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

        mLocationManager.addGpsStatusListener(satelliteListener);

        Location lastLoc = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (lastLoc == null) {
            lastLoc = mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }
        if (lastLoc != null && mMapActivity.get() != null) {
            Log.d(LOG_TAG, "set last known location");
            mMapActivity.get().setUserPositionAt(lastLoc);
        }

        enableLocationListener(true, mNetworkLocationListener);
        enableLocationListener(true, mGpsLocationListener);

        if (mapFragment.getApplication().isIsScanningPausedDueToNoMotion()) {
            pauseGpsUpdates(true);
        }
    }

    private void enableLocationListener(boolean isEnabled, LocationUpdateListener listener) {
        if (isEnabled && !listener.mIsActive) {
            mLocationManager.requestLocationUpdates(listener.mType, listener.mFreqMs, 0, listener);
        } else if (!isEnabled && listener.mIsActive) {
            mLocationManager.removeUpdates(listener);
        }
        listener.mIsActive = isEnabled;
    }

    void removeListener() {
        enableLocationListener(false, mGpsLocationListener);
        enableLocationListener(false, mNetworkLocationListener);
    }

    public void pauseGpsUpdates(boolean isGpsOff) {
        enableLocationListener(!isGpsOff, mGpsLocationListener);
        enableLocationListener(true, mNetworkLocationListener);
    }
}