package org.mozilla.mozstumbler.service.stumblerthread.scanners;

import android.content.Context;
import android.content.ContextWrapper;
import android.location.Location;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.Handler;

/**
 * Created by victorng on 14-11-04.
 */
public class SimulateStumbleContextWrapper extends ContextWrapper {
    private final LocationManager locationManager;


    final boolean REQUIRED_NETWORK = false;
    final boolean REQUIRES_SATELLITE = false;
    final boolean REQUIRES_CELL = false;
    final boolean HAS_MONETARY_COST = false;
    final boolean SUPPORTS_ALTITUDE = false;
    final boolean SUPPORTS_SPEED = false;
    final boolean SUPPORTS_BEARING = false;
    final int POWER_REQUIREMENT = 0;
    final int ACCURACY = 5;
    private final WifiManager wifiManager;

    public SimulateStumbleContextWrapper(Context context) {
        super(context);

        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        // Register test provider for GPS
        locationManager.addTestProvider(LocationManager.GPS_PROVIDER,
                REQUIRED_NETWORK, REQUIRES_SATELLITE,
                REQUIRES_CELL, HAS_MONETARY_COST, SUPPORTS_ALTITUDE, SUPPORTS_SPEED,
                SUPPORTS_BEARING, POWER_REQUIREMENT, ACCURACY);
        locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER,
                true);



    }

    private final static int INTERVAL = 1000 * 1; // Every second
    Handler handler = new Handler();

    void startRepeatingTask()
    {
        handler.postDelayed(new Runnable() {
            public void run() {
                Location location = getNextLocation();
                if (location == null) {
                    return;
                }

                Location mockLocation = new Location(LocationManager.GPS_PROVIDER); // a string
                mockLocation.setLatitude(location.getLatitude());  // double
                mockLocation.setLongitude(location.getLongitude());
                mockLocation.setAltitude(location.getAltitude());
                mockLocation.setTime(System.currentTimeMillis());
                locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, mockLocation);

                Location mockLocation = new Location(LocationManager.NETWORK_PROVIDER); // a string
                mockLocation.setLatitude(location.getLatitude());  // double
                mockLocation.setLongitude(location.getLongitude());
                mockLocation.setAltitude(location.getAltitude());
                mockLocation.setTime(System.currentTimeMillis());
                locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, mockLocation);


                // Send another ping
                handler.postDelayed(this, INTERVAL);
            }
        }, INTERVAL);

    }

    private Location getNextLocation() {
        // TODO: this should be a generator of some kind
        return null;
    }

    public Object getSystemService(String name) {
        if (name.equals(Context.LOCATION_SERVICE)){
            return locationManager;
        }
    }


}

