package com.mozilla.mozstumbler;

import android.net.wifi.ScanResult;

import java.util.Locale;
import java.util.regex.Pattern;

final class BSSIDBlockList {
    private static final String NULL_BSSID = "00:00:00:00:00:00";
    private static final String WILDCARD_BSSID = "ff:ff:ff:ff:ff:ff";
    private static final Pattern BSSID_PATTERN = Pattern.compile("([0-9a-f]{2}:){5}[0-9a-f]{2}");
    private static final String AD_HOC_HEX_VALUES = "26aeAE";

    private BSSIDBlockList() {
    }

    static boolean contains(ScanResult scanResult) {
        String BSSID = scanResult.BSSID;
        return BSSID == null ||
               isAdHocBSSID(BSSID) ||
               !isCanonicalBSSID(BSSID) ||
               NULL_BSSID.equals(BSSID) ||
               WILDCARD_BSSID.equals(BSSID);
    }

    static String canonicalizeBSSID(String BSSID) {
        if (BSSID == null || isCanonicalBSSID(BSSID)) {
            return BSSID;
        }

        // Some devices may return BSSIDs with '-' or '.' delimiters.
        BSSID = BSSID.toLowerCase(Locale.US).replace('-', ':').replace('.', ':');
        return isCanonicalBSSID(BSSID) ? BSSID : null;
    }

    private static boolean isCanonicalBSSID(String BSSID) {
        return BSSID_PATTERN.matcher(BSSID).matches();
    }

    private static boolean isAdHocBSSID(String BSSID) {
        // We filter out any ad-hoc devices. Ad-hoc devices are identified by
        // having a 2,6,a or e in the second nybble.
        // See http://en.wikipedia.org/wiki/MAC_address -- ad hoc networks
        // have the last two bits of the second nybble set to 10.
        char secondNybble = BSSID.charAt(1);
        return AD_HOC_HEX_VALUES.indexOf(secondNybble) != -1;
    }
}
