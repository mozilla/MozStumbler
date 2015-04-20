/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread.motiondetection;

import android.location.LocationManager;
import android.location.LocationProvider;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowLocationManager;

@Implements(LocationManager.class)
public class CustomLocationManager  extends ShadowLocationManager {

    /*
     Inexplicably, robolectric 2.4 does not include an overload for getProvider which causes a
     NPE during test runs.
     */
    @Implementation
    public LocationProvider getProvider (String name) {
        return null;
    }
}
