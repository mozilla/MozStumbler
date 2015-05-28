/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread.datahandling;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.log.ILogger;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

import java.util.ArrayList;
import java.util.List;

/*
 This subclass of JSONObject provides additional getters - and only getters for convenient access
 to bits of data that are relevant to the Ichnaea JSON specification.
 */
public class MLSJSONObject extends JSONObject {

    private final static ILogger Log = (ILogger) ServiceLocator.getInstance().getService(ILogger.class);
    private final static String LOG_TAG = LoggerUtil.makeLogTag(MLSJSONObject.class);


    public MLSJSONObject() {
        super();
    }

    public MLSJSONObject(String s) throws JSONException {
        super(s);
    }

    public int radioCount() {
        int result = 0;
        result += getWifiCount();
        result += getCellCount();
        return result;
    }

    public int getWifiCount() {
        JSONArray wifiRecords = this.optJSONArray(DataStorageConstants.ReportsColumns.WIFI);
        return (wifiRecords == null ? 0 : wifiRecords.length());
    }

    public int getCellCount() {
        JSONArray cellRecords = this.optJSONArray(DataStorageConstants.ReportsColumns.CELL);
        return (cellRecords == null ? 0 : cellRecords.length());
    }

    public List<String> extractBSSIDs() {
        JSONArray wifiRecords = this.optJSONArray(DataStorageConstants.ReportsColumns.WIFI);
        List<String> result = new ArrayList<String>();
        if (wifiRecords != null) {
            for (int i = 0; i < wifiRecords.length(); i++) {
                try {
                    String bssid = wifiRecords.getJSONObject(i).getString("macAddress");
                    result.add(bssid);
                } catch (JSONException e) {
                    Log.e(LOG_TAG, "Error deserializing wifi BSSID data", e);
                }
            }
        }

        return result;
    }
}
