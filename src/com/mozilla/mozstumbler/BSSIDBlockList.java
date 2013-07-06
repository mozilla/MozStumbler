package com.mozilla.mozstumbler;

import android.net.wifi.ScanResult;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

final class BSSIDBlockList {
    private static final String NULL_BSSID = "00:00:00:00:00:00";
    private static final String WILDCARD_BSSID = "ff:ff:ff:ff:ff:ff";

    // copied from
    // http://code.google.com/p/sensor-data-collection-library/source/browse/src/main/java/TextFileSensorLog.java#223,
    // which is Apache licensed.
    private static final Set<Character> AD_HOC_HEX_VALUES = new HashSet<Character>(
            Arrays.asList('2', '6', 'a', 'e', 'A', 'E'));

    private BSSIDBlockList() {
    }

    static boolean contains(ScanResult scanResult) {
        String BSSID = scanResult.BSSID;
        return BSSID == null ||
               isAdHocBSSID(BSSID) ||
               NULL_BSSID.equals(BSSID) ||
               WILDCARD_BSSID.equals(BSSID);
    }

    private static boolean isAdHocBSSID(String BSSID) {
        // We filter out any ad-hoc devices. Ad-hoc devices are identified by
        // having a 2,6,a or e in the second nybble.
        // See http://en.wikipedia.org/wiki/MAC_address -- ad hoc networks
        // have the last two bits of the second nybble set to 10.
        // Only apply this test if we have exactly 17 character long BSSID which
        // should be the case.
        char secondNybble = BSSID.charAt(1);
        return AD_HOC_HEX_VALUES.contains(secondNybble);
    }
}
