/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.core.offline;

import android.location.Location;
import android.location.LocationManager;

public class TileCoord {
    private static final double ZOOM = 18;
    public final int tile_x;
    public final int tile_y;

    public TileCoord(int x, int y) {
        tile_x = x;
        tile_y = y;
    }

    public TileCoord(String line) {
        //Get all tokens available in line
        String[] tokens = line.split(",");
        tokens[0] = tokens[0].replaceAll("\\s+", "");
        tokens[1] = tokens[1].replaceAll("\\s+", "");

        tile_x = Integer.parseInt(tokens[0]);
        tile_y = Integer.parseInt(tokens[1]);
    }

    public String toString() {
        return Integer.toString(tile_x) + "," + Integer.toString(tile_y);
    }

    public Location getLocation() {
        Location location = new Location(LocationManager.NETWORK_PROVIDER);
        location.setAccuracy(150);
        location.setLatitude(tile2lat());
        location.setLongitude(tile2lon());
        return location;
    }

    double tile2lon() {
        return tile_x / Math.pow(2.0, ZOOM) * 360.0 - 180;
    }

    double tile2lat() {
        double n = Math.PI - (2.0 * Math.PI * tile_y) / Math.pow(2.0, ZOOM);
        return Math.toDegrees(Math.atan(Math.sinh(n)));
    }
}
