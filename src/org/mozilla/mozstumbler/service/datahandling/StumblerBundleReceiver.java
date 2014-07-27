/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.datahandling;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.mozstumbler.service.AppGlobals;

import java.io.IOException;

public final class StumblerBundleReceiver {
    private static final String LOGTAG = StumblerBundleReceiver.class.getName();

    public void handleBundle(StumblerBundle bundle) {
        JSONObject mlsObj;
        int wifiCount = 0;
        int cellCount = 0;
        try {
            mlsObj = bundle.toMLSJSON();
            wifiCount = mlsObj.getInt(DataStorageContract.ReportsColumns.WIFI_COUNT);
            cellCount = mlsObj.getInt(DataStorageContract.ReportsColumns.CELL_COUNT);

        } catch (JSONException e) {
            Log.w(LOGTAG, "Failed to convert bundle to JSON: " + e);
            return;
        }
            if (AppGlobals.isDebug) Log.d(LOGTAG, "Received bundle: " + mlsObj.toString());

            AppGlobals.guiLogInfo(mlsObj.toString());

        try {

            AppGlobals.dataStorageManager.insert(mlsObj.toString(), wifiCount, cellCount);
        } catch (IOException e) {
            Log.w(LOGTAG, e.toString());
        }
    }
}
