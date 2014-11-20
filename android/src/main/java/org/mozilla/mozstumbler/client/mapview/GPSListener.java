/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.mapview;

import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

import org.mozilla.mozstumbler.client.ClientPrefs;

import java.lang.ref.WeakReference;

class GPSListener implements LocationListener {
    private WeakReference<MapFragment> mMapActivity;
    private LocationManager mLocationManager;
    private final GpsStatus.Listener mStatusListener;

    private static GpsStatus sGpsStatus;

    GPSListener(MapFragment mapFragment) {
        mMapActivity = new WeakReference<MapFragment>(mapFragment);

        mLocationManager = mapFragment.getLocationManager();
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, this);
        mStatusListener = new GpsStatus.Listener() {
            public void onGpsStatusChanged(int event) {
                if (event == GpsStatus.GPS_EVENT_SATELLITE_STATUS && mLocationManager != null) {
                    if (sGpsStatus != null) {
                        mLocationManager.getGpsStatus(sGpsStatus);
                    } else {
                        sGpsStatus = mLocationManager.getGpsStatus(null);
                    }
                    Iterable<GpsSatellite> sats = sGpsStatus.getSatellites();
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
                }
            }
        };

        mLocationManager.addGpsStatusListener(mStatusListener);
        Location lastGpsLoc = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        Location lastNetworkLoc = mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

        if (lastGpsLoc != null) {
            onLocationChanged(lastGpsLoc);
        } else if (lastNetworkLoc != null) {
            onLocationChanged(lastNetworkLoc);
        }
    }

    void removeListener() {
        mLocationManager.removeUpdates(this);
        mMapActivity = null;
        mLocationManager = null;
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location == null) {
            return;
        }

        if (mMapActivity != null) {
            mMapActivity.get().setUserPositionAt(location);

            ClientPrefs cPrefs = ClientPrefs.getInstance();
            if (cPrefs.isDefaultSimulationLatLon()) {
                cPrefs.setSimulationLat((float) location.getLatitude());
                cPrefs.setSimulationLon((float) location.getLongitude());
                cPrefs.wroteSimulationLatLon();
            }
        }
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {
    }

    @Override
    public void onProviderEnabled(String s) {}

    @Override
    public void onProviderDisabled(String s) {}
}