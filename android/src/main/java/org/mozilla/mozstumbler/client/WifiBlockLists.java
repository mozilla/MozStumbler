/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client;

import org.mozilla.mozstumbler.service.stumblerthread.blocklist.WifiBlockListInterface;

public class WifiBlockLists implements WifiBlockListInterface {

    public String[] getSsidPrefixList() {
        return SSID_PREFIX_LIST;
    };

    public String[] getSsidSuffixList() {
        return SSID_SUFFIX_LIST;
    };

    public String[] getBssidOuiList() {
        return BSSID_OUI_LIST;
    };

    private static final String[] SSID_PREFIX_LIST = {
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
            "Lenovo P780_ROW",
            "LG-MS770",
            "LG-MS870",
            "LG VS910 4G",
            "LG Vortex",
            "MB526 ", // Motorola Defy Plus, matching regex "^MB526 \d{4}$"
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
            "XT320-", // Motorola Defy Mini, matching regex "^XT320-\d{4}$"
            "FirefoxHotspot",
            "Mobile Hotspot", // BlackBerry OS 10

            // Transportation Wi-Fi
            ".DeinBus.de", // DeinBus on-bus WiFi (Germany)
            "ac_transit_wifi_bus",
            "ADAC_Postbus", // ADAC Postbus on-bus WiFi (Germany)
            "Afifi", // Nazareen express transportation services (Israel)
            "AirportExpressZone", // Oslo airport express train on-train WiFi (Norway)
            "AmtrakConnect",
            "Amtrak_",
            "amtrak_",
            "Arcticbus Wifi", // Arcticbus on-bus WiFi (Sweden)
            "arriva", //Arriva Nederland on-train Wifi (Netherlands)
            "AutoPostale", // Swiss municipial busses on-bus WiFi (Italian speaking part)
            "Barcelona Bus Turistic ", // Barcelona tourisitic buses http://barcelonabusturistic.cat
            "Boreal_Kundenett", // Tromso on-boat (and probably bus) WiFi (Norway)
            "Bus4You-", // Bus4You on-bus WiFi (Norway)
            "CapitalBus", // Capital Bus on-bus WiFi (Taiwan)
            "CarPostal", // Swiss municipial busses on-bus WiFi (French speaking part)
            "CDWiFi",           // Ceske drahy (Czech railways): http://www.cd.cz/cd-online/-15765/
            "CommuteNet", // Copenhagen S-Tog on-train WiFi: http://www.dsb.dk/s-tog/kampagner/fri-internet-i-s-tog
            "csadplzen_bus",    // CSAD Plzen bus hotspots: http://www.csadplzen.cz/?ob=aktuality#wifi7
            "EMT-Madrid",  // Empresa municipal de transportes de Madrid http://buswifi.com/
            "FlixBus", // FlixBus on-bus WiFi (Germany)
            "Flybussekspressen", // Oslo airport transportation on-bus WiFi (Norway)
            "Flybussen", // Airport transportation on-bus WiFi all over Norway (Norway)
            "Flygbussarna Free WiFi ", // Flygbussarna.se on-bus WiFi (Sweden)
            "GBUS",
            "GBusWifi",
            "gogoinflight", // Gogo in-flight WiFi
            "Hot-Spot-KS", // Koleje Slaskie transportation services (Poland)
            "wifi_rail", // BART
            "egged.co.il", // Egged transportation services (Israel)
            "gb-tours.com", // GB Tours transportation services (Israel)
            "ISRAEL-RAILWAYS",
            "Kolumbus", // Stavanger public transport on-boat WiFi (Norway)
            "KOLUMBUS", // Stavanger public transport on-bus WiFi (Norway)
            "Kystbussen_Kundennett", // Kystbussen on-bus WiFi (Norway)
            "MAVSTART-WiFi", // Hungarian State Railways onboard hotspot on InterCity trains (Hungary)
            "MeinFernbus", // MeinFernbus on-bus WiFi (Germany)
            "Nateev-WiFi", // Nateev Express transportation services (Israel)
            "NationalExpress", // National Express on-bus WiFi (United Kingdom)
            "Norgesbuss", // Norgesbuss on-bus WiFi (Norway)
            "Norwegian Internet Access", // Norwegian in-flight WiFi (Norway)
            "NSB_INTERAKTIV", // NSB on-train WiFi (Norway)
            "Omni-WiFi", // Omnibus transportation services (Israel)
            "onnibus.com", // OnniBus.com Oy on-bus WiFi (Finland)
            "Oxford Tube", // Oxford Tube on-bus WiFi (United Kindom)
            "PostAuto", // Swiss municipial busses on-bus WiFi (German speaking part)
            "QbuzzWIFI", //Qbuzz on-bus WiFi (Netherlands)
            "quickshuttle_", //Quick Shuttle on-bus WiFi (United States WA/Canada BC)
            "SF Shuttle Wireless",
            "ShuttleWiFi",
            "Southwest WiFi", // Southwest Airlines in-flight WiFi
            "SST-PR-1", // Sears Home Service van hotspot?!
            "stagecoach-wifi", // Stagecoach on-bus WiFi (United Kingdom)
            "Telekom_ICE", // Deutsche Bahn on-train WiFi
            "TPE-Free Bus", // Taipei City on-bus WiFi (Taiwan)
            "THSR-VeeTIME", // Taiwan High Speed Rail on-train WiFi
            "TriangleTransitWiFi_", // Triangle Transit on-bus WiFi
            "VR-junaverkko", // VR on-train WiFi (Finland)
            "WiFi in de trein", //Nederlandse Spoorwegen on-train WiFi (Netherlands)
            "WiFi PPLUS", //Primera Plus passenger buses
            "wifi@boreal.no", // Boreal on-bus WiFi (Norway)
            "wifi@nettbuss.no", // Nettbuss on-bus WiFi (Norway)
            "first-wifi" // First Bus wifi (United Kingdom)
    };

    private static final String[] SSID_SUFFIX_LIST = {
            // Mobile devices
            "iPhone",
            "iphone",
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

    private static final String[] BSSID_OUI_LIST = {
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

}
