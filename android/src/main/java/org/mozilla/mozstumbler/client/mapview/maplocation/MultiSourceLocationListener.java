/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.mozstumbler.client.mapview.maplocation;

import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.LocationManager;

import org.mozilla.mozstumbler.client.mapview.MapFragment;
import org.mozilla.mozstumbler.client.mapview.maplocation.MapUpdatingLocationListener.ReceivedLocationCallback;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.GPSScanner;

import java.lang.ref.WeakReference;

public class MultiSourceLocationListener {
    private final LocationManager mLocationManager;
    private final ReceivedLocationCallback mReceivedGpsLocation = new ReceivedLocationCallback() {
        @Override
        public void receivedLocation() {
            // Once the gps begins receiving locations, stop the network listener
            UserPositionUpdateManager.enableLocationListener(mLocationManager, false, mNetworkLocationListener);
        }
    };

    private final MapUpdatingLocationListener mNetworkLocationListener;
    private final MapUpdatingLocationListener mGpsLocationListener;
    private final MapUpdatingLocationListener mPassiveLocationListener;
    private final WeakReference<MapFragment> mMapFragment;
    private boolean mPassive;

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
                if (mMapFragment.get() != null) {
                    mMapFragment.get().updateGPSInfo(satellites, fixes);
                }

                if (fixes < GPSScanner.MIN_SAT_USED_IN_FIX) {
                    notEnoughSatellites();
                }
            }
        }
    };

    MultiSourceLocationListener(LocationManager manager, MapFragment mapFragment, boolean passive) {
        mLocationManager = manager;
        mPassive = passive;
        mMapFragment = new WeakReference<MapFragment>(mapFragment);
        mNetworkLocationListener = new NetworkMapUpdatingLocationListener(mapFragment, null);
        mGpsLocationListener = new GPSMapUpdatingLocationListener(mapFragment, mReceivedGpsLocation);
        mPassiveLocationListener = new PassiveMapUpdatingLocationListener(mapFragment, null);
    }

    public void start() {
        mLocationManager.addGpsStatusListener(mSatelliteListener);
        setLocationUpdatesEnabled(true);
    }

    public void stop() {
        mLocationManager.removeGpsStatusListener(mSatelliteListener);
        setLocationUpdatesEnabled(false);
    }

    private  void setLocationUpdatesEnabled(boolean enable) {
        if (mLocationManager == null) {
            return;
        }

        if (enable) {
            if (!mPassive) {
                // enable NetworkLocationListener first because GpsLocationListener will disable it
                UserPositionUpdateManager.enableLocationListener(mLocationManager, true, mNetworkLocationListener);
                UserPositionUpdateManager.enableLocationListener(mLocationManager, true, mGpsLocationListener);
            } else {
                UserPositionUpdateManager.enableLocationListener(mLocationManager, true, mPassiveLocationListener);
            }
        } else {
            // disable GpsLocationListener first because it might enable NetworkLocationListener
            UserPositionUpdateManager.enableLocationListener(mLocationManager, false, mGpsLocationListener);
            UserPositionUpdateManager.enableLocationListener(mLocationManager, false, mNetworkLocationListener);
            UserPositionUpdateManager.enableLocationListener(mLocationManager, false, mPassiveLocationListener);
        }
    }

    // See mReceivedGpsLocation for the case where the GPS is active again
    public void notEnoughSatellites() {
        if (!mGpsLocationListener.mIsActive || mNetworkLocationListener.mIsActive) {
            return;
        }

        UserPositionUpdateManager.enableLocationListener(mLocationManager, true, mNetworkLocationListener);
    }
}
