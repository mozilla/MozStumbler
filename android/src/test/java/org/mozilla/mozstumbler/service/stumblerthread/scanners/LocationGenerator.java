/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.mozstumbler.service.stumblerthread.scanners;

import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.SystemClock;

import java.util.Iterator;

/*
 Use this to inject a list of locations into the mocks
 */
public class LocationGenerator implements Iterator<Location> {

    private final Location theLocation;

    public LocationGenerator() {

        Location mockLocation = new Location(LocationManager.GPS_PROVIDER); // a string
        mockLocation.setLatitude(35.1);
        mockLocation.setLongitude(40.2);
        mockLocation.setAltitude(42.0);  // meters above sea level
        mockLocation.setAccuracy(5);
        mockLocation.setTime(System.currentTimeMillis());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            mockLocation.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        }

        theLocation = mockLocation;
    }

    @Override
    public boolean hasNext() {
        return true;
    }

    @Override
    public Location next() {
        return theLocation;
    }

    @Override
    public void remove() {
    }
}
