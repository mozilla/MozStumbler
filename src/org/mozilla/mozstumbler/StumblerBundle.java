package org.mozilla.mozstumbler;

import android.location.Location;
import android.net.wifi.ScanResult;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.Parcel;

import org.mozilla.mozstumbler.cellscanner.CellInfo;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

final class StumblerBundle implements Parcelable {
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
    }

    public StumblerBundle(Location position) {
        mGpsPosition = position;
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
}
