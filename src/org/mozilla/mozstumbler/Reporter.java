package org.mozilla.mozstumbler;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
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
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

class Reporter {
    private static final String LOGTAG          = Reporter.class.getName();
    private static final String LOCATION_URL    = "https://location.services.mozilla.com/v1/submit";
    private static final String NICKNAME_HEADER = "X-Nickname";
    private static final String USER_AGENT_HEADER = "User-Agent";
    private static final int RECORD_BATCH_SIZE  = 100;

    private static String       MOZSTUMBLER_USER_AGENT_STRING;

    private final Context       mContext;
    private final Prefs         mPrefs;
    private final Set<String>   mAPs = new HashSet<String>();
    private int mLocationCount;
    private JSONArray mReports;
    private volatile long mLastUploadTime;

    Reporter(Context context, Prefs prefs) {
        mContext = context;
        mPrefs = prefs;

        MOZSTUMBLER_USER_AGENT_STRING = getUserAgentString();

        String storedReports = mPrefs.getReports();
        try {
            mReports = new JSONArray(storedReports);
        } catch (Exception e) {
            mReports = new JSONArray();
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

        JSONArray reports = mReports;
        mReports = new JSONArray();

        String nickname = mPrefs.getNickname();
        spawnReporterThread(reports, nickname);
    }

    private void spawnReporterThread(final JSONArray reports, final String nickname) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    Log.d(LOGTAG, "sending results...");

                    URL url = new URL(LOCATION_URL);
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                    try {
                        urlConnection.setDoOutput(true);
                        urlConnection.setRequestProperty(USER_AGENT_HEADER, MOZSTUMBLER_USER_AGENT_STRING);

                        if (nickname != null) {
                            urlConnection.setRequestProperty(NICKNAME_HEADER, nickname);
                        }

                        JSONObject wrapper = new JSONObject();
                        wrapper.put("items", reports);
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

                        mLastUploadTime = System.currentTimeMillis();
                        sendUpdateIntent();

                        Log.d(LOGTAG, "response was: \n" + total + "\n");
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
                    obj.put("key", ap.BSSID);
                    obj.put("frequency", ap.frequency);
                    obj.put("signal", ap.level);
                    wifiInfo.put(obj);

                    mAPs.add(ap.BSSID);

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

        mLocationCount++;
        sendUpdateIntent();
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

    public int getLocationCount() {
        return mLocationCount;
    }

    public int getAPCount() {
        return mAPs.size();
    }

    public long getLastUploadTime() {
        return mLastUploadTime;
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

    private String getUserAgentString() {
        String appName = mContext.getString(R.string.app_name);

        String versionName;
        try {
            PackageManager pm = mContext.getPackageManager();
            versionName = pm.getPackageInfo("org.mozilla.mozstumbler", 0).versionName;
        } catch (NameNotFoundException e) {
            throw new IllegalArgumentException(e);
        }

        // "MozStumbler/X.Y.Z"
        return appName + '/' + versionName;
    }

    private void sendUpdateIntent() {
        Intent i = new Intent(ScannerService.MESSAGE_TOPIC);
        i.putExtra(Intent.EXTRA_SUBJECT, "Reporter");
        mContext.sendBroadcast(i);
    }
}
