/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.mozstumbler.client.mapview.maplocation;

import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import org.mozilla.mozstumbler.client.mapview.MapFragment;
import java.lang.ref.WeakReference;

// Listens for position changes and updates the position on the map view
public class MapUpdatingLocationListener implements LocationListener {
    interface ReceivedLocationCallback {
        void receivedLocation();
    }

    final ReceivedLocationCallback mCallback;
    final long mFreqMs;
    final String mType;
    boolean mIsActive;
    private final WeakReference<MapFragment> mMap;

    public MapUpdatingLocationListener(MapFragment map, String type, long freq, ReceivedLocationCallback callback) {
        mCallback = callback;
        mType = type;
        mFreqMs = freq;
        mMap = new WeakReference<MapFragment>(map);
    }

    public void onLocationChanged(Location location) {
        if (mMap.get() != null) {
            mMap.get().setUserPositionAt(location);
        }
        if (mCallback != null) {
            mCallback.receivedLocation();
        }
    }

    public void onStatusChanged(String s, int i, Bundle bundle) {}
    public void onProviderEnabled(String s) {}
    public void onProviderDisabled(String s) {}
}