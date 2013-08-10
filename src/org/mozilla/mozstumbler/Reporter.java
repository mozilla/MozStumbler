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

import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;

class Reporter {
    private static final String LOGTAG          = Reporter.class.getName();
    private static final String LOCATION_URL    = "https://location.services.mozilla.com/v1/submit";
    private static final String NICKNAME_HEADER = "X-Nickname";
    private static final String TOKEN_HEADER    = "X-Token";
    private static final int RECORD_BATCH_SIZE = 100;

    private final Context       mContext;
    private final Prefs         mPrefs;
    private final MessageDigest mSHA1;

    private JSONArray mReports;

    Reporter(Context context, Prefs prefs) {
        mContext = context;
        mPrefs = prefs;

        // Attempt to write out mReports
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
      // Attempt to write out mReports
      mPrefs.setReports(mReports.toString());
    }

    void sendReports(boolean force) {
        int count = mReports.length();
        if (count == 0) {
          return;
        }

        if (count < RECORD_BATCH_SIZE && !force) {
          return;
        }

        new Thread(new Runnable() {
            public void run() {
                try {
                    URL url = new URL(LOCATION_URL);
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                    try {
                        String token = mPrefs.getToken().toString();

                        urlConnection.setDoOutput(true);
                        urlConnection.setRequestProperty(TOKEN_HEADER, token);

                        String nickname = mPrefs.getNickname();
                        if (nickname != null) {
                            urlConnection.setRequestProperty(NICKNAME_HEADER, nickname);
                        }

                        JSONArray batch = new JSONArray();
                        batch.put(mReports);
                        JSONObject wrapper = new JSONObject();
                        wrapper.put("items", batch);
                        String data = wrapper.toString();
                        byte[] bytes = data.getBytes();
                        urlConnection.setFixedLengthStreamingMode(bytes.length);
                        OutputStream out = new BufferedOutputStream(urlConnection.getOutputStream());
                        out.write(bytes);
                        out.flush();

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
        JSONObject locInfo = new JSONObject();
        try {
            locInfo.put("time", DateTimeUtils.formatTime(location.getTime()));
            locInfo.put("lon", location.getLongitude());
            locInfo.put("lat", location.getLatitude());
            locInfo.put("accuracy", (int) location.getAccuracy());
            locInfo.put("altitude", (int) location.getAltitude());
            locInfo.put("cell", cellInfo);
            if (radioType == TelephonyManager.PHONE_TYPE_GSM) {
                locInfo.put("radio", "gsm");
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

                    Log.v(LOGTAG, "Reporting: BSSID=" + ap.BSSID + ", SSID=\"" + ap.SSID + "\", Signal=" + ap.level);
                }
            }
            locInfo.put("wifi", wifiInfo);
        } catch (JSONException jsonex) {
            Log.w(LOGTAG, "json exception", jsonex);
            return;
        }

        mReports.put(locInfo);

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

    public int numberOfReportedLocations() {
        return mReports.length();
    }

    private String hashScanResult(ScanResult ap) {
        StringBuilder sb = new StringBuilder();
        byte[] result = mSHA1.digest((ap.BSSID + ap.SSID).getBytes());
        for (byte b : result) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
