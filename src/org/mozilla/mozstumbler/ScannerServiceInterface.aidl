package org.mozilla.mozstumbler;

interface ScannerServiceInterface {
    boolean isScanning();
    void startScanning();
    void stopScanning();
    int numberOfReportedLocations();
}
