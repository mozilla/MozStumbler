package org.mozilla.mozstumbler;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
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
import android.content.BroadcastReceiver;


class Reporter extends BroadcastReceiver {
    private static final String LOGTAG          = Reporter.class.getName();
    private static final String LOCATION_URL    = "https://location.services.mozilla.com/v1/submit";
    private static final String NICKNAME_HEADER = "X-Nickname";
    private static final String USER_AGENT_HEADER = "User-Agent";
    private static final int RECORD_BATCH_SIZE  = 100;
    private static final int REPORTER_WINDOW  = 3000; //ms

    private static String       MOZSTUMBLER_USER_AGENT_STRING;

    private final Context       mContext;
    private final Prefs         mPrefs;

    private int   mAPCount;
    private int   mLocationCount;
    private JSONArray mReports;
    private volatile long mLastUploadTime;

    private String mWifiData;
    private long mWifiDataTime;

    private String mCellData;
    private long mCellDataTime;

    private long mGPSDataTime;
    private String mGPSData;

    private String mRadioType;
        
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

        resetData();
        mContext.registerReceiver(this, new IntentFilter(ScannerService.MESSAGE_TOPIC));
    }

    private void resetData() {
        mWifiData = "";
        mCellData = "";
        mRadioType = "";
        mGPSData = "";
        mWifiDataTime = 0;
        mCellDataTime = 0;
        mGPSDataTime = 0;
    }

    void shutdown() {
        Log.d(LOGTAG, "shutdown");

        resetData();
        // Attempt to write out mReports
        mPrefs.setReports(mReports.toString());
        mContext.unregisterReceiver(this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        
        if (!action.equals(ScannerService.MESSAGE_TOPIC)) {
            Log.e(LOGTAG, "Received an unknown intent");
            return;
        }

        long time = intent.getLongExtra("time", 0);
        String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);
        String data = intent.getStringExtra("data");
        Log.d(LOGTAG, "" + subject + " : " + data);

        if (subject.equals("WifiScanner")) {
            mWifiData = data;
            mWifiDataTime = time;
        } else if (subject.equals("CellScanner")) {
            mCellData = data;
            mRadioType = intent.getStringExtra("radioType");
            mCellDataTime = time;
        } else if (subject.equals("GPSScanner")) {
            mGPSData = data;
            mGPSDataTime = time;
        }
        else {
          Log.e(LOGTAG, "Unexpected subject: " + subject);
        }

        if (mWifiDataTime - time > REPORTER_WINDOW) {
          mWifiData = "";
          mWifiDataTime = 0;
        }

        if (mCellDataTime - time > REPORTER_WINDOW) {
          mCellData = "";
          mCellDataTime = 0;
        }

        if (mGPSDataTime - time > REPORTER_WINDOW) {
          mGPSData = "";
          mGPSDataTime = 0;
        }

        // Record recent Wi-Fi and/or cell scan results for the current GPS position.
        if (mGPSData.length() > 0 && (mWifiData.length() > 0 || mCellData.length() > 0)) {
          reportLocation(mGPSData, mWifiData, mRadioType, mCellData);
          resetData();
        }
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
                        String wrapperData = wrapper.toString();
                        byte[] bytes = wrapperData.getBytes();
                        urlConnection.setFixedLengthStreamingMode(bytes.length);
                        OutputStream out = new BufferedOutputStream(urlConnection.getOutputStream());
                        out.write(bytes);
                        out.flush();

                        Log.d(LOGTAG, "uploaded wrapperData: " + wrapperData + " to " + LOCATION_URL);

                        int code = urlConnection.getResponseCode();
                        Log.e(LOGTAG, "urlConnection returned " + code);

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

    void reportLocation(String location, String wifiInfo, String radioType, String cellInfo) {
        Log.d(LOGTAG, "reportLocation called");
        JSONObject locInfo = null;
        try {
            locInfo = new JSONObject( location );
            locInfo.put("cell", new JSONArray(cellInfo));
            locInfo.put("radio", radioType);

            JSONArray w = new JSONArray(wifiInfo);
            mAPCount += w.length();
            locInfo.put("wifi", w);

        } catch (JSONException jsonex) {
            Log.w(LOGTAG, "json exception", jsonex);
            return;
        }

        mReports.put(locInfo);
        sendReports(false);
        mLocationCount++;
    }

    public int getLocationCount() {
        return mLocationCount;
    }

    public int getAPCount() {
        return mAPCount;
    }

    public long getLastUploadTime() {
      return mLastUploadTime;
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
