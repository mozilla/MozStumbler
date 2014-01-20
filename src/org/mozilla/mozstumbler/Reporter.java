package org.mozilla.mozstumbler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.os.Build;
import android.util.Log;

import org.mozilla.mozstumbler.cellscanner.CellInfo;
import org.mozilla.mozstumbler.cellscanner.CellScanner;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

final class Reporter extends BroadcastReceiver {
    private static final String LOGTAG          = Reporter.class.getName();
    private static final String LOCATION_URL    = "https://location.services.mozilla.com/v1/submit";
    private static final String NICKNAME_HEADER = "X-Nickname";
    private static final String USER_AGENT_HEADER = "User-Agent";
    private static final int RECORD_BATCH_SIZE  = 100;
    private static final int REPORTER_WINDOW  = 60000; //ms
    private static final int WIFI_COUNT_WATERMARK = 500;
    private static final int CELLS_COUNT_WATERMARK = 50;

    private static String       MOZSTUMBLER_USER_AGENT_STRING;

    private final Context       mContext;
    private final Prefs         mPrefs;
    private JSONArray           mReports;

    private final AtomicLong    mLastUploadTime = new AtomicLong();
    private URL                 mURL;
    private ReentrantLock       mReportsLock;

    private final Location      mGpsPosition = new Location("");
    private long                mGpsPositionTime;

    private final Map<String, ScanResult> mWifiData = new HashMap<String, ScanResult>();
    private final Map<String, CellInfo> mCellData = new HashMap<String, CellInfo>();

    private final AtomicLong mReportsSent = new AtomicLong();

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

    private boolean isGpsPositionKnown() {
        return (mGpsPositionTime > 0);
    }

    private void resetData() {
        mWifiData.clear();
        mCellData.clear();
        mGpsPosition.reset();
        mGpsPositionTime = 0;
    }

    void shutdown() {
        Log.d(LOGTAG, "shutdown");

        reportCollectedLocation();
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

        if (!ScannerService.MESSAGE_TOPIC.equals(action)) {
            Log.e(LOGTAG, "Received an unknown intent");
            return;
        }

        long time = intent.getLongExtra("time", System.currentTimeMillis());
        String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);

        if (isGpsPositionKnown() && Math.abs(time - mGpsPositionTime) > REPORTER_WINDOW) {
            reportCollectedLocation();
        }

        if (WifiScanner.WIFI_SCANNER_EXTRA_SUBJECT.equals(subject)) {
            List<ScanResult> results = intent.getParcelableArrayListExtra(WifiScanner.WIFI_SCANNER_ARG_SCAN_RESULTS);
            putWifiResults(results);
        } else if (CellScanner.CELL_SCANNER_EXTRA_SUBJECT.equals(subject)) {
            List<CellInfo> results = intent.getParcelableArrayListExtra(CellScanner.CELL_SCANNER_ARG_CELLS);
            putCellResults(results);
        } else if (GPSScanner.GPS_SCANNER_EXTRA_SUBJECT.equals(subject)) {
            reportCollectedLocation();
            Location l = intent.getParcelableExtra(GPSScanner.GPS_SCANNER_ARG_LOCATION);
            if (l == null) {
                mGpsPositionTime = 0;
            } else {
                mGpsPosition.set(l);
                mGpsPositionTime = time;
            }
        } else {
            Log.d(LOGTAG, "Intent ignored with Subject: " + subject);
            return; // Intent not aimed at the Reporter (it is possibly for UI instead)
        }

