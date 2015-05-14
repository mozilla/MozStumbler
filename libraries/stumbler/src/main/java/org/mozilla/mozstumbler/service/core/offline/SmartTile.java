/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.core.offline;

import android.location.Location;
import android.location.LocationManager;

/*
 This is a convenience class which can convert to and from slippy tiles coordinates to lat/lon
 by returning a Location object.
 */
public class SmartTile {
    private static final double ZOOM = 18;
    public final int tile_x;
    public final int tile_y;

    /*
     Create a tile based on lat/lon
     */
    public SmartTile(double lat, double lon) {
        final int zoom = (int) ZOOM;
        int xtile = (int) Math.floor((lon + 180) / 360 * (1 << zoom));
        int ytile = (int) Math.floor((1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1 << zoom));
        if (xtile < 0)
            xtile = 0;
        if (xtile >= (1 << zoom))
            xtile = ((1 << zoom) - 1);
        if (ytile < 0)
            ytile = 0;
        if (ytile >= (1 << zoom))
            ytile = ((1 << zoom) - 1);
        tile_x = xtile;
        tile_y = ytile;
    }

    /*
     Create a tile based on x/y co-ordinates
     */
    public SmartTile(int x, int y) {
        tile_x = x;
        tile_y = y;
    }

    public SmartTile(String line) {
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
