/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.mozstumbler.service.utils;

import android.location.Location;

public class CoordinateUtils {
    public static boolean isValidLocation(double latitude, double longitude) {
        return Math.abs(latitude) > 0.0001 && Math.abs(longitude) > 0.0001;
    }

    public static boolean isValidLocation(Location location) {
        return isValidLocation(location.getLatitude(), location.getLongitude());
    }
}
