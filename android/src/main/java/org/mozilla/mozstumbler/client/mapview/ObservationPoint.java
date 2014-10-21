/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.mapview;

import android.location.Location;

import com.ekito.simpleKML.model.Coordinate;

import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.mozstumbler.client.ClientPrefs;
import org.mozilla.mozstumbler.service.utils.NetworkInfo;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.DataStorageContract;
import org.osmdroid.util.GeoPoint;
import org.mozilla.mozstumbler.service.core.logging.Log;

public class ObservationPoint implements MLSLocationGetter.MLSLocationGetterCallback {
    public final GeoPoint pointGPS;
    public GeoPoint pointMLS;
    private JSONObject mMLSQuery;
    private boolean mIsMLSLocationQueryRunning;
    public long mTimestamp;
    public int mWifiCount;
    public int mCellCount;
    public boolean mWasReadFromFile;
    public double mHeading;

    public ObservationPoint(GeoPoint pointGPS) {
        this.pointGPS = pointGPS;
        mTimestamp = System.currentTimeMillis();
    }

    public ObservationPoint(Coordinate pointGPS, int wifis, int cells/*, long timestamp*/) {
        this.pointGPS = new GeoPoint(pointGPS.getLatitude(), pointGPS.getLongitude());
        mWifiCount = wifis;
        mCellCount = cells;
        /*mTimestamp = timestamp;*/
    }

    public void setMLSQuery(JSONObject ichnaeaQueryObj) {
        mMLSQuery = ichnaeaQueryObj;
    }

    public void setCounts(JSONObject ichnaeaQueryObj) {
        try {
            mCellCount = ichnaeaQueryObj.getInt(DataStorageContract.ReportsColumns.CELL_COUNT);
            mWifiCount = ichnaeaQueryObj.getInt(DataStorageContract.ReportsColumns.WIFI_COUNT);
        } catch (JSONException ex) {}
    }

    public void fetchMLS() {
        if (mMLSQuery == null || pointMLS != null || mIsMLSLocationQueryRunning) {
            return;
        }
        ClientPrefs prefs = ClientPrefs.getInstance();
        if (prefs.getUseWifiOnly() && !NetworkInfo.getInstance().isWifiAvailable()) {
            return;
        }

        mIsMLSLocationQueryRunning = true;
        new MLSLocationGetter(this, mMLSQuery).execute();
    }

    public boolean needsToFetchMLS() {
        return pointMLS == null && mMLSQuery != null;
    }

    public void setMLSResponseLocation(Location location) {
        mIsMLSLocationQueryRunning = false;

        if (location != null) {
            mMLSQuery = null; // todo decide how to persist this to kml
            pointMLS = new GeoPoint(location);
        }
    }

    public Coordinate getGPSCoordinate() {
        return new Coordinate(pointGPS.getLongitude(), pointGPS.getLatitude(), 0.0);
    }

    public Coordinate getMLSCoordinate() {
        return new Coordinate(pointMLS.getLongitude(), pointMLS.getLatitude(), 0.0);
    }

    public void setMLSCoordinate(Coordinate c) {
        pointMLS = new GeoPoint(c.getLatitude(), c.getLongitude());
    }

    public void errorMLSResponse(boolean stopRequesting) {
        if (stopRequesting) {
            mMLSQuery = null;
            Log.i(ObservationPoint.class.getSimpleName(), "Error:" + mMLSQuery.toString());
        }
        mIsMLSLocationQueryRunning = false;
    }
}
