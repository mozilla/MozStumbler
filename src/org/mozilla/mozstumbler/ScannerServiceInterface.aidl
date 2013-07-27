package org.mozilla.mozstumbler;

interface ScannerServiceInterface {
    boolean isScanning();
    void startScanning();
    void stopScanning();
    
    void showNotification();
    
    int numberOfReportedLocations();
}
