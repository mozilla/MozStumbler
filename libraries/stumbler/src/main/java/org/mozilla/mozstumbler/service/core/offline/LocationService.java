/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.core.offline;

import android.os.Environment;

import com.crankycoder.marisa.IntRecordTrie;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;

import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.mozstumbler.service.core.http.IResponse;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.MLSJSONObject;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.log.ILogger;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LocationService implements IOfflineLocationService {

    private static final ILogger Log = (ILogger) ServiceLocator.getInstance().getService(ILogger.class);
    private static final String LOG_TAG = LoggerUtil.makeLogTag(LocationService.class);

    private final IntRecordTrie trie;

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
        String fmt = "<" + new String(new char[100]).replace("\0", "i");
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
        Set<Integer> result = null;

        ArrayList<Set<Integer>> trieResults = new ArrayList<Set<Integer>>();

        // Build up a list
        Log.i(LOG_TAG, "Start BSSID for offline geo");
        for (String k: bssidList) {
            trieResults.add(trie.getResultSet(k));
            Log.i(LOG_TAG, "Using BSSID = ["+k+"] ");
        }
        Log.i(LOG_TAG, "END BSSID for offline geo");


        Set<Integer> smallest = null;
        int[] smallestBssidIndexes = null;

        Set<Integer> tmp = null;

        // Try to match any 3 BSSIDs together in the trie
        for (int i = 0; i < bssidList.size()-2; i++) {
            for (int j = i+1; j < bssidList.size()-1; j++) {
                for (int k = j+1; k < bssidList.size(); k++) {
                    Set<Integer> t1 = trieResults.get(i);
                    Set<Integer> t2 = trieResults.get(j);
                    Set<Integer> t3 = trieResults.get(k);

                    tmp = new HashSet<Integer>(t1);
                    tmp.retainAll(t2);
                    if (tmp.size() > 0 && tmp.size() > 0 && tmp.size() < smallest.size()) {
                        smallest = tmp;
                        smallestBssidIndexes = new int[]{i, j};
                    }
                    tmp = new HashSet<Integer>(t2);
                    tmp.retainAll(t3);
                    if (tmp.size() > 0 && tmp.size() > 0 && tmp.size() < smallest.size()) {
                        smallest = tmp;
                        smallestBssidIndexes = new int[]{j, k};
                    }
                    tmp = new HashSet<Integer>(t1);
                    tmp.retainAll(t2);
                    if (tmp.size() > 0 && tmp.size() > 0 && tmp.size() < smallest.size()) {
                        smallest = tmp;
                        smallestBssidIndexes = new int[]{i, k};
                    }
                    tmp = new HashSet<Integer>(t1);
                    tmp.retainAll(t2);
                    tmp.retainAll(t3);
                    if (tmp.size() > 0 && tmp.size() > 0 && tmp.size() < smallest.size()) {
                        smallest = tmp;
                        smallestBssidIndexes = new int[]{i, j, k};
                    }

                    if (smallest.size() == 1) { break; }
                }
                if (smallest.size() == 1) { break; }
            }
            if (smallest.size() == 1) { break; }
        }

        if (smallest == null || smallest.size() == 100) {
            // No fix could be found at all.
            return nolocationFound();
        }

        if (smallest.size() == 1) {
            // We have a solid fix with 3 BSSIDs.
            return locationFix(Iterables.get(smallest, 0));
        }

        if (smallest.size() < 100) {
            // Ok, we have a partial fix.  Let's try to narrow it down by cross referencing the
            // adjacent cells each of the matches against disjoint matchsets
            // TODO:
            Log.i(LOG_TAG, "Partial fix found: " + Joiner.on(", ").join(smallest));
            Integer bestGuess = new Integer(0);
            return locationFix(bestGuess);
        }

        return null;
    }

    private IResponse nolocationFound() {
        // TODO: return a no location found response
        Log.i(LOG_TAG, "No matches found!");
        return null;
    }

    private IResponse locationFix(Integer integer) {
        IResponse result = null;
        // TODO: make a working location fix from a tile Id
        Log.i(LOG_TAG, "Found a match: " + integer);
        return result;
    }
}
