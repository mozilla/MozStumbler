/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.mapview;

import android.location.Location;
import org.json.JSONObject;
import org.mozilla.mozstumbler.client.ClientPrefs;
import org.mozilla.mozstumbler.service.utils.NetworkInfo;
import org.osmdroid.util.GeoPoint;

public class ObservationPoint implements MLSLocationGetter.MLSLocationGetterCallback {
    public GeoPoint pointGPS;
    public GeoPoint pointMLS;
    public JSONObject mMLSQuery;

    public ObservationPoint(GeoPoint pointGPS) {
        this.pointGPS = pointGPS;
    }

    MLSLocationGetter mMLSLocationGetter;

    public void setMLSQuery(JSONObject ichnaeaQueryObj) {
        mMLSQuery = ichnaeaQueryObj;
    }

    public void fetchMLS() {
        if (mMLSQuery == null || pointMLS != null || mMLSLocationGetter != null) {
            return;
        }
        ClientPrefs prefs = ClientPrefs.getInstance();
        if (prefs.getUseWifiOnly() && !NetworkInfo.getInstance().isWifiAvailable()) {
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
