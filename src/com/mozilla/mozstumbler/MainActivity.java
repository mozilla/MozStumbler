package com.mozilla.mozstumbler;

import android.os.Bundle;
import android.os.Looper;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.telephony.CellLocation;
import android.telephony.NeighboringCellInfo;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;

public class MainActivity extends Activity implements LocationListener {
    private static final String LOGTAG = "Mozilla Stumbler";
    protected static final String LOCATION_URL = "https://location.services.mozilla.com/v1/submit";

    private boolean mIsScanning = false;

    private int mSignalStrenth;
    private PhoneStateListener mPhoneStateListener = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button scanningBtn = (Button) findViewById(R.id.toggle_scanning);
        scanningBtn.setText(R.string.start_scanning);
    }

    public void onBtnClicked(View v) {
        if (v.getId() == R.id.toggle_scanning) {

            mIsScanning = !mIsScanning;
            // handle the click here
            Button b = (Button) v;

            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

            if (mIsScanning) {

                Criteria criteria = new Criteria();
                criteria.setSpeedRequired(false);
                criteria.setBearingRequired(false);
                criteria.setAltitudeRequired(false);
                criteria.setAccuracy(Criteria.ACCURACY_FINE);
                criteria.setPowerRequirement(Criteria.POWER_HIGH); // hmm.

                String provider = lm.getBestProvider(criteria, true);
                lm.requestLocationUpdates(provider, 1000, (float) .5,
                        getLocationListener(), Looper.getMainLooper());

                b.setText(R.string.stop_scanning);
            } else {
                b.setText(R.string.start_scanning);
                lm.removeUpdates(getLocationListener());
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    public LocationListener getLocationListener() {
        if (mPhoneStateListener == null) {
            mPhoneStateListener = new PhoneStateListener() {
                public void onSignalStrengthsChanged(
                        SignalStrength signalStrength) {
                    setCurrentSignalStrenth(signalStrength);
                }
            };
            TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            tm.listen(mPhoneStateListener,
                    PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        }
        return this;
    }

    // copied from
    // http://code.google.com/p/sensor-data-collection-library/source/browse/src/main/java/TextFileSensorLog.java#223,
    // which is apache licensed
    private static final Set<Character> AD_HOC_HEX_VALUES = new HashSet<Character>(
            Arrays.asList('2', '6', 'a', 'e', 'A', 'E'));

    private static final String OPTOUT_SSID_SUFFIX = "_nomap";

    private static boolean shouldLog(final ScanResult sr) {
        // We filter out any ad-hoc devices. Ad-hoc devices are identified by
        // having a
        // 2,6,a or e in the second nybble.
        // See http://en.wikipedia.org/wiki/MAC_address -- ad hoc networks
        // have the last two bits of the second nybble set to 10.
        // Only apply this test if we have exactly 17 character long BSSID which
        // should
        // be the case.
        final char secondNybble = sr.BSSID.length() == 17 ? sr.BSSID.charAt(1)
                : ' ';

        if (AD_HOC_HEX_VALUES.contains(secondNybble)) {
            return false;

        } else if (sr.SSID != null && sr.SSID.endsWith(OPTOUT_SSID_SUFFIX)) {
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        collectAndReportLocInfo(location);
    }

    public void setCurrentSignalStrenth(SignalStrength ss) {
        if (ss.isGsm())
            mSignalStrenth = ss.getGsmSignalStrength();
    }

    private int getCellInfo(JSONArray cellInfo) {
        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (tm == null)
            return TelephonyManager.PHONE_TYPE_NONE;
        List<NeighboringCellInfo> cells = tm.getNeighboringCellInfo();
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
                }
                String mcc_mnc = tm.getNetworkOperator();
                if (mcc_mnc.length() > 3) {
                    mcc = mcc_mnc.substring(0, 3);
                    mnc = mcc_mnc.substring(3);
                    obj.put("mcc", mcc);
                    obj.put("mnc", mnc);
                }
                obj.put("asu", mSignalStrenth);
            } catch (JSONException jsonex) {
            }
            cellInfo.put(obj);
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
                    }

                    obj.put("asu", nci.getRssi());
                    cellInfo.put(obj);
                } catch (JSONException jsonex) {
                }
            }
        }
        return tm.getPhoneType();
    }

    @SuppressLint("SimpleDateFormat")
    private void collectAndReportLocInfo(Location location) {
        final JSONObject locInfo = new JSONObject();
        WifiManager wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        wm.startScan();
        try {
            JSONArray cellInfo = new JSONArray();
            int radioType = getCellInfo(cellInfo);
            if (radioType == TelephonyManager.PHONE_TYPE_GSM)
                locInfo.put("radio", "gsm");

            locInfo.put("lon", location.getLongitude());
            locInfo.put("lat", location.getLatitude());
            locInfo.put("accuracy", (int) location.getAccuracy());
            locInfo.put("altitude", (int) location.getAltitude());

            DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
            locInfo.put("time", df.format(new Date(location.getTime())));
            locInfo.put("cell", cellInfo);

            MessageDigest digest = MessageDigest.getInstance("SHA-1");

            JSONArray wifiInfo = new JSONArray();
            List<ScanResult> aps = wm.getScanResults();
            if (aps != null) {
                for (ScanResult ap : aps) {
                    if (!shouldLog(ap))
                        continue;
                    StringBuilder sb = new StringBuilder();
                    try {
                        byte[] result = digest.digest((ap.BSSID + ap.SSID)
                                .getBytes("UTF-8"));
                        for (byte b : result)
                            sb.append(String.format("%02X", b));

                        JSONObject obj = new JSONObject();

                        obj.put("key", sb.toString());
                        obj.put("frequency", ap.frequency);
                        obj.put("signal", ap.level);
                        wifiInfo.put(obj);
                    } catch (UnsupportedEncodingException uee) {
                        Log.w(LOGTAG, "can't encode the key", uee);
                    }
                }
            }
            locInfo.put("wifi", wifiInfo);
        } catch (JSONException jsonex) {
            Log.w(LOGTAG, "json exception", jsonex);
        } catch (NoSuchAlgorithmException nsae) {
            Log.w(LOGTAG, "can't creat a SHA1", nsae);
        }

        new Thread(new Runnable() {
            public void run() {
                try {
                    URL url = new URL(LOCATION_URL);
                    HttpURLConnection urlConnection = (HttpURLConnection) url
                            .openConnection();
                    try {
                        urlConnection.setDoOutput(true);
                        JSONArray batch = new JSONArray();
                        batch.put(locInfo);
                        JSONObject wrapper = new JSONObject();
                        wrapper.put("items", batch);
                        byte[] bytes = wrapper.toString().getBytes();
                        urlConnection.setFixedLengthStreamingMode(bytes.length);
                        OutputStream out = new BufferedOutputStream(
                                urlConnection.getOutputStream());
                        out.write(bytes);
                        out.flush();
                    } catch (JSONException jsonex) {
                        Log.e(LOGTAG, "error wrapping data as a batch", jsonex);
                    } catch (Exception ex) {
                        Log.e(LOGTAG, "error submitting data", ex);
                    } finally {
                        urlConnection.disconnect();
                    }
                } catch (Exception ex) {
                    Log.e(LOGTAG, "error submitting data", ex);
                }
            }
        }).start();
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
}
