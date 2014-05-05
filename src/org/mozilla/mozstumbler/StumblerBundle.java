package org.mozilla.mozstumbler;

import android.location.Location;
import android.net.wifi.ScanResult;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.telephony.TelephonyManager;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.mozstumbler.cellscanner.CellInfo;

final class StumblerBundle implements Parcelable {
    private final int mPhoneType;
    private final Location mGpsPosition;
    private final Map<String, ScanResult> mWifiData;
    private final Map<String, CellInfo> mCellData;

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

    public static final Parcelable.Creator<StumblerBundle> CREATOR
        = new Parcelable.Creator<StumblerBundle>() {
        @Override
        public StumblerBundle createFromParcel(Parcel in) {
            return new StumblerBundle(in);
        }

        @Override
        public StumblerBundle[] newArray(int size) {
            return new StumblerBundle[size];
        }
    };

    private StumblerBundle(Parcel in) {
        mWifiData = new HashMap<String, ScanResult>();
        mCellData = new HashMap<String, CellInfo>();

        Bundle wifiBundle = in.readBundle(ScanResult.class.getClassLoader());
        Bundle cellBundle = in.readBundle(CellInfo.class.getClassLoader());

        Collection<String> scans = wifiBundle.keySet();
        for (String s : scans) {
            mWifiData.put(s, (ScanResult) wifiBundle.get(s));
        }

        Collection<String> cells = cellBundle.keySet();
        for (String c : cells) {
            mCellData.put(c, (CellInfo) cellBundle.get(c));
        }

        mGpsPosition = in.readParcelable(Location.class.getClassLoader());
        mPhoneType = in.readInt();
    }

    public StumblerBundle(Location position, int phoneType) {
        mGpsPosition = position;
        mPhoneType = phoneType;
        mWifiData = new HashMap<String, ScanResult>();
        mCellData = new HashMap<String, CellInfo>();
    }

    public Location getGpsPosition() {
        return mGpsPosition;
    }

    public Map<String, ScanResult> getWifiData() {
        return mWifiData;
    }

    public Map<String, CellInfo> getCellData() {
        return mCellData;
    }

    public JSONObject toMLSJSON() throws JSONException {
        JSONObject item = new JSONObject();

        item.put("time", mGpsPosition.getTime());
        item.put("lat", Math.floor(mGpsPosition.getLatitude() * 1.0E6) / 1.0E6);
        item.put("lon", Math.floor(mGpsPosition.getLongitude() * 1.0E6) / 1.0E6);

        if (mGpsPosition.hasAccuracy()) {
            item.put("accuracy", (int) Math.ceil(mGpsPosition.getAccuracy()));
        }

        if (mGpsPosition.hasAltitude()) {
            item.put("altitude", Math.round(mGpsPosition.getAltitude()));
        }

        if (mPhoneType == TelephonyManager.PHONE_TYPE_GSM ||
            mPhoneType == TelephonyManager.PHONE_TYPE_CDMA) {

            item.put("radio", (mPhoneType == TelephonyManager.PHONE_TYPE_GSM) ? "gsm" : "cdma");
            JSONArray cells = new JSONArray();
            item.put("cell", cells);
            for (CellInfo c : mCellData.values()) {
               cells.put(c.toJSONObject());
            }
        }

        JSONArray wifis = new JSONArray();
        item.put("wifi", wifis);
        for (ScanResult s : mWifiData.values()) {
            JSONObject wifiEntry = new JSONObject();
            wifiEntry.put("key", s.BSSID);
            wifiEntry.put("frequency", s.frequency);
            wifiEntry.put("signal", s.level);
            wifis.put(wifiEntry);
        }

        return item;
    }
}
