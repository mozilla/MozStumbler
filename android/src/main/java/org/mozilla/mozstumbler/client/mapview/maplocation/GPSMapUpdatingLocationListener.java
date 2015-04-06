/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.mapview.maplocation;

import android.location.LocationManager;

import org.mozilla.mozstumbler.client.mapview.MapFragment;

public class GPSMapUpdatingLocationListener extends MapUpdatingLocationListener {
    public GPSMapUpdatingLocationListener(MapFragment map, ReceivedLocationCallback callback) {
        super(map, LocationManager.GPS_PROVIDER, 1000, callback);
    }
}