        if (isGpsPositionKnown() && ((mWifiData.size() > WIFI_COUNT_WATERMARK)
                || (mCellData.size() > CELLS_COUNT_WATERMARK))) {
            reportCollectedLocation();
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

        if (count < RECORD_BATCH_SIZE && !force && mLastUploadTime.get() > 0) {
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

                        // Workaround for a bug in Android HttpURLConnection. When the library
                        // reuses a stale connection, the connection may fail with an EOFException
                        if (Build.VERSION.SDK_INT > 13 && Build.VERSION.SDK_INT < 19) {
                            urlConnection.setRequestProperty("Connection", "Close");
                        }

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
                            mReportsSent.addAndGet(reports.length());
                            mLastUploadTime.set(System.currentTimeMillis());
                            sendUpdateIntent();
                            successfulUpload = true;
                        }
                        Log.e(LOGTAG, "urlConnection returned " + code);

                        BufferedReader r = null;
                        try {
                            InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                            r = new BufferedReader(new InputStreamReader(in));
                            StringBuilder total = new StringBuilder(in.available());
                            String line;
                            while ((line = r.readLine()) != null) {
                                total.append(line);
                            }
                            Log.d(LOGTAG, "response was: \n" + total + "\n");
                        } catch (Exception e) {
                            Log.e(LOGTAG, "", e);
                        } finally {
                            if (r != null) {
                                r.close();
                                r = null;
                            }
                        }
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

    private void putWifiResults(List<ScanResult> results) {
        if (!isGpsPositionKnown()) {
            return;
        }
        for (ScanResult result : results) {
            String key = result.BSSID;
            if (!mWifiData.containsKey(key)) {
                mWifiData.put(key, result);
            }
        }
    }

    private void putCellResults(List<CellInfo> cells) {
        if (!isGpsPositionKnown()) {
            return;
        }
        for (CellInfo result : cells) {
            String key = result.getRadio()
                    + " " + result.getCellRadio()
                    + " " + result.getMcc()
                    + " " + result.getMnc()
                    + " " + result.getLac()
                    + " " + result.getCid()
                    + " " + result.getPsc();

            if (!mCellData.containsKey(key)) {
                mCellData.put(key, result);
            }
        }
    }

    private void reportCollectedLocation() {
        if (!isGpsPositionKnown()) {
            return;
        }

        final Collection<CellInfo> cells = mCellData.values();
        final Collection<ScanResult> wifis = mWifiData.values();

        if (cells.isEmpty() && wifis.isEmpty()) {
            return;
        }

        if (!cells.isEmpty()) {
            Map<String, List<CellInfo>> groupByRadio = new HashMap<String, List<CellInfo>>();
            for (CellInfo cell : cells) {
                List<CellInfo> list;
                String radio = cell.getRadio();
                list = groupByRadio.get(radio);
                if (list == null) {
                    list = new ArrayList<CellInfo>();
                    groupByRadio.put(radio, list);
                }
                list.add(cell);
            }
            for (String radio : groupByRadio.keySet()) {
                reportLocation(mGpsPosition, wifis, radio, groupByRadio.get(radio), mGpsPositionTime);
                mWifiData.clear();
            }
            mCellData.clear();
        }
        if (!wifis.isEmpty()) {
            Collection<CellInfo> emptyList = Collections.emptyList();
            reportLocation(mGpsPosition, wifis, "", emptyList, mGpsPositionTime);
        }

        mGpsPositionTime = System.currentTimeMillis();
    }

    void reportLocation(Location gpsPosition, Collection<ScanResult> wifiInfo, String radioType,
                        Collection<CellInfo> cellInfo, long time) {
        Log.d(LOGTAG, "reportLocation called");
        JSONObject locInfo;

        // At least one cell or wifi entry is required
        // as per: https://mozilla-ichnaea.readthedocs.org/en/latest/api/submit.html
        if (cellInfo.isEmpty() && wifiInfo.isEmpty()) {
            Log.w(LOGTAG, "Invalid report: at least one cell/wifi entry is required");
            return;
        }

        try {
            locInfo = new JSONObject();
            locInfo.put("lat", Math.floor(gpsPosition.getLatitude() * 1.0E6) / 1.0E6);
            locInfo.put("lon", Math.floor(gpsPosition.getLongitude() * 1.0E6) / 1.0E6);
            locInfo.put("time", DateTimeUtils.formatTime(time));
            if (gpsPosition.hasAccuracy()) {
                locInfo.put("accuracy", (int) Math.ceil(gpsPosition.getAccuracy()));
            }
            if (gpsPosition.hasAltitude()) {
                locInfo.put("altitude", Math.round(gpsPosition.getAltitude()));
            }

            if (!cellInfo.isEmpty()) {
                JSONArray cellJSON=new JSONArray();
                for (CellInfo cell: cellInfo) cellJSON.put(cell.toJSONObject());
                locInfo.put("cell", cellJSON);
                locInfo.put("radio", radioType);
            }

            if (!wifiInfo.isEmpty()) {
                JSONArray wifiJSON = new JSONArray();
                for (ScanResult wifi : wifiInfo) {
                    JSONObject jsonItem = new JSONObject();
                    jsonItem.put("key", wifi.BSSID);
                    jsonItem.put("frequency", wifi.frequency);
                    jsonItem.put("signal", wifi.level);
                    wifiJSON.put(jsonItem);
                }
                locInfo.put("wifi", wifiJSON);
            }
        } catch (JSONException jsonex) {
            Log.w(LOGTAG, "JSON exception", jsonex);
            return;
        }

        mReports.put(locInfo);
        sendReports(false);
    }

    public long getLastUploadTime() {
        return mLastUploadTime.get();
    }

    public long getReportsSent() {
        return mReportsSent.get();
    }

    private void sendUpdateIntent() {
        Intent i = new Intent(ScannerService.MESSAGE_TOPIC);
        i.putExtra(Intent.EXTRA_SUBJECT, "Reporter");
        mContext.sendBroadcast(i);
    }
}
