/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.mozstumbler.client.leaderboard;

import android.content.Context;
import android.location.Location;

import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.base.JSONRowsStorageManager;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.DataStorageConstants;

import java.util.HashMap;

class LBDataStorage extends JSONRowsStorageManager {

    private static final String LOG_TAG = "LBDataStorage";
    public static final String KEY_GRID = "grid";
    public static final String KEY_CELL = "cellcount";
    public static final String KEY_WIFI = "wificount";

    private HashMap<String, JSONObject> mGridToRow = new HashMap<String, JSONObject>();
    // mGridToRow applies only to the current in memory buffer, when this buffer changes it no longer applies
    // the following is used to track this
    private Object mCurrentObjForGridToRow = new Object();

    LBDataStorage(Context c) {
        super(c, null, DataStorageConstants.DEFAULT_MAX_BYTES_STORED_ON_DISK,
                DataStorageConstants.DEFAULT_MAX_WEEKS_DATA_ON_DISK, "/leaderboard");
    }

    private void incrementJSON(JSONObject json, String key, int value) throws JSONException {
        json.put(key, (Integer) json.get(key) + value);
    }

    public void insert(Location location, int cellCount, int wifiCount) {
        String grid = locationToGrid(location);

        JSONObject prev = findRowWithMatchingGrid(grid);
        try {
            if (prev != null) {
                incrementJSON(prev, KEY_CELL, cellCount);
                incrementJSON(prev, KEY_WIFI, wifiCount);
                return;
            }

            JSONObject json = new JSONObject();
            json.put(KEY_GRID, grid);
            json.put(KEY_CELL, cellCount);
            json.put(KEY_WIFI, wifiCount);
            insertRow(json);

            if (mCurrentObjForGridToRow != mInMemoryActiveJSONRows) {
                mCurrentObjForGridToRow = mInMemoryActiveJSONRows;
                mGridToRow = new HashMap<String, JSONObject>();
            }
            mGridToRow.put(grid, json);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private JSONObject findRowWithMatchingGrid(String grid) {
        if (!mGridToRow.containsKey(grid)) {
            return null;
        }
        return mGridToRow.get(grid);
    }

    private String locationToGrid(Location location) {
        //Code from here, http://wiki.openstreetmap.org/wiki/Mercator,spherical world mercator (not elliptical)
        final double earthRadius = 6378137.000;

        // get northing, easting of lower left of grid cell
        double northing = earthRadius * Math.log(Math.tan(Math.PI / 4.0 +
                Math.toRadians(location.getLatitude()) / 2.0));

        double easting = Math.toRadians(location.getLongitude()) * earthRadius;

        // round down to grid lower left, 500m increments
        northing = Math.floor(northing / 1000.0 * 2) / 2.0 * 1000;
        easting = Math.floor(easting / 1000.0 * 2) / 2.0 * 1000;

        // bump up by small amount towards center of cell (to avoid being on the edge)
        northing += 100;
        easting += 100;

        String grid = String.format("%.4f,%.4f", easting, northing);
        // 4th decimal is ~10 meters
        return grid;
    }
}
