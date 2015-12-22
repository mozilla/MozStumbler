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
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

import java.util.HashMap;

// TODO: change this to use delegation instead of subclassing
class LBDataStorage extends JSONRowsStorageManager {

    private static final String LOG_TAG = LoggerUtil.makeLogTag(LBDataStorage.class);

    // TODO split the easting and northing into separate bits
    public static final String KEY_TILE_EASTING_COORD = "tile_easting_m";
    public static final String KEY_TILE_NORTHING_COORD = "tile_northing_m";

    public static final String KEY_OBSERVATIONS = "observations";
    private static final String TIME_KEY = "time";

    private HashMap<String, JSONObject> mGridToRow = new HashMap<String, JSONObject>();
    // mGridToRow applies only to the current in memory buffer, when this buffer changes it no longer applies
    // the following is used to track this
    private Object mCurrentObjForGridToRow = new Object();

    public LBDataStorage(Context c) {
        super(c, null, DataStorageConstants.DEFAULT_MAX_BYTES_STORED_ON_DISK,
                DataStorageConstants.DEFAULT_MAX_WEEKS_DATA_ON_DISK, "/leaderboard");
    }

    private void incrementJSON(JSONObject json, String key, int value) throws JSONException {
        json.put(key, (Integer) json.get(key) + value);
    }

    public void insert(Location location) {
        long easting = locationToEasting(location);
        long northing = locationToNorthing(location);

        String locationHashKey = locationToEastingNorthing(location);

        JSONObject prev = findRowWithMatchingGrid(locationHashKey);
        try {
            if (prev != null) {
                incrementJSON(prev, KEY_OBSERVATIONS, 1);
                Log.i(LOG_TAG, "Updated leaderboard JSON: " + prev);
                return;
            }

            JSONObject json = new JSONObject();
            json.put(TIME_KEY, System.currentTimeMillis() / 1000L);
            json.put(KEY_TILE_EASTING_COORD, easting);
            json.put(KEY_TILE_NORTHING_COORD, northing);
            json.put(KEY_OBSERVATIONS, 1);
            insertRow(json);

            if (mCurrentObjForGridToRow != mInMemoryActiveJSONRows) {
                mCurrentObjForGridToRow = mInMemoryActiveJSONRows;
                mGridToRow = new HashMap<String, JSONObject>();
            }
            Log.i(LOG_TAG, "Created leaderboard JSON: " + json);
            mGridToRow.put(locationHashKey, json);
        } catch (JSONException e) {
            Log.e(LOG_TAG, e.toString());
        }
    }

    private JSONObject findRowWithMatchingGrid(String grid) {
        if (!mGridToRow.containsKey(grid)) {
            return null;
        }
        return mGridToRow.get(grid);
    }

    private String locationToEastingNorthing(Location location) {
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


    private long locationToNorthing(Location location) {
        //Code from here, http://wiki.openstreetmap.org/wiki/Mercator,spherical world mercator (not elliptical)
        final double earthRadius = 6378137.000;

        // get northing of lower left of grid cell
        double northing = earthRadius * Math.log(Math.tan(Math.PI / 4.0 +
                Math.toRadians(location.getLatitude()) / 2.0));

        // round down to grid lower left, 500m increments
        northing = Math.floor(northing / 1000.0 * 2) / 2.0 * 1000;

        // bump up by small amount towards center of cell (to avoid being on the edge)
        northing += 100;

        return Math.round(northing);
    }


    private long locationToEasting(Location location) {
        //Code from here, http://wiki.openstreetmap.org/wiki/Mercator,spherical world mercator (not elliptical)
        final double earthRadius = 6378137.000;

        // get easting of lower left of grid cell
        double easting = Math.toRadians(location.getLongitude()) * earthRadius;

        // round down to grid lower left, 500m increments
        easting = Math.floor(easting / 1000.0 * 2) / 2.0 * 1000;

        // bump up by small amount towards center of cell (to avoid being on the edge)
        easting += 100;

        return Math.round(easting);
    }

}
