package org.mozilla.mozstumbler.service.stumblerthread.scanners;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;

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
       // TODO: If the client pref for simulated stumbling is on, we should expect that
       // mContext is of type SimulateStumbleContextWrapper.

       // Additionally, we can use SimulateStumbleContextWrapper.SIMULATION_PING_INTERVAL as the default interval to
       // generate new WifiData
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

    private WifiManager getWifiManager() {
        return (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
    }

    public synchronized void registerReceiver(WifiScanner wifiScanner) {
        //
        mWifiScanner = wifiScanner;

        mContext.registerReceiver(this,
                new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
    }

    public void unregisterReceiver() {
        mContext.unregisterReceiver(this);
    }

    public void onReceive(Context c, Intent intent) {

        // TODO: this is the hook we need to call to send in fake
        // Wifi signals.
        // WifiScanner will expect and intent with
        // action ==  WifiManager.SCAN_RESULTS_AVAILABLE_ACTION
        // and the getScanResults() method will then be called to return a list of
        // Wifi scans.
        mWifiScanner.onProxyReceive(c, intent);
    }

    public WifiManager.WifiLock createWifiLock() {
        return getWifiManager().createWifiLock(WifiManager.WIFI_MODE_SCAN_ONLY, "MozStumbler");
    }
}
