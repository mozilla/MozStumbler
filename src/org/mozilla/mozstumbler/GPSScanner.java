package org.mozilla.mozstumbler;

import android.content.Context;
import android.content.Intent;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;

import org.json.JSONException;
import org.json.JSONObject;
import android.os.Bundle;
import android.util.Log;

public class GPSScanner implements LocationListener {
    public static final String GPS_SCANNER_EXTRA_SUBJECT = "GPSScanner";

    private static final String   LOGTAG                  = Scanner.class.getName();
    private static final long     GEO_MIN_UPDATE_TIME     = 1000;
    private static final float    GEO_MIN_UPDATE_DISTANCE = 10;

    private final Context         mContext;
    private GpsStatus.Listener    mGPSListener;

    private int mLocationCount;
    private double mLatitude;
    private double mLongitude;

    GPSScanner(Context context) {
        mContext = context;
    }

    public void start() {
        LocationManager lm = getLocationManager();
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                                                            GEO_MIN_UPDATE_TIME,
                                                            GEO_MIN_UPDATE_DISTANCE,
                                                            this);

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
                        reportNewGpsStatus(fixes);
                        Log.d(LOGTAG, "onGpsStatusChange - satellites: " + satellites + " fixes: " + fixes);
                    }
                }
            };

        lm.addGpsStatusListener(mGPSListener);
    }

    public void stop() {
        LocationManager lm = getLocationManager();
        lm.removeUpdates(this);

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

    @Override
    public void onLocationChanged(Location location) {
        if (location == null) {
            return;
        }

        if (LocationBlockList.contains(location)) {
            Log.w(LOGTAG, "Blocked location: " + location);
            return;
        }

        Log.d(LOGTAG, "New location: " + location);

        mLongitude = location.getLongitude();
        mLatitude = location.getLatitude();

        reportNewLocationReceived(location);
        mLocationCount++;
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    private LocationManager getLocationManager() {
        return (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
    }

    private void reportNewLocationReceived(Location location) {
        JSONObject locInfo = new JSONObject();
        try {
            locInfo.put("time", DateTimeUtils.formatTime(location.getTime()));
            locInfo.put("lon", location.getLongitude());
            locInfo.put("lat", location.getLatitude());
            locInfo.put("accuracy", (int) location.getAccuracy());
            locInfo.put("altitude", (int) location.getAltitude());
        } catch (JSONException jsonex) {
            Log.e(LOGTAG, "", jsonex);
        }

        Intent i = new Intent(ScannerService.MESSAGE_TOPIC);
        i.putExtra(Intent.EXTRA_SUBJECT, GPS_SCANNER_EXTRA_SUBJECT);
        i.putExtra("data", locInfo.toString());
        i.putExtra("time", System.currentTimeMillis());
        mContext.sendBroadcast(i);
    }

    private void reportNewGpsStatus(int fixes) {
        Intent i = new Intent(ScannerService.MESSAGE_TOPIC);
        i.putExtra(Intent.EXTRA_SUBJECT, "Scanner");
        i.putExtra("fixes", fixes);
        mContext.sendBroadcast(i);
    }
}
