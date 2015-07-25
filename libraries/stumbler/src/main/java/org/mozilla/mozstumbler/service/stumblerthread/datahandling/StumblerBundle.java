/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread.datahandling;

import android.location.Location;
import android.net.wifi.ScanResult;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.cellscanner.CellInfo;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * A StumblerBundle contains stumbling data related to a single GPS lat/long fix.
 */
public final class StumblerBundle implements Parcelable {
    /* The maximum number of Wi-Fi access points in a single observation. */
    public static final int MAX_WIFIS_PER_LOCATION = 200;
    /* The maximum number of cells in a single observation */
    public static final int MAX_CELLS_PER_LOCATION = 50;
    private final Location mGpsPosition;
    private int mTrackSegment = -1;
    private final TreeMap<String, ScanResult> mWifiData;
    private final TreeMap<String, CellInfo> mCellData;

    public StumblerBundle(Location position) {
        mGpsPosition = position;
        mWifiData = new TreeMap<String, ScanResult>();
        mCellData = new TreeMap<String, CellInfo>();
    }

    public StumblerBundle(Location position, int trackSegment) {
        this(position);
        mTrackSegment = trackSegment;
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
        out.writeInt(mTrackSegment);
    }

    public Location getGpsPosition() {
        return mGpsPosition;
    }

    public int getTrackSegment() {
        return mTrackSegment;
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

    public MLSJSONObject toMLSGeosubmit() throws JSONException {
        MLSJSONObject headerFields = new MLSJSONObject();
        headerFields.put(DataStorageConstants.ReportsColumns.LAT, Math.floor(mGpsPosition.getLatitude() * 1.0E6) / 1.0E6);
        headerFields.put(DataStorageConstants.ReportsColumns.LON, Math.floor(mGpsPosition.getLongitude() * 1.0E6) / 1.0E6);
        headerFields.put(DataStorageConstants.ReportsColumns.TIME, mGpsPosition.getTime());

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
            headerFields.put(DataStorageConstants.ReportsColumns.CELL, cellJSON);
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
            headerFields.put(DataStorageConstants.ReportsColumns.WIFI, wifis);
        }

        if (mGpsPosition.hasAltitude()) {
            headerFields.put(DataStorageConstants.ReportsColumns.ALTITUDE, (float) mGpsPosition.getAltitude());
        }
        if (mGpsPosition.hasAccuracy()) {
            // Note that Android does not support an accuracy measurement specific to altitude
            headerFields.put(DataStorageConstants.ReportsColumns.ACCURACY, mGpsPosition.getAccuracy());
        }
        return headerFields;
    }

    public JSONObject toMLSGeolocate() throws JSONException {
        JSONObject headerFields = new JSONObject();
        headerFields.put(DataStorageConstants.ReportsColumns.LAT, Math.floor(mGpsPosition.getLatitude() * 1.0E6) / 1.0E6);
        headerFields.put(DataStorageConstants.ReportsColumns.LON, Math.floor(mGpsPosition.getLongitude() * 1.0E6) / 1.0E6);
        headerFields.put(DataStorageConstants.ReportsColumns.TIME, mGpsPosition.getTime());

        /* Skip adding 'heading'

            The heading field denotes the direction of travel of the device and is specified in
            degrees, where 0° ≤ heading < 360°, counting clockwise relative to the true north.
            If the device cannot provide heading information or the device is stationary,
            the field should be omitted.

            Adding heading is tricky and problematic.  We might be able to do this by taking a delta
            between two relatively high precision locations, but I'm skeptical that the
        */

        if (mCellData.size() > 0) {
            headerFields.put(DataStorageConstants.ReportsColumns.RADIO, firstRadioType());

            JSONArray cellJSON = new JSONArray();

            for (CellInfo c : mCellData.values()) {
                JSONObject obj = c.toJSONObject();
                cellJSON.put(obj);
            }
            headerFields.put(DataStorageConstants.ReportsColumns.CELL, cellJSON);
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
            headerFields.put(DataStorageConstants.ReportsColumns.WIFI, wifis);
        }

        return headerFields;
    }

    private String firstRadioType() {
        return mCellData.firstEntry().getValue().getCellRadio();
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
