/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.blocklist;

import android.net.wifi.ScanResult;
import android.util.Log;
import java.util.Locale;
import java.util.regex.Pattern;

public final class BSSIDBlockList {
    private static final String  LOG_TAG = BSSIDBlockList.class.getSimpleName();
    private static final String  NULL_BSSID        = "000000000000";
    private static final String  WILDCARD_BSSID    = "ffffffffffff";
    private static final Pattern BSSID_PATTERN     = Pattern.compile("([0-9a-f]{12})");

    private static final String[] OUI_LIST = {
        // Some iPad and iPhone OUIs:
        "001b63",
        "0021e9",
        "74e2f5",
        "78d6f0",
        "7c6d62",
        "7cc537",
        "88c663",
        "8c7712",

        // Motorola Mobility OUIs:
        "1430c6",
        "34bb26",
        "60beb5",
        "b07994",
        "ccc3ea",
        "e0757d",
        "f8e079",
        "f8f1b6",

         // Adelaide Metro WiFi Network OUIs:
        "a854b2",
    };

    private BSSIDBlockList() {
    }

    public static boolean contains(ScanResult scanResult) {
        String BSSID = scanResult.BSSID;
        if (BSSID == null || NULL_BSSID.equals(BSSID) || WILDCARD_BSSID.equals(BSSID)) {
            return true; // blocked!
        }

        if (!isCanonicalBSSID(BSSID)) {
            Log.w(LOG_TAG, "", new IllegalArgumentException("Unexpected BSSID format: " + BSSID));
            return true; // blocked!
        }

        for (String oui : OUI_LIST) {
            if (BSSID.startsWith(oui)) {
                return true; // blocked!
            }
        }

        return false; // OK
    }

    public static String canonicalizeBSSID(String BSSID) {
        if (BSSID == null) {
            return "";
        }

        if (isCanonicalBSSID(BSSID)) {
            return BSSID;
        }

        // Some devices may return BSSIDs with '-' or '.' delimiters.
        BSSID = BSSID.toLowerCase(Locale.US).replace(":", "")
                                            .replace("-", "")
                                            .replace(".", "");

        return isCanonicalBSSID(BSSID) ? BSSID : "";
    }

    private static boolean isCanonicalBSSID(String BSSID) {
        return BSSID_PATTERN.matcher(BSSID).matches();
    }
}
