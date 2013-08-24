package org.mozilla.mozstumbler;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.telephony.TelephonyManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

class Reporter {
    private static final String LOGTAG          = Reporter.class.getName();
    private static final String LOCATION_URL    = "https://location.services.mozilla.com/v1/submit";
    private static final String NICKNAME_HEADER = "X-Nickname";
    private static final String TOKEN_HEADER    = "X-Token";
    private static final int RECORD_BATCH_SIZE  = 100;

    private final Context       mContext;
    private final Prefs         mPrefs;
    private final MessageDigest mSHA1;
    private final Set<String>   mAPs = new HashSet<String>();

    private JSONArray mReports;

    Reporter(Context context, Prefs prefs) {
        mContext = context;
        mPrefs = prefs;

        String storedReports = mPrefs.getReports();
        try {
            mReports = new JSONArray(storedReports);
        } catch (Exception e) {
            mReports = new JSONArray();
        }

        try {
            mSHA1 = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    void shutdown() {
        Log.d(LOGTAG, "shutdown");

        // Attempt to write out mReports
        mPrefs.setReports(mReports.toString());
    }

    void sendReports(boolean force) {
        Log.d(LOGTAG, "sendReports: " + force);
        int count = mReports.length();
        if (count == 0) {
            Log.d(LOGTAG, "no reports to send");
            return;
        }

        if (count < RECORD_BATCH_SIZE && !force) {
            Log.d(LOGTAG, "batch count not reached, and !force");
            return;
        }

        new Thread(new Runnable() {
            public void run() {
                try {
                    Log.d(LOGTAG, "sending results...");

                    URL url = new URL(LOCATION_URL);
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                    try {
                        urlConnection.setDoOutput(true);

                        String nickname = mPrefs.getNickname();
                        if (nickname != null) {
                            String token = mPrefs.getToken().toString();
                            urlConnection.setRequestProperty(TOKEN_HEADER, token);
                            urlConnection.setRequestProperty(NICKNAME_HEADER, nickname);
                        }

                        JSONObject wrapper = new JSONObject();
                        wrapper.put("items", mReports);
                        String data = wrapper.toString();
                        byte[] bytes = data.getBytes();
                        urlConnection.setFixedLengthStreamingMode(bytes.length);
                        OutputStream out = new BufferedOutputStream(urlConnection.getOutputStream());
                        out.write(bytes);
                        out.flush();

                        InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                        BufferedReader r = new BufferedReader(new InputStreamReader(in));
                        StringBuilder total = new StringBuilder(in.available());
                        String line;
                        while ((line = r.readLine()) != null) {
                          total.append(line);
                        }
                        r.close();

                        Log.d(LOGTAG, "response was: \n" + total + "\n");

                        // clear the reports.
                        mReports = new JSONArray();

                        Log.d(LOGTAG, "uploaded data: " + data + " to " + LOCATION_URL);
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

    void reportLocation(Location location, Collection<ScanResult> scanResults, int radioType, JSONArray cellInfo) {
        Log.d(LOGTAG, "reportLocation called");
        JSONObject locInfo = new JSONObject();
        try {
            locInfo.put("time", DateTimeUtils.formatTime(location.getTime()));
            locInfo.put("lon", location.getLongitude());
            locInfo.put("lat", location.getLatitude());
            locInfo.put("accuracy", (int) location.getAccuracy());
            locInfo.put("altitude", (int) location.getAltitude());
            locInfo.put("cell", cellInfo);

            String radioTypeName = getRadioTypeName(radioType);
            if (radioTypeName != null) {
                locInfo.put("radio", radioTypeName);
            }

            JSONArray wifiInfo = new JSONArray();
            if (scanResults != null) {
                for (ScanResult ap : scanResults) {
                    if (!shouldLog(ap)) {
                        continue;
                    }

                    JSONObject obj = new JSONObject();
                    obj.put("key", hashScanResult(ap));
                    obj.put("frequency", ap.frequency);
                    obj.put("signal", ap.level);
                    wifiInfo.put(obj);

                    // Since mAPs will grow without bound, strip BSSID colons to reduce memory usage.
                    mAPs.add(ap.BSSID.replace(":", ""));

                    Log.v(LOGTAG, "Reporting: BSSID=" + ap.BSSID + ", SSID=\"" + ap.SSID + "\", Signal=" + ap.level);
                }
            }
            locInfo.put("wifi", wifiInfo);
        } catch (JSONException jsonex) {
            Log.w(LOGTAG, "json exception", jsonex);
            return;
        }

        mReports.put(locInfo);

        sendReports(false);

        Intent i = new Intent(ScannerService.MESSAGE_TOPIC);
        i.putExtra(Intent.EXTRA_SUBJECT, "Reporter");
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

    public int getAPCount() {
        return mAPs.size();
    }

    private String hashScanResult(ScanResult ap) {
        StringBuilder sb = new StringBuilder();
        byte[] result = mSHA1.digest((ap.BSSID + ap.SSID).getBytes());
        for (byte b : result) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
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
}
