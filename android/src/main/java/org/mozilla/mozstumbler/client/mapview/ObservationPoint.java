/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.mapview;

import android.location.Location;

import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.mozstumbler.client.ClientPrefs;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.DataStorageContract;
import org.mozilla.mozstumbler.service.utils.NetworkUtils;
import org.osmdroid.util.GeoPoint;

public class ObservationPoint implements MLSLocationGetter.MLSLocationGetterCallback {
    public GeoPoint pointGPS;
    public GeoPoint pointMLS;
    public JSONObject mMLSQuery;

    public boolean mHasCellScan;
    public boolean mHasWifiScan;

    public ObservationPoint(GeoPoint pointGPS) {
        this.pointGPS = pointGPS;
    }

    MLSLocationGetter mMLSLocationGetter;

    public void setMLSQuery(JSONObject queryCellAndWifi) {
        mMLSQuery = queryCellAndWifi;
        try {
            mHasCellScan = mMLSQuery.getInt(DataStorageContract.ReportsColumns.CELL_COUNT) > 0;
            mHasWifiScan = mMLSQuery.getInt(DataStorageContract.ReportsColumns.WIFI_COUNT) > 0;
        } catch (JSONException ex) {}
    }

    public void fetchMLS() {
        if (mMLSQuery == null || pointMLS != null || mMLSLocationGetter != null) {
            return;
        }
        ClientPrefs prefs = ClientPrefs.getInstance();
        if (prefs.getUseWifiOnly() && !NetworkUtils.getInstance().isWifiAvailable()) {
            return;
        }
        mMLSLocationGetter = new MLSLocationGetter(this, mMLSQuery);
        mMLSLocationGetter.execute();
    }

    public boolean needsToFetchMLS() {
        return pointMLS == null;
    }

    public void setMLSResponseLocation(Location location) {
        if (location != null) {
            mMLSQuery = null;
            mMLSLocationGetter = null;
            pointMLS = new GeoPoint(location);
        }
    }
}
