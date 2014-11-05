package org.mozilla.mozstumbler.service.stumblerthread.scanners;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.stumblerthread.blocklist.BSSIDBlockList;

import java.util.ArrayList;
import java.util.List;

/*

 This class provides an abstraction around android.net.wifi.WifiManager
 so that we can properly mock it out and simulate inbound intents.
 */
public class WifiManagerProxy extends BroadcastReceiver {

    private final Context mContext;
    private WifiScanner mWifiScanner;

    public WifiManagerProxy(Context ctx) {
        mContext = ctx;
    }

   public boolean startScan() {
       return getWifiManager().startScan();
   }

   public List<ScanResult> getScanResults() {
        WifiManager manager = getWifiManager();
        if (manager == null) {
            return null;
        }
        return getWifiManager().getScanResults();
   }

        public boolean isWifiEnabled() {
       return getWifiManager().isWifiEnabled();
   }

    // TODO: vng - this needs to be private to make sure people aren't calling the wifimanager directly.
    private WifiManager getWifiManager() {
        return (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
    }

    public synchronized void registerReceiver(WifiScanner wifiScanner) {
        //
        mWifiScanner = wifiScanner;

        IntentFilter i = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        mContext.registerReceiver(this, i);

    }

    public void unregisterReceiver() {
        mContext.unregisterReceiver(this);
    }

    public void onReceive(Context c, Intent intent) {
        mWifiScanner.onProxyReceive(c, intent);
    }

    public WifiManager.WifiLock createWifiLock() {
        return getWifiManager().createWifiLock(WifiManager.WIFI_MODE_SCAN_ONLY, "MozStumbler");
    }
}
