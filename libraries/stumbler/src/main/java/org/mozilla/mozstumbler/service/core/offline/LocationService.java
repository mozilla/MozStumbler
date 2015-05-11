/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.core.offline;

import android.location.Location;
import android.location.LocationManager;
import android.os.Environment;

import com.crankycoder.marisa.IntRecordTrie;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;

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

import static junit.framework.Assert.assertEquals;

public class LocationService implements IOfflineLocationService {

    private static final ILogger Log = (ILogger) ServiceLocator.getInstance().getService(ILogger.class);
    private static final String LOG_TAG = LoggerUtil.makeLogTag(LocationService.class);

    private final IntRecordTrie trie;
    private static final int BSSID_DUPLICATES = 100;

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
        File f = new File(sdcardArchivePath() +"/newmarket.record_trie");
        recordTrie.mmap(f.getAbsolutePath());
        return recordTrie;
    }
    
    @Override
    public IResponse search(JSONObject mlsGeoLocate, Map<String, String> headers, boolean precompressed) {
        MLSJSONObject mlsJson = null;
        try {
            mlsJson = new MLSJSONObject(mlsGeoLocate.toString());
        } catch (JSONException e) {
            Log.e(LOG_TAG, "Can't make a MLSJSONObject!", e);
            return null;
        }
        List<String> bssidList = mlsJson.geolocateBSSIDs();

        Log.i(LOG_TAG, "Offline location started!");

        ArrayList<Set<Integer>> trieResults = new ArrayList<Set<Integer>>();

        // Build up a list
        Log.i(LOG_TAG, "Start BSSID for offline geo");
        for (String k: bssidList) {
            trieResults.add(trie.getResultSet(k));
            Log.i(LOG_TAG, "Using BSSID = ["+k+"] ");
        }
        Log.i(LOG_TAG, "END BSSID for offline geo");

        return locationFix(493);
    }

    private IResponse locationFix(Integer tile_id) {

        // TODO: convert the tile_id to tile_x, tile_y and then run num2deg on it.
        IResponse result = null;
        double lat = 44.033613;
        double lon = -79.4905629;


        // There's only GPS, NETWORK and PASSIVE providers specified in
        // LocationManager.  Use
        Location roundTrip = new Location("mls_offline");
        roundTrip.setAccuracy(150);
        roundTrip.setLatitude(lat);
        roundTrip.setLongitude(lon);

        JSONObject jsonLocation = LocationAdapter.toJSON(roundTrip);

        result = new HTTPResponse(200,
                new HashMap<String, List<String>>(),
                jsonLocation.toString().getBytes(),
                0);
        Log.i(LOG_TAG, "Sending back location: " + jsonLocation.toString());
        return result;
    }
}
