package org.mozilla.mozstumbler.sync;

import android.accounts.Account;
import android.annotation.TargetApi;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.SyncResult;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

import org.mozilla.mozstumbler.BuildConfig;
import org.mozilla.mozstumbler.NetworkUtils;
import org.mozilla.mozstumbler.preferences.Prefs;
import org.mozilla.mozstumbler.provider.DatabaseContract;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import static org.mozilla.mozstumbler.provider.DatabaseContract.*;

public class SyncAdapter extends AbstractThreadedSyncAdapter {
    private static final String NICKNAME_HEADER = "X-Nickname";
    private static final String USER_AGENT_HEADER = "User-Agent";
    static final String SYNC_EXTRAS_IGNORE_WIFI_STATUS = "org.mozilla.mozstumbler.sync.ignore_wifi_status";

    private static final String LOGTAG = SyncAdapter.class.getName();
    private static final boolean DBG = BuildConfig.DEBUG;
    private static final String LOCATION_URL = "https://location.services.mozilla.com/v1/submit";
    private static final int MAX_RETRY_COUNT = 3;
    private static final URL sURL;

    private final ContentResolver mContentResolver;
    private final Prefs mPrefs;
    private final String mUserAgentString;
    private String mNickname;

    static {
        try {
            sURL = new URL(LOCATION_URL + "?key=" + BuildConfig.MOZILLA_API_KEY);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        mPrefs = new Prefs(context);
        mContentResolver = context.getContentResolver();
        mUserAgentString = NetworkUtils.getUserAgentString(context);
    }

    /**
     * Set up the sync adapter. This form of the
     * constructor maintains compatibility with Android 3.0
     * and later platform versions
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public SyncAdapter(
            Context context,
            boolean autoInitialize,
            boolean allowParallelSyncs) {
        super(context, autoInitialize, allowParallelSyncs);
        mPrefs = new Prefs(context);
        mContentResolver = context.getContentResolver();
        mUserAgentString = NetworkUtils.getUserAgentString(context);
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority,
                              ContentProviderClient provider, SyncResult syncResult) {
        final boolean ignoreNetworkStatus = extras.getBoolean(SYNC_EXTRAS_IGNORE_WIFI_STATUS, false);
        uploadReports(ignoreNetworkStatus, syncResult);
        Log.i(LOGTAG, "Network synchronization complete");
    }

    private void uploadReports(boolean ignoreNetworkStatus, SyncResult syncResult) {
        int maxRequestCount;
        long totalObservations = 0;
        long totalCells = 0;
        long totalWifis = 0;

        if (!ignoreNetworkStatus && mPrefs.getWifi() && !NetworkUtils.isWifiAvailable(getContext())) {
            Log.d(LOGTAG, "not on WiFi, not sending");
            syncResult.stats.numIoExceptions += 1;
            return;
        }

        maxRequestCount = getReportsCount();
        if (maxRequestCount == 0) {
            return;
        }
        // In case of new reports during upload
        maxRequestCount += 2;

        mNickname = mPrefs.getNickname();
        for (int reqNo = 0; reqNo < maxRequestCount; ++reqNo) {
            long reportId;
            byte body[];
            int retry;
            int observations;
            int cells;
            int wifis;

            Cursor reportCursor = mContentResolver.query(
                    Reports.CONTENT_URI.buildUpon().appendQueryParameter("limit", "1").build(),
                    null, null, null, Reports._ID);
            if (reportCursor == null) {
                break;
            }

            try {
                if (!reportCursor.moveToFirst()) {
                    break;
                }
                reportId = reportCursor.getLong(reportCursor.getColumnIndex(Reports._ID));
                body = reportCursor.getBlob(reportCursor.getColumnIndex(Reports.REPORT));
                retry = reportCursor.getInt(reportCursor.getColumnIndex(Reports.RETRY_NUMBER));
                observations = reportCursor.getInt(reportCursor.getColumnIndex(Reports.OBSERVATION_COUNT));
                cells = reportCursor.getInt(reportCursor.getColumnIndex(Reports.CELL_COUNT));
                wifis = reportCursor.getInt(reportCursor.getColumnIndex(Reports.WIFI_COUNT));
            } finally {
                reportCursor.close();
            }

            try {
                uploadReport(body);
                mContentResolver.delete(Reports.buildReportUri(reportId), null, null);
                syncResult.stats.numDeletes += 1;
                syncResult.stats.numEntries += 1;
                totalObservations += observations;
                totalWifis += wifis;
                totalCells += cells;
            } catch (IOException e) {
                boolean isTemporary;

                Log.e(LOGTAG, "IO exception ", e);
                syncResult.stats.numIoExceptions += 1;
                isTemporary = !(e instanceof HttpErrorException) || ((HttpErrorException) e).isTemporary();

                if (isTemporary) {
                    retry += 1;
                    if (retry >= MAX_RETRY_COUNT) {
                        Log.e(LOGTAG, "Upload failed " + MAX_RETRY_COUNT + " times");
                        mContentResolver.delete(Reports.buildReportUri(reportId), null, null);
                        syncResult.stats.numDeletes += 1;
                    } else {
                        Log.e(LOGTAG, "Upload failed. Retrying at the next sync");
                        ContentValues values = new ContentValues(1);
                        values.put(Reports.RETRY_NUMBER, retry);
                        mContentResolver.update(Reports.buildReportUri(reportId),
                                values, null, null);
                        syncResult.stats.numUpdates += 1;
                    }
                } else {
                    Log.e(LOGTAG, "Upload failed");
                    mContentResolver.delete(Reports.buildReportUri(reportId), null, null);
                    syncResult.stats.numDeletes += 1;
                }
            }
        }

        try {
            updateSyncStats(totalObservations, totalCells, totalWifis);
        } catch (RemoteException e) {
            Log.e(LOGTAG, "Update sync stats failed", e);
            syncResult.stats.numIoExceptions += 1;
        } catch (OperationApplicationException e) {
            Log.e(LOGTAG, "Update sync stats failed", e);
            syncResult.stats.numIoExceptions += 1;
            e.printStackTrace();
        }
    }

    private int getReportsCount() {
        Cursor statsCursor = mContentResolver.query(Reports.CONTENT_URI_SUMMARY, null, null, null, null);
        try {
            statsCursor.moveToFirst();
            return statsCursor.getInt(statsCursor.getColumnIndex(Reports.TOTAL_REPORT_COUNT));
        } finally {
            if (statsCursor != null) statsCursor.close();
        }
    }

    private void updateSyncStats(long observations, long cells, long wifis) throws RemoteException,
            OperationApplicationException {
        Cursor syncStats;
        long totalObservations = observations;
        long totalCells = cells;
        long totalWifis = wifis;

        if (observations == 0 && cells == 0 && wifis == 0) {
            return;
        }
        syncStats = mContentResolver.query(Stats.CONTENT_URI, null, null, null, null);
        if (syncStats != null) {
            try {
                while (syncStats.moveToNext()) {
                    String key = syncStats.getString(syncStats.getColumnIndex(Stats.KEY));
                    String value = syncStats.getString(syncStats.getColumnIndex(Stats.VALUE));

                    if (Stats.KEY_OBSERVATIONS_SENT.equals(key)) {
                        totalObservations += Long.valueOf(value);
                    } else if (Stats.KEY_CELLS_SENT.equals(key)) {
                        totalCells += Long.valueOf(value);
                    } else if (Stats.KEY_WIFIS_SENT.equals(key)) {
                        totalWifis += Long.valueOf(value);
                    }
                }
            } finally {
                syncStats.close();
            }
        }

        ArrayList<ContentProviderOperation> updateBatch = new ArrayList<ContentProviderOperation>(4);
        updateBatch.add(Stats.updateOperation(Stats.KEY_LAST_UPLOAD_TIME, System.currentTimeMillis()));
        updateBatch.add(Stats.updateOperation(Stats.KEY_OBSERVATIONS_SENT, totalObservations));
        updateBatch.add(Stats.updateOperation(Stats.KEY_CELLS_SENT, totalCells));
        updateBatch.add(Stats.updateOperation(Stats.KEY_WIFIS_SENT, totalWifis));
        mContentResolver.applyBatch(DatabaseContract.CONTENT_AUTHORITY, updateBatch);
    }

    private int uploadReport(byte body[]) throws IOException {
        HttpURLConnection urlConnection = (HttpURLConnection) sURL.openConnection();
        try {
            urlConnection.setDoOutput(true);
            urlConnection.setRequestProperty(USER_AGENT_HEADER, mUserAgentString);
            urlConnection.setRequestProperty("Content-Type", "application/json");

            // Workaround for a bug in Android HttpURLConnection. When the library
            // reuses a stale connection, the connection may fail with an EOFException
            if (Build.VERSION.SDK_INT > 13 && Build.VERSION.SDK_INT < 19) {
                urlConnection.setRequestProperty("Connection", "Close");
            }

            if (mNickname != null) {
                urlConnection.setRequestProperty(NICKNAME_HEADER, mNickname);
            }

            urlConnection.setFixedLengthStreamingMode(body.length);
            OutputStream out = new BufferedOutputStream(urlConnection.getOutputStream());
            out.write(body);
            out.flush();

            if (DBG) {
                Log.d(LOGTAG, "uploaded wrapperData: " + new String(body, "UTF-8") + " to " + sURL.toString());
            }

            int code = urlConnection.getResponseCode();

            if (DBG) Log.e(LOGTAG, "urlConnection returned " + code);

            if (code != 204) {
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
                    }
                }
            }
            if (!(code >= 200 && code <= 299)) {
                throw new HttpErrorException(code);
            }

            return code;
        } finally {
            urlConnection.disconnect();
        }
    }

    private static class HttpErrorException extends IOException {
        private static final long serialVersionUID = -5404095858043243126L;
        public final int responseCode;

        public HttpErrorException(int responseCode) {
            super();
            this.responseCode = responseCode;
        }

        public boolean isTemporary() {
            return responseCode >= 500 && responseCode <= 599;
        }

    }
}
