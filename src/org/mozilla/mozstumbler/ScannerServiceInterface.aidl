package org.mozilla.mozstumbler;

interface ScannerServiceInterface {
    boolean isScanning();
    void startScanning();
    void stopScanning();
    int getLocationCount();
    double getLatitude();
    double getLongitude();
    int getAPCount();
    int getVisibleAPCount();
    int getWifiStatus();
    int getCellInfoCount();
    int getCurrentCellInfoCount();
    boolean isGeofenced();
    void checkPrefs();
}
