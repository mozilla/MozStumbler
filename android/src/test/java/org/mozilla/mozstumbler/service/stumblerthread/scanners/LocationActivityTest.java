/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.mozstumbler.service.stumblerthread.scanners;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLocationManager;

import static junit.framework.Assert.assertEquals;
import static org.robolectric.Robolectric.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(shadows = {CustomShadowLocationManager.class})
public class LocationActivityTest {
    private LocationActivity locationActivity;

    @Before
    public void setUp() {
        locationActivity = Robolectric.buildActivity(LocationActivity.class).create().get();
    }

    @Test
    public void shouldReturnTheLatestLocation() {
        LocationManager locationManager = (LocationManager)
                Robolectric.application.getSystemService(Context.LOCATION_SERVICE);
        ShadowLocationManager shadowLocationManager = shadowOf(locationManager);

        LocationGenerator lgen = new LocationGenerator();
        Location expectedLocation = lgen.next();

        // This would be a method on our SimulationContext
        locationActivity.setTestProviderLocation(LocationManager.GPS_PROVIDER, expectedLocation);

        Location actualLocation = locationActivity.latestLocation();
        assertEquals(expectedLocation, actualLocation);
    }

    private Location location(String provider, double latitude, double longitude) {
        Location location = new Location(provider);
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        location.setTime(System.currentTimeMillis());
        return location;
    }
}
