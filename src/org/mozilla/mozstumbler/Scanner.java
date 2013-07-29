package org.mozilla.mozstumbler;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Bundle;
import android.telephony.CellLocation;
import android.telephony.NeighboringCellInfo;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;
import java.util.Timer;
import java.util.TimerTask;

class Scanner implements LocationListener {
    private static final String LOGTAG              = Scanner.class.getName();
    private static final long   GEO_MIN_UPDATE_TIME     = 0;                   // milliseconds
    private static final float  GEO_MIN_UPDATE_DISTANCE = 10;                     // meters
    private static final long   WIFI_MIN_UPDATE_TIME    = 5000;                   // milliseconds

    private ScannerService      mContext;
    private int                 mSignalStrength;
    private PhoneStateListener  mPhoneStateListener;
    private final Reporter      mReporter;
    private boolean             mIsScanning;
    private WifiLock            mWifiLock;
    private WifiReceiver        mWifiReceiver;

    private Timer                  mWifiScanTimer;
    private long                   mWifiScanResultsTime;
    private Collection<ScanResult> mWifiScanResults;

    Scanner(ScannerService context, Reporter reporter) {
        mContext = context;
        mReporter = reporter;
    }

    class WifiReceiver extends BroadcastReceiver {
      public void onReceive(Context c, Intent intent) {

        mWifiScanResults = getWifiManager().getScanResults();
        mWifiScanResultsTime = System.currentTimeMillis();

        Log.d(LOGTAG, "WifiReceiver new data at " + mWifiScanResultsTime);

        // TODO does this even work?  Aren't we just setting
        // a copy (scanResult) to store the correct BSSID?

        for (ScanResult scanResult : mWifiScanResults) {
          String BSSID = BSSIDBlockList.canonicalizeBSSID(scanResult.BSSID);
          if (BSSID == null) {
            Log.e(LOGTAG, "", new IllegalArgumentException("Unexpected BSSID: " + scanResult.BSSID));
          }
          scanResult.BSSID = BSSID;
        }
      }
    }

    void startScanning() {
        if (mIsScanning) {
            return;
        }
        Log.d(LOGTAG, "Scanning started...");
        deleteAidingData();
        LocationManager lm = getLocationManager();
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                                  GEO_MIN_UPDATE_TIME,
                                  GEO_MIN_UPDATE_DISTANCE,
                                  getLocationListener());

