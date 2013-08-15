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
        "Ericsson",
        "Galaxy Note",
        "Galaxy S",
        "Galaxy Tab",
        "HTC ",
        "HelloMoto",
        "LG VS910 4G",
        "Laptop",
        "MIFI",
        "MiFi",
        "PhoneAP",
        "SAMSUNG",
        "SCH-I",
        "SPRINT",
        "Samsung",
        "Sprint",
        "Verizon",
        "VirginMobile MiFi2200",
        "WaveLAN Network",
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
        "myLGNet",
        "myTouch 4G Hotspot",
        "samsung",
        "sprint",
        "webOS Network",

        // Transportation Wi-Fi
        "GBUS",
        "GBusWifi",
        "SF Shuttle Wireless",
        "SST-PR-1", // Sears Home Service van hotspot?!
        "Shuttle",
        "Trimble ",
        "VTA Free Wi-Fi", // VTA light rail
        "ac_transit_wifi_bus",
        "airbusA380",
        "amtrak_",
        "shuttle",

        // "Viral" SSIDs that infect some Windows laptops
        "Adhoc",
        "Free Internet Access",
        "Free Internet!",
        "Jet Blue hot spot",
        "US Airways Free WiFi",
        "adhoc",
        "hpsetup",
        "tsunami",
    };

    private static final String[] SUFFIX_LIST = {
        // Mobile devices
        " ASUS",
        "-ASUS",
        "Laptop",
        "MacBook",
        "MacBook Pro",
        "MIFI",
        "MiFi",
        "Mifi",
        "MyWi",
        "Tether",
        "_ASUS",

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
