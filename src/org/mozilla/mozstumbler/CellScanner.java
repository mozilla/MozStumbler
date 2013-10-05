package org.mozilla.mozstumbler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import android.telephony.CellLocation;
import android.telephony.NeighboringCellInfo;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;

import android.os.Build.VERSION; 

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;
import java.util.Timer;
import java.util.TimerTask;

public class CellScanner extends PhoneStateListener {
    private static final String LOGTAG = Scanner.class.getName();
    private static final long CELL_MIN_UPDATE_TIME    = 1000; // milliseconds

    private final Context mContext;
    private Timer mCellScanTimer;
    private String mRadioType;
    private int mSignalStrength;

    CellScanner(Context context) {
        mContext = context;

        TelephonyManager tm = getTelephonyManager();
        mRadioType = getRadioTypeName((tm != null) ? tm.getPhoneType() : TelephonyManager.PHONE_TYPE_NONE);

        tm.listen(this, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
    }

    public void start() {
        mCellScanTimer = new Timer();

        mCellScanTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Log.d(LOGTAG, "Cell Scanning Timer fired");
                    getCellInfo();
                }
            }, 0, CELL_MIN_UPDATE_TIME);
    }

    public void stop() {
      if (mCellScanTimer != null) {
        mCellScanTimer.cancel();
        mCellScanTimer = null;
      }
    }

    @Override
    public void onSignalStrengthsChanged(SignalStrength ss) {
        if (ss.isGsm()) {
            mSignalStrength = ss.getGsmSignalStrength();
        }
    }

    private void getCellInfo() {
        JSONArray cellInfo = new JSONArray();

        TelephonyManager tm = getTelephonyManager();
        if (tm == null) {
            return;
        }
        Collection<NeighboringCellInfo> cells = tm.getNeighboringCellInfo();
        CellLocation cl = tm.getCellLocation();
        if (cl == null) {
            return;
        }

        String mcc = "", mnc = "";
        if (cl instanceof GsmCellLocation) {
            JSONObject obj = new JSONObject();
            GsmCellLocation gcl = (GsmCellLocation) cl;
            try {
                obj.put("lac", gcl.getLac());
                obj.put("cid", gcl.getCid());
                int psc = (VERSION.SDK_INT >= 9) ? gcl.getPsc() : -1;
                obj.put("psc", psc); 
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
                    case TelephonyManager.NETWORK_TYPE_LTE:
                        obj.put("radio", "lte");
                        Log.d(LOGTAG, "LTE network detected - NetworkType: " + tm.getNetworkType());
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
                        case TelephonyManager.NETWORK_TYPE_LTE:
                            obj.put("radio", "lte");
                            Log.d(LOGTAG, "LTE network in NeighboringCell - NetworkType: " + tm.getNetworkType());
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

        Intent i = new Intent(ScannerService.MESSAGE_TOPIC);
        i.putExtra(Intent.EXTRA_SUBJECT, "CellScanner");
        i.putExtra("data", cellInfo.toString());
        i.putExtra("time", System.currentTimeMillis());
        i.putExtra("radioType", mRadioType);
        mContext.sendBroadcast(i);
    }

    private static String getRadioTypeName(int phoneType) {
        switch (phoneType) {
            case TelephonyManager.PHONE_TYPE_CDMA:
                return "cdma";

            case TelephonyManager.PHONE_TYPE_GSM:
                return "gsm";

            case TelephonyManager.PHONE_TYPE_NONE:
            case TelephonyManager.PHONE_TYPE_SIP:
                // These devices have no radio.
                return null;

            default:
                Log.e(LOGTAG, "", new IllegalArgumentException("Unexpected PHONE_TYPE: " + phoneType));
                return null;
        }
    }

    private TelephonyManager getTelephonyManager() {
        return (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
    }
}
