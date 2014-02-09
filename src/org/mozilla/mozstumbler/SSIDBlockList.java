package org.mozilla.mozstumbler;

import android.net.wifi.ScanResult;

final class SSIDBlockList {
    private static final String[] PREFIX_LIST = {
        // Mobile devices
        "AndroidAP",
        "AndroidHotspot",
        "Android Hotspot",
        "barnacle", // Android tether app
        "Galaxy Note",
        "Galaxy S",
        "Galaxy Tab",
        "HTC ",
        "iPhone",
        "LG-MS770",
        "LG-MS870",
        "LG VS910 4G",
        "LG Vortex",
        "MIFI",
        "MiFi",
        "myLGNet",
        "myTouch 4G Hotspot",
        "NOKIA Lumia",
        "PhoneAP",
        "SCH-I",
        "Sprint MiFi",
        "Verizon ",
        "Verizon-",
        "VirginMobile MiFi",
        "VodafoneMobileWiFi-",
        "FirefoxHotspot",
        "Mobile Hotspot", // BlackBerry OS 10

        // Transportation Wi-Fi
        "ac_transit_wifi_bus",
        "AmtrakConnect",
        "Amtrak_",
        "amtrak_",
        "GBUS",
        "GBusWifi",
        "gogoinflight", // Gogo in-flight WiFi
        "SF Shuttle Wireless",
        "ShuttleWiFi",
        "Southwest WiFi", // Southwest Airlines in-flight WiFi
        "SST-PR-1", // Sears Home Service van hotspot?!
        "wifi_rail", // BART
        "egged.co.il", // Egged transportation services (Israel)
        "gb-tours.com", // GB Tours transportation services (Israel)
        "ISRAEL-RAILWAYS",
        "Omni-WiFi", // Omnibus transportation services (Israel)
        "Telekom_ICE", // Deutsche Bahn on-train WiFi
        "TPE-Free Bus", // Taipei City on-bus WiFi (Taiwan)
        "THSR-VeeTIME", // Taiwan High Speed Rail on-train WiFi
        "CapitalBus", // Capital Bus on-bus WiFi (Taiwan)
        "Hot-Spot-KS", // Koleje Slaskie transportation services (Poland)
    };

    private static final String[] SUFFIX_LIST = {
        // Mobile devices
        "iPhone",
        "iphone",
        "MIFI",
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
