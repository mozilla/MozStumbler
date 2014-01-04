package org.mozilla.mozstumbler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;

import org.mozilla.mozstumbler.preferences.Prefs;

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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.locks.ReentrantLock;

final class Reporter extends BroadcastReceiver {
    private static final String LOGTAG          = Reporter.class.getName();
    private static final String LOCATION_URL    = "https://location.services.mozilla.com/v1/submit";
    private static final String NICKNAME_HEADER = "X-Nickname";
    private static final String USER_AGENT_HEADER = "User-Agent";
    private static final int RECORD_BATCH_SIZE  = 100;
    private static final int REPORTER_WINDOW  = 3000; //ms

    private static String       MOZSTUMBLER_USER_AGENT_STRING;
    private static String       MOZSTUMBLER_API_KEY_STRING;

    private final Context       mContext;
    private final Prefs         mPrefs;
    private JSONArray           mReports;

    private long                mLastUploadTime;
    private URL                 mURL;
    private ReentrantLock       mReportsLock;

    private String mWifiData;
    private long   mWifiDataTime;

    private String mCellData;
    private long   mCellDataTime;

    private boolean mIsGpsPositionKnown = false;
    private final   Location mGpsPosition = new Location("");

    private String mRadioType;
    private long mReportsSent;

    Reporter(Context context, Prefs prefs) {
        mContext = context;
        mPrefs = prefs;

        MOZSTUMBLER_USER_AGENT_STRING = NetworkUtils.getUserAgentString(mContext);

        String storedReports = mPrefs.getReports();
        try {
            mReports = new JSONArray(storedReports);
        } catch (Exception e) {
            mReports = new JSONArray();
        }

        String apiKey = PackageUtils.getMetaDataString(context, "org.mozilla.mozstumbler.API_KEY");
        try {
            mURL = new URL(LOCATION_URL + "?key=" + apiKey);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
        mReportsLock = new ReentrantLock();

        resetData();
        mContext.registerReceiver(this, new IntentFilter(ScannerService.MESSAGE_TOPIC));
    }

    private void resetData() {
        mWifiData = "";
        mCellData = "";
        mRadioType = "";
        mWifiDataTime = 0;
        mCellDataTime = 0;
        mGpsPosition.reset();
        mIsGpsPositionKnown = false;
    }

    void shutdown() {
        Log.d(LOGTAG, "shutdown");

        resetData();
        // Attempt to write out mReports
        mReportsLock.lock();
        mPrefs.setReports(mReports.toString());
        mReportsLock.unlock();
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
        if (data != null) {
            Log.d(LOGTAG, "" + subject + " : " + data);
        }

        if (mWifiDataTime - time > REPORTER_WINDOW) {
            mWifiData = "";
            mWifiDataTime = 0;
        }

        if (mCellDataTime - time > REPORTER_WINDOW) {
            mCellData = "";
            mCellDataTime = 0;
        }

        if (WifiScanner.WIFI_SCANNER_EXTRA_SUBJECT.equals(subject)) {
            mWifiData = data;
            mWifiDataTime = time;
        } else if (subject.equals("CellScanner")) {
            mCellData = data;
            mRadioType = intent.getStringExtra("radioType");
            mCellDataTime = time;
        } else if (GPSScanner.GPS_SCANNER_EXTRA_SUBJECT.equals(subject)) {
            Location l = intent.getParcelableExtra(GPSScanner.GPS_SCANNER_ARG_LOCATION);
            if (l == null) {
                mIsGpsPositionKnown = false;
            } else {
                mGpsPosition.set(l);
                mIsGpsPositionKnown = true;
            }
        }
        else {
            Log.d(LOGTAG, "Intent ignored with Subject: " + subject);
            return; // Intent not aimed at the Reporter (it is possibly for UI instead)
        }

        if (mIsGpsPositionKnown && (mWifiData.length() > 0 || mCellData.length() > 0)) {
          reportLocation(mGpsPosition, mWifiData, mRadioType, mCellData);
          resetData();
        }
    }

    void sendReports(boolean force) {
        Log.d(LOGTAG, "sendReports: force=" + force);
        mReportsLock.lock();

        int count = mReports.length();
        if (count == 0) {
            Log.d(LOGTAG, "no reports to send");
            mReportsLock.unlock();
            return;
        }

        if (count < RECORD_BATCH_SIZE && !force && mLastUploadTime > 0) {
            Log.d(LOGTAG, "batch count not reached, and !force");
            mReportsLock.unlock();
            return;
        }

        if (!NetworkUtils.isNetworkAvailable(mContext)) {
            Log.d(LOGTAG, "Can't send reports without network connection");
            mReportsLock.unlock();
            return;
        }

        JSONArray reports = mReports;
        mReports = new JSONArray();
        mReportsLock.unlock();

        String nickname = mPrefs.getNickname();
        spawnReporterThread(reports, nickname);
    }

    private void spawnReporterThread(final JSONArray reports, final String nickname) {
        new Thread(new Runnable() {
            public void run() {
                boolean successfulUpload = false;
                try {
                    Log.d(LOGTAG, "sending results...");

                    HttpURLConnection urlConnection = (HttpURLConnection) mURL.openConnection();
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

                        Log.d(LOGTAG, "uploaded wrapperData: " + wrapperData + " to " + mURL.toString());

                        int code = urlConnection.getResponseCode();
                        if (code >= 200 && code <= 299) {
                            mReportsSent = mReportsSent + reports.length();
                        }
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
                        successfulUpload = true;
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

                if (!successfulUpload) {
                    try {
                        mReportsLock.lock();
                        for (int i = 0; i < reports.length(); i++) {
                            mReports.put(reports.get(i));
                        }
                    } catch (JSONException jsonex) {
                    } finally {
                        mReportsLock.unlock();
                    }
                }
            }
        }).start();
    }

    void reportLocation(Location gpsPosition, String wifiInfo, String radioType, String cellInfo) {
        Log.d(LOGTAG, "reportLocation called");
        JSONObject locInfo = null;
        JSONArray cellJSON = null;
        JSONArray wifiJSON = null;

        try {
            locInfo = new JSONObject();
            locInfo.put("lat", gpsPosition.getLatitude());
            locInfo.put("lon", gpsPosition.getLongitude());
            locInfo.put("time", DateTimeUtils.formatTime(gpsPosition.getTime()));
            if (gpsPosition.hasAccuracy()) locInfo.put("accuracy", (int)gpsPosition.getAccuracy());
            if (gpsPosition.hasAltitude()) locInfo.put("altitude", (int)gpsPosition.getAltitude());

            if (cellInfo.length()>0) {
                cellJSON=new JSONArray(cellInfo);
                locInfo.put("cell", cellJSON);
                locInfo.put("radio", radioType);
            }

            if (wifiInfo.length()>0) {
                wifiJSON=new JSONArray(wifiInfo);
                locInfo.put("wifi", wifiJSON);
            }

            // At least one cell or wifi entry is required
            // as per: https://mozilla-ichnaea.readthedocs.org/en/latest/api/submit.html
            if (cellJSON == null && wifiJSON == null) {
                Log.w(LOGTAG, "Invalid report: at least one cell/wifi entry is required");
                return;
            }

        } catch (JSONException jsonex) {
            Log.w(LOGTAG, "JSON exception", jsonex);
            return;
        }

        mReports.put(locInfo);
        sendReports(false);
    }

    public long getLastUploadTime() {
        return mLastUploadTime;
    }

    public long getReportsSent() {
        return mReportsSent;
    }

    private void sendUpdateIntent() {
        Intent i = new Intent(ScannerService.MESSAGE_TOPIC);
        i.putExtra(Intent.EXTRA_SUBJECT, "Reporter");
        mContext.sendBroadcast(i);
    }
}
