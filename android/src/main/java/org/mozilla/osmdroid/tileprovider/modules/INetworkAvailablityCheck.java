package org.mozilla.osmdroid.tileprovider.modules;

public interface INetworkAvailablityCheck {

    boolean getNetworkAvailable();

    boolean getWiFiNetworkAvailable();

    boolean getCellularDataNetworkAvailable();

    @Deprecated
    boolean getRouteToPathExists(int hostAddress);
}
