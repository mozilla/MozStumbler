/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread.scanners;

import android.location.Location;
import android.os.Build;
import android.util.Log;

import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

/*

 The StumblerFilter applies some minimal checks on location data
 to filter out unwanted location data.

 */
public final class StumblerFilter {
    private static final String LOG_TAG = LoggerUtil.makeLogTag(StumblerFilter.class);
    private static final double MAX_ALTITUDE = 8848;      // Mount Everest's altitude in meters
    private static final double MIN_ALTITUDE = -418;      // Dead Sea's altitude in meters
    private static final float MAX_SPEED = 340.29f;   // Mach 1 in meters/second
    private static final float MIN_ACCURACY = 500;       // meter radius
    private static final long MIN_TIMESTAMP = Build.TIME; // The build time of the application
    private static final long MILLISECONDS_PER_DAY = 86400000;

    public boolean blockLocation(Location location) {
        final float inaccuracy = location.getAccuracy();
        final double altitude = location.getAltitude();
        final float bearing = location.getBearing();
        final double latitude = location.getLatitude();
        final double longitude = location.getLongitude();
        final float speed = location.getSpeed();
        final long timestamp = location.getTime();
        final long tomorrow = System.currentTimeMillis() + MILLISECONDS_PER_DAY;

        boolean block = false;

        if (latitude == 0 && longitude == 0) {
            block = true;
            Log.w(LOG_TAG, "Bogus latitude,longitude: 0,0");
        } else {
            if (latitude < -90 || latitude > 90) {
                block = true;
                Log.w(LOG_TAG, "Bogus latitude: " + latitude);
            }

            if (longitude < -180 || longitude > 180) {
                block = true;
                Log.w(LOG_TAG, "Bogus longitude: " + longitude);
            }
        }

        if (location.hasAccuracy() && (inaccuracy < 0 || inaccuracy > MIN_ACCURACY)) {
            block = true;
            Log.w(LOG_TAG, "Insufficient accuracy: " + inaccuracy + " meters");
        }

        if (location.hasAltitude() && (altitude < MIN_ALTITUDE || altitude > MAX_ALTITUDE)) {
            block = true;
            Log.w(LOG_TAG, "Bogus altitude: " + altitude + " meters");
        }

        if (location.hasBearing() && (bearing < 0 || bearing > 360)) {
            block = true;
            Log.w(LOG_TAG, "Bogus bearing: " + bearing + " degrees");
        }

        if (location.hasSpeed() && (speed < 0 || speed > MAX_SPEED)) {
            block = true;
            Log.w(LOG_TAG, "Bogus speed: " + speed + " meters/second");
        }

        if (timestamp < MIN_TIMESTAMP || timestamp > tomorrow) {
            block = true;
            Log.w(LOG_TAG, "Bogus timestamp: " + timestamp);
        }

        return block;
    }
}
