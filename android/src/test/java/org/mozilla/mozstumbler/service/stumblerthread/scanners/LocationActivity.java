/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.mozstumbler.service.stumblerthread.scanners;

import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

import java.util.List;

public class LocationActivity extends Activity implements LocationListener {

    final boolean REQUIRED_NETWORK = false;
    final boolean REQUIRES_SATELLITE = false;
    final boolean REQUIRES_CELL = false;
    final boolean HAS_MONETARY_COST = false;
    final boolean SUPPORTS_ALTITUDE = false;
    final boolean SUPPORTS_SPEED = false;
    final boolean SUPPORTS_BEARING = false;
    final int POWER_REQUIREMENT = 0;
    final int ACCURACY = 5;

    private Location latestLocation;
    private LocationManager locationManager;
    private LocationGenerator locationGenerator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initLocationListener();
    }

    private void initLocationListener() {
        locationManager = (LocationManager)
                getSystemService(Context.LOCATION_SERVICE);
        List<String> allProviders = locationManager.getAllProviders();
        for (String provider : allProviders) {
            locationManager.requestLocationUpdates(provider, 0, 0, this);
        }

        // Register test provider for GPS
        locationManager.addTestProvider(LocationManager.GPS_PROVIDER,
                REQUIRED_NETWORK, REQUIRES_SATELLITE,
                REQUIRES_CELL, HAS_MONETARY_COST, SUPPORTS_ALTITUDE, SUPPORTS_SPEED,
                SUPPORTS_BEARING, POWER_REQUIREMENT, ACCURACY);
        locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER,
                true);

        locationGenerator = new LocationGenerator();
    }

    @Override
    public void onLocationChanged(Location location) {
        latestLocation = location;
    }

    public Location latestLocation() {
        return latestLocation;
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {
    }

    @Override
    public void onProviderDisabled(String s) {
    }

    @Override
    public void onProviderEnabled(String s) {
    }

    public void setTestProviderLocation(String provider, Location loc) {
        locationManager.setTestProviderLocation(provider, loc);
    }
}
