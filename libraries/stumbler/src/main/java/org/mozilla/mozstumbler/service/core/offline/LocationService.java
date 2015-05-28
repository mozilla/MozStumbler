/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.core.offline;

import android.location.Location;
import android.location.LocationManager;
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
    public static final String ADJ_PTS = "adj_pts";
    public static final String LAT = "lat";
    public static final String LON = "lon";

    private final IntRecordTrie trie;

    private static final int ZOOM_LEVEL = 18;

    private static final int BSSID_DUPLICATES = 100;
    private static final int TOTAL_POSSIBLE_TILES = 65536;
    private final OrderedCityTiles city_tiles;

    public LocationService() {
        trie = loadTrie();
        city_tiles = new OrderedCityTiles();
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
        if (trie == null) {
            return null;
        }

        MLSJSONObject mlsJson = null;
        try {
            mlsJson = new MLSJSONObject(mlsGeoLocate.toString());
        } catch (JSONException e) {
            Log.e(LOG_TAG, "Can't make a MLSJSONObject!", e);
            return null;
        }
        List<String> bssidList = mlsJson.extractBSSIDs();

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
                Location loc = adjust_center_with_adjacent_wifi(tile, max_tilept, tile_points);
                SmartTile tile_coord = new SmartTile(loc.getLatitude(), loc.getLongitude());
                return locationFix(city_tiles.getTileID(tile_coord));
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

                // Removed this in the refactored strategy code.  squaring is not needed.
                // new_pts *= new_pts;
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

            if (maxpt_tileset.size() == 1) {
                // There is a single solution.
                for (int tile_id : maxpt_tileset) {
                    return locationFix(tile_id);
                }
            } else {
                // Add adjacent points
                // TODO: check for adjacency between 2 or more tile_ids
                // and use that as an average to figure out which single tile
                // we should return
                Log.i(LOG_TAG, "Multiple solutions- take the average: " + maxpt_tileset);

                Set<Integer> tiebreaking_set = new HashSet<Integer>();
                List<Integer> maxpt_tilelist = new ArrayList<Integer>(maxpt_tileset);
                for (int i = 0; i < maxpt_tilelist.size(); i++) {
                    Integer pt = maxpt_tilelist.get(i);

                    List<Integer> adjacent_tilelist = adjacent_tile(pt);
                    HashSet<Integer> adjacent_tileset = new HashSet<Integer>(adjacent_tilelist);
                    adjacent_tileset.retainAll(new HashSet<Integer>(maxpt_tilelist.subList(i+1, maxpt_tilelist.size())));
                    if (adjacent_tileset.size() > 0) {
                        tiebreaking_set.add(pt);
                    }
                }
                double final_lat = 0;
                double final_lon = 0;
                for (Integer tie_tileid: tiebreaking_set) {
                    SmartTile tmp_coord = city_tiles.getCoord(tie_tileid);
                    final_lat += (tmp_coord.getLocation().getLatitude() * 1.0 / tiebreaking_set.size());
                    final_lon += (tmp_coord.getLocation().getLongitude() * 1.0 / tiebreaking_set.size());
                }

                SmartTile final_coord = new SmartTile(final_lat, final_lon);
                Log.i(LOG_TAG, "Multiple solutions converging on : " + final_coord);
                return locationFix(city_tiles.getTileID(final_coord));
            }
        }

        Log.i(LOG_TAG, "Can't find a solution");
        return null;
    }

    private List<Integer> adjacent_tile(int tile_id) {
        ArrayList<Integer> result = new ArrayList<Integer>();

        SmartTile tc = city_tiles.getCoord(tile_id);
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

    /*
    This method only exists for the edges of a city. Edge tiles will have < 8 adjacent tiles.
     */
    private void safe_add_adjacent_tile(OrderedCityTiles city_tiles, ArrayList<Integer> result, int tile_x, int tile_y) {
        int tile_id = city_tiles.getTileID(new SmartTile(tile_x, tile_y));
        if (tile_id == -1) {
            Log.w(LOG_TAG, "Invalid tile lookup for tx[" + tile_x + "], ty[" + tile_y + "]");
            return;
        }
        result.add(tile_id);
    }

    private IResponse locationFix(Integer tile_id) {
        IResponse result = null;

        SmartTile coord = city_tiles.getCoord(tile_id);
        if (coord == null) {
            Log.w(LOG_TAG, "Couldn't find co-ordinates for tile_id=[" + tile_id + "]");
            return null;
        }

        Location roundTrip = coord.getLocation();
        JSONObject jsonLocation = LocationAdapter.toJSON(roundTrip);

        result = new HTTPResponse(200,
                new HashMap<String, List<String>>(),
                jsonLocation.toString().getBytes(),
                0);
        Log.i(LOG_TAG, "Sending back location: " + jsonLocation.toString());
        return result;
    }

    private Location adjust_center_with_adjacent_wifi(int center_tile_id, int center_height, int[] tile_points) {
        SmartTile coord = city_tiles.getCoord(center_tile_id);
        Location loc = coord.getLocation();
        double c_lat = loc.getLatitude();
        double c_lon = loc.getLongitude();

        Log.i(LOG_TAG, "Center is at : " + c_lat + ", " + c_lon);

        List<HashMap<String, Double>> weighted_lat_lon = new ArrayList<HashMap<String, Double>>();

        for (int adj_tileid: adjacent_tile(center_tile_id)) {
            SmartTile adj_coord = city_tiles.getCoord(adj_tileid);
            int adj_pts = tile_points[adj_tileid];
            if (adj_pts > 0) {
                Location adj_loc = adj_coord.getLocation();
                Log.i(LOG_TAG, "Extra points at: " + adj_tileid + ", " + adj_pts);
                Log.i(LOG_TAG, "Lat Lon for "+
                        adj_tileid+
                        " is " +
                        adj_loc.getLatitude() +
                        ", " +
                        adj_loc.getLongitude());

                HashMap<String, Double> v = new HashMap<String, Double>();
                v.put(ADJ_PTS, (double) adj_pts);
                v.put(LAT, adj_loc.getLatitude());
                v.put(LON, adj_loc.getLongitude());

                weighted_lat_lon.add(v);
            }
        }

        double w_lat = 0;
        double w_lon = 0;

        double total_shift_weight = 0;
        for (HashMap<String, Double> v: weighted_lat_lon) {
            w_lat += v.get(LAT) * v.get(ADJ_PTS);
            w_lon += v.get(LON) * v.get(ADJ_PTS);
            total_shift_weight += v.get(ADJ_PTS);
        }

        w_lat /= total_shift_weight;
        w_lon /= total_shift_weight;
        Log.i(LOG_TAG, "Adjacent w_lat, w_lon : " + w_lat + ", " + w_lon);

        double n_lat = c_lat * 0.5 + w_lat * 0.5;
        double n_lon = c_lon * 0.5 + w_lat * 0.5;

        // There's only GPS, NETWORK and PASSIVE providers specified in
        // LocationManager.
        Location result = new Location(LocationManager.NETWORK_PROVIDER);
        result.setLatitude(n_lat);
        result.setLongitude(n_lon);
        result.setAccuracy(150);

        Log.i(LOG_TAG, "Recomputed lat/lon: ["+result.getLatitude()+"], ["+result.getLongitude()+"]");
        return result;
    }


}
