package org.mozilla.mozstumbler;

import android.net.wifi.ScanResult;

final class SSIDBlockList {
    private static final String[] PREFIX_LIST = {
        // Mobile devices
        "ASUS",
        "AndroidAP",
        "AndroidTether",
        "CLEAR Spot",
        "CLEARSpot",
        "Clear Spot",
        "ClearSPOT",
        "ClearSpot",
        "Galaxy Note",
        "Galaxy S",
        "Galaxy Tab",
        "HTC ",
        "HelloMoto",
        "LG VS910 4G",
        "Laptop",
        "MIFI",
        "MiFi",
        "MOBILE",
        "Mobile",
        "PhoneAP",
        "SAMSUNG",
        "SCH-I",
        "SPRINT",
        "Samsung",
        "Sprint",
        "Verizon",
        "VirginMobile",
        "barnacle", // Android Barnacle Wifi Tether
        "docomo",
        "hellomoto",
        "iDockUSA",
        "iHub_",
        "iPad",
        "iPhone",
        "ipad",
        "laptop",
        "mifi",
        "mobile",
        "myLGNet",
        "myTouch 4G Hotspot",
        "samsung",
        "sprint",
        "webOS Network",

        // Transportation Wi-Fi
        "AIRBUS FREE WIFI",
        "AmtrakConnect",
        "GBUS",
        "GBusWifi",
        "SF Shuttle Wireless",
        "SST-PR-1", // Sears Home Service van hotspot?!
        "Shuttle",
        "Trimble ",
        "VTA Free Wi-Fi",
        "ac_transit_wifi_bus",
        "airbusA380",
        "amtrak_",
        "shuttle",
    };

    private static final String[] SUFFIX_LIST = {
        // Mobile devices
        " ASUS",
        "-ASUS",
        "_ASUS",
        "Laptop",
        "MIFI",
        "MacBook",
        "MacBook Pro",
        "MiFi",
        "Mifi",
        "MyWi",
        "Tether",
        "iPad",
        "iPhone",
        "ipad",
        "iphone",
        "laptop",
        "macbook",
        "mifi",
        "tether",

        // Google's SSID opt-out
        "_nomap",
    };

    private SSIDBlockList() {
    }

    static boolean contains(ScanResult scanResult) {
        String SSID = scanResult.SSID;
        if (SSID == null || SSID.length() == 0) {
            return true; // blocked!
        }

        for (String prefix : PREFIX_LIST) {
            if (SSID.startsWith(prefix)) {
                return true; // blocked!
            }
        }

        for (String suffix : SUFFIX_LIST) {
            if (SSID.endsWith(suffix)) {
                return true; // blocked!
            }
        }

        return false; // OK
    }
}
