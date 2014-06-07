package org.mozilla.mozstumbler.service.scanners;

import android.content.Context;
import android.location.Location;
import android.util.Log;
import org.mozilla.mozstumbler.service.utils.DateTimeUtils;
import org.mozilla.mozstumbler.service.Prefs;


public final class LocationBlockList {
    private static final String LOGTAG          = LocationBlockList.class.getName();
    private static final double MAX_ALTITUDE    = 8848;      // Mount Everest's altitude in meters
    private static final double MIN_ALTITUDE    = -418;      // Dead Sea's altitude in meters
    private static final float  MAX_SPEED       = 340.29f;   // Mach 1 in meters/second
    private static final float  MIN_ACCURACY    = 500;       // meter radius
    private static final long   MIN_TIMESTAMP   = 946684801; // 2000-01-01 00:00:01
    private static final double GEOFENCE_RADIUS = 0.01;      // .01 degrees is approximately 1km

    private Context mContext;
    private Location mBlockedLocation;
    private boolean mGeofencingEnabled;
    private boolean mIsGeofenced = false;

    public LocationBlockList(Context context) {
        mContext = context;
        update_blocks();
    }

    public void update_blocks()    {
        Prefs prefs = new Prefs(mContext);
        mBlockedLocation = prefs.getGeofenceLocation();
        mGeofencingEnabled = prefs.getGeofenceEnabled();
    }

    public boolean contains(Location location) {
        final float inaccuracy = location.getAccuracy();
        final double altitude = location.getAltitude();
        final float bearing = location.getBearing();
        final double latitude = location.getLatitude();
        final double longitude = location.getLongitude();
        final float speed = location.getSpeed();
        final long timestamp = location.getTime();
        final long tomorrow = System.currentTimeMillis() + DateTimeUtils.MILLISECONDS_PER_DAY;

        boolean block = false;
        mIsGeofenced = false;

        if (latitude == 0 && longitude == 0) {
            block = true;
            Log.w(LOGTAG, "Bogus latitude,longitude: 0,0");
        } else {
            if (latitude < -90 || latitude > 90) {
                block = true;
                Log.w(LOGTAG, "Bogus latitude: " + latitude);
            }

            if (longitude < -180 || longitude > 180) {
                block = true;
                Log.w(LOGTAG, "Bogus longitude: " + longitude);
            }
        }

        if (location.hasAccuracy() && (inaccuracy < 0 || inaccuracy > MIN_ACCURACY)) {
            block = true;
            Log.w(LOGTAG, "Insufficient accuracy: " + inaccuracy + " meters");
        }

        if (location.hasAltitude() && (altitude < MIN_ALTITUDE || altitude > MAX_ALTITUDE)) {
            block = true;
            Log.w(LOGTAG, "Bogus altitude: " + altitude + " meters");
        }

        if (location.hasBearing() && (bearing < 0 || bearing > 360)) {
            block = true;
            Log.w(LOGTAG, "Bogus bearing: " + bearing + " degrees");
        }

        if (location.hasSpeed() && (speed < 0 || speed > MAX_SPEED)) {
            block = true;
            Log.w(LOGTAG, "Bogus speed: " + speed + " meters/second");
        }

        if (timestamp < MIN_TIMESTAMP || timestamp > tomorrow) {
            block = true;
            Log.w(LOGTAG, "Bogus timestamp: " + timestamp + " (" + DateTimeUtils.formatTime(timestamp) + ")");
        }

        if (mGeofencingEnabled &&
                Math.abs(location.getLatitude() - mBlockedLocation.getLatitude()) < GEOFENCE_RADIUS &&
                Math.abs(location.getLongitude() - mBlockedLocation.getLongitude()) < GEOFENCE_RADIUS) {
            block = true;
            mIsGeofenced = true;
            Log.i(LOGTAG, "Hit the geofence: " + mBlockedLocation.getLatitude() +" / " + mBlockedLocation.getLongitude());
        }

        return block;
    }

    public boolean isGeofenced() { return mIsGeofenced; }
}
