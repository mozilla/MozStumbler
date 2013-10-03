package org.mozilla.mozstumbler;

import android.net.wifi.ScanResult;

final class SSIDBlockList {
    private static final String[] PREFIX_LIST = {
        // Mobile devices
        "ASUS",
        "asus",
        "AndroidAP",
        "AndroidHotspot",
        "Android Hotspot",
        "barnacle",
        "Galaxy Note",
        "Galaxy S",
        "Galaxy Tab",
        "HTC Amaze 4G",
        "HTC EVO Shift 4G",
        "HTC Media Link",
        "HTC One S",
        "HTC Portable Hotspot",
        "HTC Radar 4G",
        "HTC Sensation 4G",
        "HTC Vivid",
        "iPad",
        "iPhone",
        "LAPTOP",
        "Laptop",
        "laptop",
        "LG-MS770",
        "LG-MS870",
        "LG VS910 4G",
        "LG Vortex",
        "MIFI",
        "MiFi",
        "myLGNet",
        "myTouch 4G Hotspot",
        "PhoneAP",
        "SCH-I1",
        "SCH-I2",
        "SCH-I3",
        "SCH-I4",
        "SCH-I5",
        "SCH-I6",
        "SCH-I7",
        "SCH-I8",
        "SCH-I9",
        "Sprint MiFi",
        "Verizon AC30",
        "Verizon ADR",
        "Verizon DROID",
        "Verizon MIFI",
        "Verizon MiFi",
        "Verizon SCH",
        "Verizon-ADR",
        "Verizon-MiFi",
        "Verizon-890L",
        "VirginMobile MiFi",

        // Transportation Wi-Fi
        "ac_transit_wifi_bus",
        "AmtrakConnect",
        "Amtrak_",
        "amtrak_",
        "GBUS",
        "GBusWifi",
        "SF Shuttle Wireless",
        "ShuttleWiFi",
        "SST-PR-1", // Sears Home Service van hotspot?!
        "wifi_rail", // BART
    };

    private static final String[] SUFFIX_LIST = {
        // Mobile devices
        " ASUS",
        "_ASUS",
        "-ASUS",
        "iPad",
        "iPhone",
        "iphone",
        "Laptop",
        "LAPTOP_Network",
        "Laptop-Wireless",
        "laptop",
        "laptop_network",
        "MIFI",
        "MacBook Air",
        "MacBook Air (2)",
        "MacBook",
        "MacBook (2)",
        "MacBook Pro",
        "MacBook Pro (2)",
        "macbook",
        "MacBookPro",
        "macbookpro",
        "MIFI",
        "MiFi",
        "Mifi",
        "mifi",
        "mi-fi",
        "MyWi",
        "Phone",
        "Portable Hotspot",
        "Tether",
        "tether",

        // Google's SSID opt-out
        "_nomap",
    };

    private SSIDBlockList() {
    }

    static boolean contains(ScanResult scanResult) {
        String SSID = scanResult.SSID;
        if (SSID == null) {
            return true; // no SSID?
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
