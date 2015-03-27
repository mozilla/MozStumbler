/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.mozstumbler.client.mapview.maplocation;

import android.location.LocationManager;
import org.mozilla.mozstumbler.client.mapview.MapFragment;
import org.mozilla.mozstumbler.client.mapview.maplocation.MapUpdatingLocationListener.ReceivedLocationCallback;
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

    private final WeakReference<MapFragment> mMapFragment;

    MultiSourceLocationListener(LocationManager manager, MapFragment mapFragment) {
        mLocationManager = manager;
        mMapFragment = new WeakReference<MapFragment>(mapFragment);
        mNetworkLocationListener = new MapUpdatingLocationListener(mapFragment, LocationManager.NETWORK_PROVIDER, 1000, null);
        mGpsLocationListener = new MapUpdatingLocationListener(mapFragment, LocationManager.GPS_PROVIDER, 1000, mReceivedGpsLocation);
    }

    public void start() {
        setLocationUpdatesEnabled(true);
    }

    public void stop() {
        setLocationUpdatesEnabled(false);
    }

    private  void setLocationUpdatesEnabled(boolean enable) {
        if (mLocationManager == null) {
            return;
        }

        if (enable) {
            // enable NetworkLocationListener first because GpsLocationListener will disable it
            UserPositionUpdateManager.enableLocationListener(mLocationManager, true, mNetworkLocationListener);
            UserPositionUpdateManager.enableLocationListener(mLocationManager, true, mGpsLocationListener);
        } else {
            // disable GpsLocationListener first because it might enable NetworkLocationListener
            UserPositionUpdateManager.enableLocationListener(mLocationManager, false, mGpsLocationListener);
            UserPositionUpdateManager.enableLocationListener(mLocationManager, false, mNetworkLocationListener);
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
