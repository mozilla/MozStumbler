package org.mozilla.mozstumbler;

import android.util.Log;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class WifiScanner extends BroadcastReceiver {
  private static final String LOGTAG              = Scanner.class.getName();
  private static final long WIFI_MIN_UPDATE_TIME  = 1000; // milliseconds

  private boolean                mStarted;
  private final Context          mContext;
  private WifiLock               mWifiLock;
  private Timer                  mWifiScanTimer;
  private final Set<String>      mAPs = new HashSet<String>();
  private int                    mVisibleAPs;

  WifiScanner(Context c) {
    mContext = c;
    mStarted = false;
    mVisibleAPs = 0;
  }

  public void start() {
	WifiManager wm = getWifiManager();
	  
    if (mStarted || !wm.isWifiEnabled()) {
        return;
    }
    mStarted = true;

    
    mWifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_SCAN_ONLY,
                                  "MozStumbler");
    mWifiLock.acquire();

    IntentFilter i = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
    mContext.registerReceiver(this, i);

    // Ensure that we are constantly scanning for new access points.
    mWifiScanTimer = new Timer();
    mWifiScanTimer.schedule(new TimerTask() {
        @Override
        public void run() {
          Log.d(LOGTAG, "WiFi Scanning Timer fired");
          getWifiManager().startScan();
        }
      }, 0, WIFI_MIN_UPDATE_TIME);
  }

  public void stop() {
    if (mWifiLock != null) {
      mWifiLock.release();
      mWifiLock = null;
    }

    if (mWifiScanTimer != null) {
      mWifiScanTimer.cancel();
      mWifiScanTimer = null;
    }
    
    if (mStarted) {
      mContext.unregisterReceiver(this);
    }
    mStarted = false;
  }

  public void onReceive(Context c, Intent intent) {
    Collection<ScanResult> scanResults = getWifiManager().getScanResults();

    JSONArray wifiInfo = new JSONArray();
    mVisibleAPs = 0;
    for (ScanResult scanResult : scanResults) {
      scanResult.BSSID = BSSIDBlockList.canonicalizeBSSID(scanResult.BSSID);
      if (!shouldLog(scanResult)) {
        continue;
      }

      try {
        JSONObject obj = new JSONObject();
        obj.put("key", scanResult.BSSID);
        obj.put("frequency", scanResult.frequency);
        obj.put("signal", scanResult.level);
        wifiInfo.put(obj);
      } catch (JSONException jsonex) {
        Log.e(LOGTAG, "", jsonex);
      }

      mVisibleAPs += 1;
      mAPs.add(scanResult.BSSID);

      Log.v(LOGTAG, "BSSID=" + scanResult.BSSID + ", SSID=\"" + scanResult.SSID + "\", Signal=" + scanResult.level);
    }

    // No scan results to report.
    if (wifiInfo.length() == 0) {
      return;
    }

    Intent i = new Intent(ScannerService.MESSAGE_TOPIC);
    i.putExtra(Intent.EXTRA_SUBJECT, "WifiScanner");
    i.putExtra("data", wifiInfo.toString());
    i.putExtra("time", System.currentTimeMillis());
    mContext.sendBroadcast(i);
  }

  public int getAPCount() {
    return mAPs.size();
  }

  public int getVisibleAPCount() {
    return mVisibleAPs;
  }

  private static boolean shouldLog(ScanResult scanResult) {
    if (BSSIDBlockList.contains(scanResult)) {
      Log.w(LOGTAG, "Blocked BSSID: " + scanResult);
      return false;
    }
    if (SSIDBlockList.contains(scanResult)) {
      Log.w(LOGTAG, "Blocked SSID: " + scanResult);
      return false;
    }
    return true;
  }

  private WifiManager getWifiManager() {
    return (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
  }
}