        WifiManager wm = getWifiManager();
        mWifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_SCAN_ONLY,
                                      "MozStumbler");      
        mWifiLock.acquire();
        
        if (!wm.isWifiEnabled()) {
            wm.setWifiEnabled(true);
        } 
        
        mWifiReceiver = new WifiReceiver();
        IntentFilter i = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        mContext.registerReceiver(mWifiReceiver, i);

        // Ensure that we are constantly scanning for new access points.
        mWifiScanTimer = new Timer();
        mWifiScanTimer.schedule(new TimerTask() {
            @Override
            public void run() {
              Log.d(LOGTAG, "WiFi Scanning Timer fired");
              getWifiManager().startScan();
            }
          }, WIFI_MIN_UPDATE_TIME, WIFI_MIN_UPDATE_TIME);

        // start some kind of timer repeating..
        mIsScanning = true;
    }

    void stopScanning() {
        if (!mIsScanning) {
            return;
        }

        Log.d(LOGTAG, "Scanning stopped");
        LocationManager lm = getLocationManager();
        lm.removeUpdates(getLocationListener());

        if (mPhoneStateListener != null) {
            TelephonyManager tm = getTelephonyManager();
            tm.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
            mPhoneStateListener = null;
        }
        
        mWifiLock.release();
        mWifiLock = null;

        mWifiScanTimer.cancel();
        mWifiScanTimer = null;

        mContext.unregisterReceiver(mWifiReceiver);
        mWifiReceiver = null;

        mIsScanning = false;
    }

    boolean isScanning() {
        return mIsScanning;
    }

    private LocationListener getLocationListener() {
        if (mPhoneStateListener == null) {
            mPhoneStateListener = new PhoneStateListener() {
                public void onSignalStrengthsChanged(SignalStrength ss) {
                    if (ss.isGsm()) {
                        mSignalStrength = ss.getGsmSignalStrength();
                    }
                }
            };
            TelephonyManager tm = getTelephonyManager();
            tm.listen(mPhoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        }
        return this;
    }

    @Override
    public void onLocationChanged(Location location) {
        if (LocationBlockList.contains(location)) {
            Log.w(LOGTAG, "Blocked location: " + location);
        } else {
            Log.d(LOGTAG, "New location: " + location);

            mReporter.reportLocation(location,
                                     getWifiInfo(),
                                     getRadioType(),
                                     getCellInfo());
        }
    }

    private Collection<ScanResult> getWifiInfo() {

        Log.d(LOGTAG, "getWifiInfo() called at " + System.currentTimeMillis());
        if (System.currentTimeMillis() - mWifiScanResultsTime < 5000 && mWifiScanResults != null) {
          return mWifiScanResults;
        }
        Log.d(LOGTAG, "getWifiInfo() called and there is no (or only old) scan results");
        return null;
    }

    private int getRadioType() {
      TelephonyManager tm = getTelephonyManager();
      if (tm == null)
        return TelephonyManager.PHONE_TYPE_NONE;
      return tm.getPhoneType();
    }

    private JSONArray getCellInfo() {
        JSONArray cellInfo = new JSONArray();

        TelephonyManager tm = getTelephonyManager();
        if (tm == null)
            return null;
        Collection<NeighboringCellInfo> cells = tm.getNeighboringCellInfo();
        CellLocation cl = tm.getCellLocation();
        if (cl == null)
            return null;

        String mcc = "", mnc = "";
        if (cl instanceof GsmCellLocation) {
            JSONObject obj = new JSONObject();
            GsmCellLocation gcl = (GsmCellLocation) cl;
            try {
                obj.put("lac", gcl.getLac());
                obj.put("cid", gcl.getCid());
                obj.put("psc", gcl.getPsc());
                switch (tm.getNetworkType()) {
                    case TelephonyManager.NETWORK_TYPE_GPRS:
                    case TelephonyManager.NETWORK_TYPE_EDGE:
                        obj.put("radio", "gsm");
                        break;
                    case TelephonyManager.NETWORK_TYPE_UMTS:
                    case TelephonyManager.NETWORK_TYPE_HSDPA:
                    case TelephonyManager.NETWORK_TYPE_HSUPA:
                    case TelephonyManager.NETWORK_TYPE_HSPA:
                    case TelephonyManager.NETWORK_TYPE_HSPAP:
                        obj.put("radio", "umts");
                        break;
                    default:
                        Log.w(LOGTAG, "", new IllegalStateException("Unexpected NetworkType: " + tm.getNetworkType()));
                        break;
                }
                String mcc_mnc = tm.getNetworkOperator();
                if (mcc_mnc.length() > 3) {
                    mcc = mcc_mnc.substring(0, 3);
                    mnc = mcc_mnc.substring(3);
                    obj.put("mcc", mcc);
                    obj.put("mnc", mnc);
                }
                obj.put("asu", mSignalStrength);
                cellInfo.put(obj);
            } catch (JSONException jsonex) {
                Log.e(LOGTAG, "", jsonex);
            }
        }
        if (cells != null) {
            for (NeighboringCellInfo nci : cells) {
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("lac", nci.getLac());
                    obj.put("cid", nci.getCid());
                    obj.put("psc", nci.getPsc());
                    obj.put("mcc", mcc);
                    obj.put("mnc", mnc);

                    switch (nci.getNetworkType()) {
                        case TelephonyManager.NETWORK_TYPE_GPRS:
                        case TelephonyManager.NETWORK_TYPE_EDGE:
                            obj.put("radio", "gsm");
                            break;
                        case TelephonyManager.NETWORK_TYPE_UMTS:
                        case TelephonyManager.NETWORK_TYPE_HSDPA:
                        case TelephonyManager.NETWORK_TYPE_HSUPA:
                        case TelephonyManager.NETWORK_TYPE_HSPA:
                        case TelephonyManager.NETWORK_TYPE_HSPAP:
                            obj.put("radio", "umts");
                            break;
                        default:
                            Log.w(LOGTAG, "",
                                    new IllegalStateException("Unexpected NetworkType: " + tm.getNetworkType()));
                            break;
                    }

                    obj.put("asu", nci.getRssi());
                    cellInfo.put(obj);
                } catch (JSONException jsonex) {
                    Log.e(LOGTAG, "", jsonex);
                }
            }
        }
        return cellInfo;
    }

    @Override
    public void onProviderDisabled(String provider) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onProviderEnabled(String provider) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // TODO Auto-generated method stub
    }

    private void deleteAidingData() {
        LocationManager lm = getLocationManager();

        // Delete cached A-GPS aiding data to force GPS cold start.
        lm.sendExtraCommand(LocationManager.GPS_PROVIDER, "delete_aiding_data", null);
        lm.sendExtraCommand(LocationManager.GPS_PROVIDER, "force_xtra_injection", null);

        // Force NTP resync.
        lm.sendExtraCommand(LocationManager.GPS_PROVIDER, "force_time_injection", null);
    }

    private LocationManager getLocationManager() {
        return (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
    }

    private TelephonyManager getTelephonyManager() {
        return (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
    }

    private WifiManager getWifiManager() {
        return (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
    }
}
