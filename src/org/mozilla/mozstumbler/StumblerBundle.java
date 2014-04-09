package org.mozilla.mozstumbler;

import android.location.Location;
import android.net.wifi.ScanResult;

import org.mozilla.mozstumbler.cellscanner.CellInfo;

import java.util.HashMap;
import java.util.Map;

final class StumblerBundle {
    private final Location mGpsPosition;
    private final Map<String, ScanResult> mWifiData = new HashMap<String, ScanResult>();
    private final Map<String, CellInfo> mCellData = new HashMap<String, CellInfo>();

    public StumblerBundle(Location position) {
        mGpsPosition = position;
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
