/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread.datahandling;

import android.location.Location;
import android.net.wifi.ScanResult;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.telephony.TelephonyManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.cellscanner.CellInfo;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A StumblerBundle contains stumbling data related to a single GPS lat/long fix.
 */
public final class StumblerBundle implements Parcelable {
    /* The maximum number of Wi-Fi access points in a single observation. */
    public static final int MAX_WIFIS_PER_LOCATION = 200;
    /* The maximum number of cells in a single observation */
    public static final int MAX_CELLS_PER_LOCATION = 50;
    private final int mPhoneType;
    private final Location mGpsPosition;
    private final Map<String, ScanResult> mWifiData;
    private final Map<String, CellInfo> mCellData;

    public StumblerBundle(Location position, int phoneType) {
        mGpsPosition = position;
        mPhoneType = phoneType;
        mWifiData = new HashMap<String, ScanResult>();
        mCellData = new HashMap<String, CellInfo>();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        Bundle wifiBundle = new Bundle(ScanResult.class.getClassLoader());
        Collection<String> scans = mWifiData.keySet();
        for (String s : scans) {
            wifiBundle.putParcelable(s, mWifiData.get(s));
        }

        Bundle cellBundle = new Bundle(CellInfo.class.getClassLoader());
        Collection<String> cells = mCellData.keySet();
        for (String c : cells) {
            cellBundle.putParcelable(c, mCellData.get(c));
        }

        out.writeBundle(wifiBundle);
        out.writeBundle(cellBundle);
        out.writeParcelable(mGpsPosition, 0);
        out.writeInt(mPhoneType);
    }

    public Location getGpsPosition() {
        return mGpsPosition;
    }

    public Map<String, ScanResult> getUnmodifiableWifiData() {
        if (mWifiData == null) {
            return null;
        }
        return Collections.unmodifiableMap(mWifiData);
    }

    public Map<String, CellInfo> getUnmodifiableCellData() {
        if (mCellData == null) {
            return null;
        }
        return Collections.unmodifiableMap(mCellData);
    }

    public JSONObject toMLSGeosubmit() throws JSONException {
        JSONObject item = toMLSGeolocate();
        if (mGpsPosition.hasAltitude()) {
            item.put(DataStorageContract.ReportsColumns.ALTITUDE, (float) mGpsPosition.getAltitude());
        }
        if (mGpsPosition.hasAccuracy()) {
            // Note that Android does not support an accuracy measurement specific to altitude
            item.put(DataStorageContract.ReportsColumns.ACCURACY, (float) mGpsPosition.getAccuracy());
        }
        return item;
    }

    public JSONObject toMLSGeolocate() throws JSONException {
        JSONObject item = new JSONObject();
        item.put(DataStorageContract.ReportsColumns.LAT, Math.floor(mGpsPosition.getLatitude() * 1.0E6) / 1.0E6);
        item.put(DataStorageContract.ReportsColumns.LON, Math.floor(mGpsPosition.getLongitude() * 1.0E6) / 1.0E6);
        item.put(DataStorageContract.ReportsColumns.TIME, mGpsPosition.getTime());
        /* Skip adding 'heading'

            The heading field denotes the direction of travel of the device and is specified in
            degrees, where 0° ≤ heading < 360°, counting clockwise relative to the true north.
            If the device cannot provide heading information or the device is stationary,
            the field should be omitted.

            Adding heading is tricky and problematic.  We might be able to do this by taking a delta
            between two relatively high precision locations, but I'm skeptical that the
        */

        if (mCellData.size() > 0) {
            JSONArray cellJSON = new JSONArray();

            for (CellInfo c : mCellData.values()) {
                JSONObject obj = c.toJSONObject();
                cellJSON.put(obj);
            }
            item.put(DataStorageContract.ReportsColumns.CELL, cellJSON);
        }

        if (mWifiData.size() > 0) {
            JSONArray wifis = new JSONArray();
            for (ScanResult s : mWifiData.values()) {
                JSONObject wifiEntry = new JSONObject();
                wifiEntry.put("macAddress", s.BSSID);
                if (s.frequency != 0) {
                    wifiEntry.put("frequency", s.frequency);
                }
                if (s.level != 0) {
                    wifiEntry.put("signalStrength", s.level);
                }
                wifis.put(wifiEntry);
            }
            item.put(DataStorageContract.ReportsColumns.WIFI, wifis);
        }

        return item;
    }


    public boolean hasMaxWifisPerLocation() {
        return mWifiData.size() == MAX_WIFIS_PER_LOCATION;
    }

    public boolean hasMaxCellsPerLocation() {
        return mCellData.size() == MAX_CELLS_PER_LOCATION;
    }

    public void addWifiData(String key, ScanResult result) {
        if (mWifiData.size() == MAX_WIFIS_PER_LOCATION) {
            AppGlobals.guiLogInfo("Max wifi limit reached for this location, ignoring data.");
            return;
        }
        if (!mWifiData.containsKey(key)) {
            mWifiData.put(key, result);
        }
    }

    public void addCellData(String key, CellInfo result) {
        if (mCellData.size() > MAX_CELLS_PER_LOCATION) {
            AppGlobals.guiLogInfo("Max cell limit reached for this location, ignoring data.");
            return;
        }
        if (!mCellData.containsKey(key)) {
            mCellData.put(key, result);
        }
    }
}
