package org.mozilla.mozstumbler;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
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

class Scanner implements LocationListener {
    private static final String LOGTAG = Scanner.class.getName();
    private static final long MIN_UPDATE_TIME = 1000; // milliseconds
    private static final float MIN_UPDATE_DISTANCE = 10; // meters

    private final Context mContext;
    private int mSignalStrength;
    private PhoneStateListener mPhoneStateListener;
    private Reporter mReporter;

    public boolean mIsScanning = false;
    
    Scanner(Context context) {
        mContext = context;
        mReporter = new Reporter();
    }

    void startScanning() {
    	if (mIsScanning) {
          return;
    	}
        Log.d(LOGTAG, "Scanning started...");
        deleteAidingData();
        LocationManager lm = getLocationManager();
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_UPDATE_TIME, MIN_UPDATE_DISTANCE, getLocationListener());
        mIsScanning = true;
    }

    void stopScanning() {
    	if (mIsScanning == false) {
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
                        Log.d(LOGTAG, "GSM signal strength: " + mSignalStrength
                                      + " -> " + ss.getGsmSignalStrength());
                        mSignalStrength = ss.getGsmSignalStrength();
                    }
                }
            };
            TelephonyManager tm = getTelephonyManager();
            tm.listen(mPhoneStateListener,
                    PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        }
        return this;
    }

    @Override
    public void onLocationChanged(Location location) {
        if (LocationBlockList.contains(location)) {
            Log.w(LOGTAG, "Blocked location: " + location);
        } else {
            Log.d(LOGTAG, "New location: " + location);
            collectAndReportLocInfo(location);
        }
    }

    private int getCellInfo(JSONArray cellInfo) {
        TelephonyManager tm = getTelephonyManager();
        if (tm == null)
            return TelephonyManager.PHONE_TYPE_NONE;
        Collection<NeighboringCellInfo> cells = tm.getNeighboringCellInfo();
        CellLocation cl = tm.getCellLocation();
        if (cl == null)
            return TelephonyManager.PHONE_TYPE_NONE;
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
                    Log.w(LOGTAG, "", new IllegalStateException("Unexpected NetworkType: "
                                                                + tm.getNetworkType()));
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
                        Log.w(LOGTAG, "", new IllegalStateException("Unexpected NetworkType: "
                                                                    + tm.getNetworkType()));
                        break;
                    }

                    obj.put("asu", nci.getRssi());
                    cellInfo.put(obj);
                } catch (JSONException jsonex) {
                    Log.e(LOGTAG, "", jsonex);
                }
            }
        }
        return tm.getPhoneType();
    }

    @SuppressLint("SimpleDateFormat")
    private void collectAndReportLocInfo(Location location) {
        JSONArray cellInfo = new JSONArray();
        int radioType = getCellInfo(cellInfo);

        WifiManager wm = getWifiManager();
        wm.startScan();
        Collection<ScanResult> scanResults = wm.getScanResults();
        if (scanResults != null) {
            for (ScanResult scanResult : scanResults) {
                String BSSID = BSSIDBlockList.canonicalizeBSSID(scanResult.BSSID);
                if (BSSID == null) {
                    Log.e(LOGTAG, "", new IllegalArgumentException("Unexpected BSSID: "
                                                                   + scanResult.BSSID));
                }
                scanResult.BSSID = BSSID;
            }
        }

        mReporter.reportLocation(location, scanResults, radioType, cellInfo);
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
