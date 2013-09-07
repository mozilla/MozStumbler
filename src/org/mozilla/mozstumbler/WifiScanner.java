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
  private final MessageDigest    mSHA1;

  WifiScanner(Context c) {
    mContext = c;
    mStarted = false;

    try {
      mSHA1 = MessageDigest.getInstance("SHA-1");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public void start() {
    if (mStarted == true) {
        return;
    }
    mStarted = true;

    WifiManager wm = getWifiManager();
    mWifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_SCAN_ONLY,
                                  "MozStumbler");
    mWifiLock.acquire();
    
    if (!wm.isWifiEnabled()) {
      wm.setWifiEnabled(true);
    }

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
    mWifiLock.release();
    mWifiLock = null;

    mWifiScanTimer.cancel();
    mWifiScanTimer = null;
    
    mContext.unregisterReceiver(this);
    mStarted = false;
  }

  public void onReceive(Context c, Intent intent) {
    Collection<ScanResult> scanResults = getWifiManager().getScanResults();
    if (scanResults == null) {
      return;
    }

    JSONArray wifiInfo = new JSONArray();
    for (ScanResult scanResult : scanResults) {
      if (!shouldLog(scanResult)) {
        continue;
      }

      String BSSID = BSSIDBlockList.canonicalizeBSSID(scanResult.BSSID);
      if (BSSID == null) {
        Log.e(LOGTAG, "", new IllegalArgumentException("Unexpected BSSID: " + scanResult.BSSID));
      }
      scanResult.BSSID = BSSID;

      try {
        JSONObject obj = new JSONObject();
        obj.put("key", hashScanResult(scanResult));
        obj.put("frequency", scanResult.frequency);
        obj.put("signal", scanResult.level);
        wifiInfo.put(obj);
      } catch (JSONException jsonex) {
        Log.e(LOGTAG, "", jsonex);
      }
      // TODO: this is for the total stats we have seen
      // Since mAPs will grow without bound, strip BSSID colons to reduce memory usage.
      //        mAPs.add(scanResult.BSSID.replace(":", ""));
      Log.v(LOGTAG, "BSSID=" + scanResult.BSSID + ", SSID=\"" + scanResult.SSID + "\", Signal=" + scanResult.level);
    }

    Intent i = new Intent(ScannerService.MESSAGE_TOPIC);
    i.putExtra(Intent.EXTRA_SUBJECT, "WifiScanner");
    i.putExtra("data", wifiInfo.toString());
    i.putExtra("time", System.currentTimeMillis());
    mContext.sendBroadcast(i);
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

  private String hashScanResult(ScanResult scanResult) {
    StringBuilder sb = new StringBuilder();
    byte[] result = mSHA1.digest((scanResult.BSSID + scanResult.SSID).getBytes());
    for (byte b : result) {
      sb.append(String.format("%02X", b));
    }
    return sb.toString();
  }


  private WifiManager getWifiManager() {
    return (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
  }
}
