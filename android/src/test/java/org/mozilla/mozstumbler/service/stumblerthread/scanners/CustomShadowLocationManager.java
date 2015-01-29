/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.mozstumbler.service.stumblerthread.scanners;

import android.location.LocationListener;
import android.location.LocationManager;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowLocationManager;

import java.util.HashMap;
import java.util.LinkedList;

@Implements(LocationManager.class)
public class CustomShadowLocationManager extends ShadowLocationManager {

    private HashMap<String, LinkedList<LocationListener>> registeredListeners = new HashMap<String, LinkedList<LocationListener>>();


    public void requestLocationUpdates(String provider, long minTime, float minDistance, LocationListener listener) {
        if (registeredListeners.get(provider) == null) {
            registeredListeners.put(provider, new LinkedList<LocationListener>());
        }
        LinkedList<LocationListener> listeners = registeredListeners.get(provider);
        listeners.add(listener);
    }


    @Implementation
    public void addTestProvider(java.lang.String name, boolean requiresNetwork, boolean requiresSatellite, boolean requiresCell, boolean hasMonetaryCost, boolean supportsAltitude, boolean supportsSpeed, boolean supportsBearing, int powerRequirement, int accuracy) {
        // Note that this is only implemented because robolectric 2.4 doesn't
        // implement the method already.  This should change should be pushed upstream to the main
        // robolectric branch
    }

    @Implementation
    public void setTestProviderEnabled(java.lang.String provider, boolean enabled) {
        // Note that this is only implemented because robolectric 2.4 doesn't
        // implement the method already.  This should change should be pushed upstream to the main
        // robolectric branch
    }


    @Implementation
    public void setTestProviderLocation(java.lang.String provider, android.location.Location loc) {
        // Note that this is only implemented because robolectric 2.4 doesn't
        // implement the method already.  This should change should be pushed upstream to the main
        // robolectric branch
        if (registeredListeners.get(provider) == null) {
            registeredListeners.put(provider, new LinkedList<LocationListener>());
        }

        for (LocationListener listener : registeredListeners.get(provider)) {
            listener.onLocationChanged(loc);
        }
    }
}
