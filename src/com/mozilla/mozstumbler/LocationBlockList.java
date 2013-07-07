package com.mozilla.mozstumbler;

import android.location.Location;

final class LocationBlockList {
    private static final long MILLISECONDS_PER_DAY = 86400000; // milliseconds/day
    private static final double MAX_ALTITUDE = 8848; // Mount Everest's altitude in meters
    private static final double MIN_ALTITUDE = -418; // Dead Sea's altitude in meters
    private static final float MAX_SPEED = 340.29f; // Mach 1 in meters/second
    private static final float MAX_INACCURACY = 500; // meter radius
    private static final long MIN_TIMESTAMP = 946684801; // 2000-01-01 00:00:01

    private LocationBlockList() {
    }

    static boolean contains(Location location) {
        final float inaccuracy = location.getAccuracy();
        final double altitude = location.getAltitude();
        final float bearing = location.getBearing();
        final double latitude = location.getLatitude();
        final double longitude = location.getLongitude();
        final float speed = location.getSpeed();
        final long timestamp = location.getTime();
        final long tomorrow = System.currentTimeMillis() + MILLISECONDS_PER_DAY;

        return inaccuracy < 0 ||
               inaccuracy > MAX_INACCURACY ||
               altitude < MIN_ALTITUDE ||
               altitude > MAX_ALTITUDE ||
               bearing < 0 ||
               bearing > 360 ||
               latitude < -180 ||
               latitude > 180 ||
               longitude < -180 ||
               longitude > 180 ||
               (latitude == 0 && longitude == 0) ||
               speed < 0 ||
               speed > MAX_SPEED ||
               timestamp < MIN_TIMESTAMP ||
               timestamp > tomorrow;
    }
}
