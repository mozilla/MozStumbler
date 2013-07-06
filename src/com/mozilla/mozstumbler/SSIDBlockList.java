package com.mozilla.mozstumbler;

import android.net.wifi.ScanResult;

final class SSIDBlockList {
    private static final String OPTOUT_SSID_SUFFIX = "_nomap";

    private SSIDBlockList() {
    }

    static boolean contains(ScanResult scanResult) {
        String SSID = scanResult.SSID;
        return SSID == null ||
               SSID.length() == 0 ||
               SSID.endsWith(OPTOUT_SSID_SUFFIX);
    }
}
