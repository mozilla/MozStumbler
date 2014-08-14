/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread.datahandling;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.mozstumbler.service.AppGlobals;

import java.io.IOException;

public final class StumblerBundleReceiver {
    private static final String LOG_TAG = AppGlobals.LOG_PREFIX + StumblerBundleReceiver.class.getSimpleName();

    public void handleBundle(StumblerBundle bundle) {
        JSONObject mlsObj;
        int wifiCount = 0;
        int cellCount = 0;
        try {
            mlsObj = bundle.toMLSJSON();
            wifiCount = mlsObj.getInt(DataStorageContract.ReportsColumns.WIFI_COUNT);
            cellCount = mlsObj.getInt(DataStorageContract.ReportsColumns.CELL_COUNT);

        } catch (JSONException e) {
            Log.w(LOG_TAG, "Failed to convert bundle to JSON: " + e);
            return;
        }

        if (AppGlobals.isDebug) {
            Log.d(LOG_TAG, "Received bundle: " + mlsObj.toString());
        }

        AppGlobals.guiLogInfo(mlsObj.toString());

        try {
            DataStorageManager.getInstance().insert(mlsObj.toString(), wifiCount, cellCount);
        } catch (IOException e) {
            Log.w(LOG_TAG, e.toString());
        }
    }
}
