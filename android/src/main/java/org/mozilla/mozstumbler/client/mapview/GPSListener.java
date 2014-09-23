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
import android.util.Log;

import org.mozilla.mozstumbler.service.AppGlobals;

import java.lang.ref.WeakReference;

class GPSListener implements LocationListener {
   private WeakReference<MapActivity> mMapActivity;
   private LocationManager mLocationManager;
   private GpsStatus.Listener mStatusListener;

    GPSListener(MapActivity mapActivity) {
        mMapActivity = new WeakReference<MapActivity>(mapActivity);
        mLocationManager = (LocationManager) mapActivity.getActivity().getApplicationContext().
                getSystemService(Context.LOCATION_SERVICE);
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, this);
        mStatusListener = new GpsStatus.Listener() {
            public void onGpsStatusChanged(int event) {
                if (event == GpsStatus.GPS_EVENT_SATELLITE_STATUS && mLocationManager != null) {
                    GpsStatus status = mLocationManager.getGpsStatus(null);
                    Iterable<GpsSatellite> sats = status.getSatellites();
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
    }

    void removeListener() {
        mLocationManager.removeUpdates(this);
        mMapActivity = null;
        mLocationManager = null;
    }

    @Override
    public void onLocationChanged(Location location) {
        if (mMapActivity != null) {
            mMapActivity.get().setUserPositionAt(location);
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