/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.mapview;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

import java.lang.ref.WeakReference;

class GPSListener implements LocationListener {
   private WeakReference<MapActivity> mMapActivity;
   private LocationManager mLocationManager;

    GPSListener(MapActivity mapActivity) {
        mMapActivity = new WeakReference<MapActivity>(mapActivity);
        mLocationManager = (LocationManager) mapActivity.getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, this);
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
    public void onStatusChanged(String s, int i, Bundle bundle) {}

    @Override
    public void onProviderEnabled(String s) {}

    @Override
    public void onProviderDisabled(String s) {}
}