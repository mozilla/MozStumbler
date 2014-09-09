package org.mozilla.mozstumbler.client.mapview;

import android.location.Location;
import org.json.JSONObject;
import org.mozilla.mozstumbler.client.ClientPrefs;
import org.mozilla.mozstumbler.service.utils.NetworkUtils;
import org.osmdroid.util.GeoPoint;

public class ObservationPoint implements MLSLocationGetter.MLSLocationGetterCallback {
    public GeoPoint pointGPS;
    public GeoPoint pointMLS;
    public JSONObject mMLSQuery;

    public ObservationPoint(GeoPoint pointGPS) {
        this.pointGPS = pointGPS;
    }

    MLSLocationGetter mMLSLocationGetter;

    public void setMLSQuery(JSONObject queryCellAndWifi) {
        mMLSQuery = queryCellAndWifi;
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
