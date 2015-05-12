/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.core.offline;

import android.location.Location;
import android.os.Environment;

import com.crankycoder.marisa.IntRecordTrie;
import com.google.common.primitives.Ints;

import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.mozstumbler.service.core.http.HTTPResponse;
import org.mozilla.mozstumbler.service.core.http.IResponse;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.MLSJSONObject;
import org.mozilla.mozstumbler.service.utils.LocationAdapter;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.log.ILogger;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LocationService implements IOfflineLocationService {

    private static final ILogger Log = (ILogger) ServiceLocator.getInstance().getService(ILogger.class);
    private static final String LOG_TAG = LoggerUtil.makeLogTag(LocationService.class);

    private final IntRecordTrie trie;

    private static final int BSSID_DUPLICATES = 100;
    private static final int TOTAL_POSSIBLE_TILES = 65536;

    public LocationService() {
        trie = loadTrie();
    }

    public static String sdcardArchivePath() {
        return Environment.getExternalStorageDirectory() + File.separator + "StumblerOffline";
    }


    @Override
    public IResponse submit(byte[] data, Map<String, String> headers, boolean precompressed) {
        return null;
    }

    private IntRecordTrie loadTrie() {
        String fmt = "<" + new String(new char[BSSID_DUPLICATES]).replace("\0", "i");
        IntRecordTrie recordTrie = new IntRecordTrie(fmt);
        File f = new File(sdcardArchivePath() + "/offline.record_trie");

        if (!f.exists()) {
            return null;
        }
        recordTrie.mmap(f.getAbsolutePath());
        return recordTrie;
    }

    @Override
    public IResponse search(JSONObject mlsGeoLocate, Map<String, String> headers, boolean precompressed) {
        if (trie == null) return null;
        MLSJSONObject mlsJson = null;
        try {
            mlsJson = new MLSJSONObject(mlsGeoLocate.toString());
        } catch (JSONException e) {
            Log.e(LOG_TAG, "Can't make a MLSJSONObject!", e);
            return null;
        }
        List<String> bssidList = mlsJson.geolocateBSSIDs();

        Log.i(LOG_TAG, "Offline location started!");

        int[] tile_points = new int[TOTAL_POSSIBLE_TILES];
        int max_tilept = 0;

        // Build up a list
        Log.i(LOG_TAG, "Start BSSID for offline geo");
        for (String k : bssidList) {
            Log.i(LOG_TAG, "Using BSSID = [" + k + "] ");
            for (int tile_id : trie.getResultSet(k)) {
                tile_points[tile_id] += 1;
            }
        }
        Log.i(LOG_TAG, "END BSSID for offline geo");


        max_tilept = Ints.max(tile_points);
        if (max_tilept <= 1) {
            Log.i(LOG_TAG, "Can't find any solution to this set of BSSIDS");
            return null;
        }

        Set<Integer> maxpt_tileset = new HashSet<Integer>();
        for (int i = 0; i < tile_points.length; i++) {
            if (tile_points[i] == max_tilept) {
                maxpt_tileset.add(i);
            }
        }

        if (maxpt_tileset.size() == 1) {
            Log.i(LOG_TAG, "Unique solution found: " + maxpt_tileset.toString());
            for (int tile : maxpt_tileset) {
                return locationFix(tile);
            }
        } else {
            // We have to solve a tie breaker
            // square the points for the max point array
            for (int pt : maxpt_tileset) {
                tile_points[pt] *= tile_points[pt];
            }

            int[] adj_tile_points = new int[TOTAL_POSSIBLE_TILES];

            for (int tile : maxpt_tileset) {
                int new_pts = 0;

                for (int adj_tileid : adjacent_tile(tile)) {
                    new_pts += tile_points[adj_tileid];
                }
                new_pts *= new_pts;
                adj_tile_points[tile] = new_pts;
            }

            // Copy points over
            for (int i = 0; i < adj_tile_points.length; i++) {
                tile_points[i] += adj_tile_points[i];
            }

            // Recompute the max tile
            max_tilept = Ints.max(tile_points);
            maxpt_tileset = new HashSet<Integer>();
            for (int i = 0; i < tile_points.length; i++) {
                if (tile_points[i] == max_tilept) {
                    maxpt_tileset.add(i);
                }
            }
            String msg = "Tie breaking solution: " +
                    maxpt_tileset.toString() +
                    ", " +
                    maxpt_tileset.size() +
                    " solutions were found.";

            Log.i(LOG_TAG, msg);

            for (int tile_id : maxpt_tileset) {
                return locationFix(tile_id);
            }
        }

        Log.i(LOG_TAG, "Can't find a solution");
        return null;
    }

    private List<Integer> adjacent_tile(int tile_id) {
        ArrayList<Integer> result = new ArrayList<Integer>();
        OrderedCityTiles city_tiles = new OrderedCityTiles();

        TileCoord tc = city_tiles.getCoord(tile_id);
        if (tc == null) {
            // No adjacent tiles could be found.
            Log.w(LOG_TAG, "Couldn't find adjacent tiles for tile_id=[" + tile_id + "]");
            return result;
        }
        safe_add_adjacent_tile(city_tiles, result, tc.tile_x - 1, tc.tile_y - 1);
        safe_add_adjacent_tile(city_tiles, result, tc.tile_x, tc.tile_y - 1);
        safe_add_adjacent_tile(city_tiles, result, tc.tile_x + 1, tc.tile_y - 1);

        safe_add_adjacent_tile(city_tiles, result, tc.tile_x - 1, tc.tile_y);
        safe_add_adjacent_tile(city_tiles, result, tc.tile_x + 1, tc.tile_y);

        safe_add_adjacent_tile(city_tiles, result, tc.tile_x - 1, tc.tile_y + 1);
        safe_add_adjacent_tile(city_tiles, result, tc.tile_x, tc.tile_y + 1);
        safe_add_adjacent_tile(city_tiles, result, tc.tile_x + 1, tc.tile_y + 1);

        return result;
    }

    private void safe_add_adjacent_tile(OrderedCityTiles city_tiles, ArrayList<Integer> result, int tile_x, int tile_y) {
        int tile_id = city_tiles.getTileID(new TileCoord(tile_x, tile_y));
        if (tile_id == -1) {
            Log.w(LOG_TAG, "Invalid tile lookup for tx[" + tile_x + "], ty[" + tile_y + "]");
            return;
        }
        result.add(tile_id);
    }

    private IResponse locationFix(Integer tile_id) {
        IResponse result = null;

        OrderedCityTiles city_tiles = new OrderedCityTiles();
        TileCoord coord = city_tiles.getCoord(tile_id);
        if (coord == null) {
            Log.w(LOG_TAG, "Couldn't find co-ordinates for tile_id=[" + tile_id + "]");
            return null;
        }

        // There's only GPS, NETWORK and PASSIVE providers specified in
        // LocationManager.  Use
        Location roundTrip = coord.getLocation();
        JSONObject jsonLocation = LocationAdapter.toJSON(roundTrip);

        result = new HTTPResponse(200,
                new HashMap<String, List<String>>(),
                jsonLocation.toString().getBytes(),
                0);
        Log.i(LOG_TAG, "Sending back location: " + jsonLocation.toString());
        return result;
    }
}
