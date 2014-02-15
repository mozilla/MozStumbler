package org.mozilla.mozstumbler;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.util.Log;

import org.mozilla.mozstumbler.cellscanner.CellInfo;
import org.mozilla.mozstumbler.cellscanner.CellScanner;
import org.mozilla.mozstumbler.preferences.Prefs;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.mozstumbler.provider.DatabaseContract;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

final class Reporter extends BroadcastReceiver {
    private static final String LOGTAG          = Reporter.class.getName();

    /**
     *  Maximum number of observations in a single report
     */
    private static final int RECORD_BATCH_SIZE  = 40;

    /**
     * The maximum time of observation
     */
    private static final int REPORTER_WINDOW  = 24 * 60 * 60 * 1000; //ms

    /**
     * The maximum number of Wi-Fi access points in a single observation
     */
    private static final int WIFI_COUNT_WATERMARK = 100;

    /**
     * The maximum number of cells in a single observation
     */
    private static final int CELLS_COUNT_WATERMARK = 50;

    private final Context       mContext;
    private final AtomicLong    mLastUploadTime = new AtomicLong();
    private final Report mReport = new Report();

    private Location            mGpsPosition;
    private final Map<String, ScanResult> mWifiData = new HashMap<String, ScanResult>();
    private final Map<String, CellInfo> mCellData = new HashMap<String, CellInfo>();

    Reporter(Context context) {
        mContext = context;
        resetData();
        mContext.registerReceiver(this, new IntentFilter(ScannerService.MESSAGE_TOPIC));
    }

    private void resetData() {
        mWifiData.clear();
        mCellData.clear();
        mGpsPosition = null;
    }

    void flush() {
        reportCollectedLocation();
        queueReport(true);
    }

    void shutdown() {
        Log.d(LOGTAG, "shutdown");
        flush();
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

        if (mGpsPosition != null && Math.abs(time - mGpsPosition.getTime()) > REPORTER_WINDOW) {
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
            mGpsPosition = intent.getParcelableExtra(GPSScanner.GPS_SCANNER_ARG_LOCATION);
        } else {
            Log.d(LOGTAG, "Intent ignored with Subject: " + subject);
            return; // Intent not aimed at the Reporter (it is possibly for UI instead)
        }

        if (mGpsPosition != null &&
            (mWifiData.size() > WIFI_COUNT_WATERMARK || mCellData.size() > CELLS_COUNT_WATERMARK)) {
            reportCollectedLocation();
        }
    }

    private void putWifiResults(List<ScanResult> results) {
        if (mGpsPosition == null) {
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
        if (mGpsPosition == null) {
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
        if (mGpsPosition == null) {
            return;
        }

        Collection<CellInfo> cells = mCellData.values();
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
                Collection<CellInfo> cellInfo = groupByRadio.get(radio);
                saveCellObservation(radio, cellInfo);
            }
            mCellData.clear();
        }

        Collection<ScanResult> wifis = mWifiData.values();
        if (!wifis.isEmpty()) {
            saveWifiObservation(wifis);
            mWifiData.clear();
        }
    }

    private JSONObject createObservation() throws JSONException {
        JSONObject locInfo = new JSONObject();

        locInfo.put("lat", Math.floor(mGpsPosition.getLatitude() * 1.0E6) / 1.0E6);
        locInfo.put("lon", Math.floor(mGpsPosition.getLongitude() * 1.0E6) / 1.0E6);
        locInfo.put("time", DateTimeUtils.formatTime(mGpsPosition.getTime()));

        if (mGpsPosition.hasAccuracy()) {
            locInfo.put("accuracy", (int) Math.ceil(mGpsPosition.getAccuracy()));
        }

        if (mGpsPosition.hasAltitude()) {
            locInfo.put("altitude", Math.round(mGpsPosition.getAltitude()));
        }

        return locInfo;
    }

    private void saveCellObservation(String radioType, Collection<CellInfo> cellInfo) {
        if (cellInfo.isEmpty()) {
            throw new IllegalArgumentException("cellInfo must not be empty");
        }

        try {
            JSONArray cellJSON = new JSONArray();
            for (CellInfo cell : cellInfo) {
                cellJSON.put(cell.toJSONObject());
            }

            JSONObject locInfo = createObservation();
            locInfo.put("cell", cellJSON);
            locInfo.put("radio", radioType);
            saveObservation(locInfo, 0, cellInfo.size());
        } catch (JSONException jsonex) {
            Log.w(LOGTAG, "JSON exception", jsonex);
        }
    }

    private void saveWifiObservation(Collection<ScanResult> wifiInfo) {
        if (wifiInfo.isEmpty()) {
            throw new IllegalArgumentException("wifiInfo must not be empty");
        }

        try {
            JSONArray wifiJSON = new JSONArray();
            for (ScanResult wifi : wifiInfo) {
                JSONObject jsonItem = new JSONObject();
                jsonItem.put("key", wifi.BSSID);
                jsonItem.put("frequency", wifi.frequency);
                jsonItem.put("signal", wifi.level);
                wifiJSON.put(jsonItem);
            }

            JSONObject locInfo = createObservation();
            locInfo.put("wifi", wifiJSON);
            saveObservation(locInfo, wifiInfo.size(), 0);
        } catch (JSONException jsonex) {
            Log.w(LOGTAG, "JSON exception", jsonex);
        }
    }

    private void saveObservation(JSONObject locInfo, int wifiCount, int cellCount) {
        mReport.putObservation(locInfo, wifiCount, cellCount);
        queueReport(false);
    }

    public void queueReport(boolean force) {
        Log.d(LOGTAG, "queueReport: force=" + force);
        int count = mReport.length();
        if (count == 0) {
            Log.d(LOGTAG, "no observations to send");
            return;
        }
        if (count < RECORD_BATCH_SIZE && !force && mLastUploadTime.get() > 0) {
            Log.d(LOGTAG, "batch count not reached, and !force");
            return;
        }

        try {
            ContentValues values = mReport.asInsertContentValues();
            mContext.getContentResolver().insert(DatabaseContract.Reports.CONTENT_URI, values);
            mLastUploadTime.set(System.currentTimeMillis());
            mReport.reset();
        } catch (JSONException jsonex) {
            Log.e(LOGTAG, "error wrapping data as a batch", jsonex);
            mReport.reset();
        }
    }

    /**
     * The report is a collection of observations
     */
    private static class Report {
        private JSONArray mObservations;
        private int mWifiCount;
        private int mCellCount;

        public Report() {
            reset();
        }

        public void reset() {
            mObservations = new JSONArray();
            mCellCount = 0;
            mWifiCount = 0;
        }

        public void putObservation(JSONObject observation, int cellCount, int wifiCount) {
            mObservations.put(observation);
            mWifiCount += wifiCount;
            mCellCount += cellCount;
        }

        public JSONObject asJsonObject() throws JSONException {
            JSONObject wrapper = new JSONObject();
            wrapper.put("items", mObservations);
            return wrapper;
        }

        public ContentValues asInsertContentValues() throws JSONException {
            byte[] bytes = asJsonObject().toString().getBytes();

            ContentValues values = new ContentValues();
            values.put(DatabaseContract.Reports.REPORT, bytes);
            values.put(DatabaseContract.Reports.OBSERVATION_COUNT, mObservations.length());
            values.put(DatabaseContract.Reports.WIFI_COUNT, mCellCount);
            values.put(DatabaseContract.Reports.CELL_COUNT, mWifiCount);
            return values;
        }

        public int length() {
            return mObservations.length();
        }
    }
}
