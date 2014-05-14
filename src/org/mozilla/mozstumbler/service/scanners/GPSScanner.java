package org.mozilla.mozstumbler.service.scanners;

import android.content.Context;
import android.content.Intent;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import org.mozilla.mozstumbler.SharedConstants;
import org.mozilla.mozstumbler.service.Prefs;

public class GPSScanner implements LocationListener {
    public static final String ACTION_BASE = SharedConstants.ACTION_NAMESPACE + ".GPSScanner.";
    public static final String ACTION_GPS_UPDATED = ACTION_BASE + "GPS_UPDATED";
    public static final String ACTION_ARG_TIME = SharedConstants.ACTION_ARG_TIME;
    public static final String SUBJECT_NEW_STATUS = "new_status";
    public static final String SUBJECT_LOCATION_LOST = "location_lost";
    public static final String SUBJECT_NEW_LOCATION = "new_location";
    public static final String NEW_STATUS_ARG_FIXES = "fixes";
    public static final String NEW_STATUS_ARG_SATS = "sats";
    public static final String NEW_LOCATION_ARG_LOCATION = "location";

    private static final String   LOGTAG                  = GPSScanner.class.getName();
    private static final long     GEO_MIN_UPDATE_TIME     = 1000;
    private static final float    GEO_MIN_UPDATE_DISTANCE = 10;
    private static final int      MIN_SAT_USED_IN_FIX     = 3;

    private final Context         mContext;
    private GpsStatus.Listener    mGPSListener;

    private int mLocationCount;
    private double mLatitude;
    private double mLongitude;
    private LocationBlockList mBlockList;
    private boolean mAutoGeofencing;

    public GPSScanner(Context context) {
        mContext = context;
    }

    public void start() {
        LocationManager lm = getLocationManager();
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                                                            GEO_MIN_UPDATE_TIME,
                                                            GEO_MIN_UPDATE_DISTANCE,
                                                            this);

        reportLocationLost();
        mGPSListener = new GpsStatus.Listener() {
                public void onGpsStatusChanged(int event) {
                    if (event == GpsStatus.GPS_EVENT_SATELLITE_STATUS) {
                        GpsStatus status = getLocationManager().getGpsStatus(null);
                        Iterable<GpsSatellite> sats = status.getSatellites();

                        int satellites = 0;
                        int fixes = 0;

                        for (GpsSatellite sat : sats) {
                            satellites++;
                            if(sat.usedInFix()) {
                                fixes++;
                            }
                        }
                        reportNewGpsStatus(fixes,satellites);
                        if (fixes < MIN_SAT_USED_IN_FIX) {
                            reportLocationLost();
                        }
                        Log.d(LOGTAG, "onGpsStatusChange - satellites: " + satellites + " fixes: " + fixes);
                    } else if (event == GpsStatus.GPS_EVENT_STOPPED) {
                        reportLocationLost();
                    }
                }
            };

        lm.addGpsStatusListener(mGPSListener);
        mBlockList = new LocationBlockList(mContext);
    }

    public void stop() {
        LocationManager lm = getLocationManager();
        lm.removeUpdates(this);
        reportLocationLost();

        if (mGPSListener != null) {
          lm.removeGpsStatusListener(mGPSListener);
          mGPSListener = null;
        }
    }

    public int getLocationCount() {
        return mLocationCount;
    }

    public double getLatitude() {
        return mLatitude;
    }

    public double getLongitude() {
        return mLongitude;
    }
    public void checkPrefs() {
        Log.d(LOGTAG,"Updating blocking data.");
        if (mBlockList!=null) mBlockList.update_blocks();
        Prefs prefs = new Prefs(mContext);
        mAutoGeofencing = prefs.getGeofenceHere();
    }

    public boolean isGeofenced() {
        return (mBlockList != null) && mBlockList.isGeofenced();
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location == null) {
            reportLocationLost();
            return;
        }

        if (mBlockList.contains(location)) {
            Log.w(LOGTAG, "Blocked location: " + location);
            reportLocationLost();
            return;
        }

        Log.d(LOGTAG, "New location: " + location);

        mLongitude = location.getLongitude();
        mLatitude = location.getLatitude();

        if (!mAutoGeofencing) { reportNewLocationReceived(location); }
        mLocationCount++;
    }

    @Override
    public void onProviderDisabled(String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider)) {
            reportLocationLost();
        }
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        if ((status != LocationProvider.AVAILABLE)
                && (LocationManager.GPS_PROVIDER.equals(provider))) {
            reportLocationLost();
        }
    }

    private LocationManager getLocationManager() {
        return (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
    }

    private void reportNewLocationReceived(Location location) {
        Intent i = new Intent(ACTION_GPS_UPDATED);
        i.putExtra(Intent.EXTRA_SUBJECT, SUBJECT_NEW_LOCATION);
        i.putExtra(NEW_LOCATION_ARG_LOCATION, location);
        i.putExtra(ACTION_ARG_TIME, System.currentTimeMillis());
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(i);
    }

    private void reportLocationLost() {
        Intent i = new Intent(ACTION_GPS_UPDATED);
        i.putExtra(Intent.EXTRA_SUBJECT, SUBJECT_LOCATION_LOST);
        i.putExtra(ACTION_ARG_TIME, System.currentTimeMillis());
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(i);
    }

    private void reportNewGpsStatus(int fixes, int sats) {
        Intent i = new Intent(ACTION_GPS_UPDATED);
        i.putExtra(Intent.EXTRA_SUBJECT, SUBJECT_NEW_STATUS);
        i.putExtra(NEW_STATUS_ARG_FIXES, fixes);
        i.putExtra(NEW_STATUS_ARG_SATS, sats);
        i.putExtra(ACTION_ARG_TIME, System.currentTimeMillis());
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(i);
    }
}
