/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.core.offline;

import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.log.ILogger;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

public class OrderedCityTiles {

    private static ILogger Log = (ILogger) ServiceLocator.getInstance().getService(ILogger.class);
    private static final String LOG_TAG = LoggerUtil.makeLogTag(OrderedCityTiles.class);

    private final HashMap<String, Integer> CoordToTileID = new HashMap<String, Integer>();
    private final HashMap<Integer, String> TileIDToCoord = new HashMap<Integer, String>();

    public OrderedCityTiles() {
        parse_csv();
    }

    private void parse_csv() {

        BufferedReader fileReader = null;
        try {
            fileReader = new BufferedReader(new FileReader(LocationService.sdcardArchivePath()+"/ordered_city.csv"));
        } catch (FileNotFoundException e) {
            Log.e(LOG_TAG, "Can't open file", e);
        }

        //Read the file line by line
        String line;
        int lineNum = 0;
        try {
            while ((line = fileReader.readLine()) != null) {
                SmartTile tc = new SmartTile(line);
                CoordToTileID.put(tc.toString(), lineNum);
                TileIDToCoord.put(lineNum, tc.toString());
                lineNum += 1;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int getTileID(SmartTile tc) {
        String key = tc.toString();
        if (!CoordToTileID.containsKey(key)) {
            return -1;
        }
        return CoordToTileID.get(tc.toString());
    }

    public SmartTile getCoord(int tile_id) {
        if (!TileIDToCoord.containsKey(tile_id)) {
            return null;
        }
        return new SmartTile(TileIDToCoord.get(tile_id));
    }
}
