package org.mozilla.mozstumbler.service.sync;

import android.accounts.Account;
import android.annotation.TargetApi;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.SyncResult;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import java.util.ArrayList;
import java.util.Collections;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.mozstumbler.BuildConfig;
import org.mozilla.mozstumbler.DateTimeUtils;
import org.mozilla.mozstumbler.NetworkUtils;
import org.mozilla.mozstumbler.Prefs;
import org.mozilla.mozstumbler.DatabaseContract;
import org.mozilla.mozstumbler.SharedConstants;
import static org.mozilla.mozstumbler.DatabaseContract.Reports;
import static org.mozilla.mozstumbler.DatabaseContract.Stats;

public class SyncAdapter extends AbstractThreadedSyncAdapter {
    private static final String LOGTAG = SyncAdapter.class.getName();
    private static final boolean DBG = BuildConfig.DEBUG;
    private static final int REQUEST_BATCH_SIZE = 50;
    private static final int MAX_RETRY_COUNT = 3;

    private final ContentResolver mContentResolver;
    private final Prefs mPrefs;


    private static class BatchRequestStats {
        final byte[] body;
        final int wifis;
        final int cells;
        final int observations;
        final long minId;
        final long maxId;

        BatchRequestStats(byte[] body, int wifis, int cells, int observations, long minId, long maxId) {
            this.body = body;
            this.wifis = wifis;
            this.cells = cells;
            this.observations = observations;
            this.minId = minId;
            this.maxId = maxId;
        }
    }

    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        mPrefs = new Prefs(context);
        mContentResolver = context.getContentResolver();
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
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority,
                              ContentProviderClient provider, SyncResult syncResult) {
        final boolean ignoreNetworkStatus = extras.getBoolean(SharedConstants.SYNC_EXTRAS_IGNORE_WIFI_STATUS, false);
        uploadReports(ignoreNetworkStatus, syncResult);
        Log.i(LOGTAG, "Network synchronization complete");
    }

    private void uploadReports(boolean ignoreNetworkStatus, SyncResult syncResult) {
        long uploadedObservations = 0;
        long uploadedCells = 0;
        long uploadedWifis = 0;
        long queueMinId, queueMaxId;

        if (!ignoreNetworkStatus && mPrefs.getWifi() && !NetworkUtils.isWifiAvailable(getContext())) {
            Log.d(LOGTAG, "not on WiFi, not sending");
            syncResult.stats.numIoExceptions += 1;
            return;
        }

        Uri uri = Reports.CONTENT_URI.buildUpon()
                .appendQueryParameter("limit", String.valueOf(REQUEST_BATCH_SIZE))
                .build();
        queueMinId = 0;
        queueMaxId = getMaxId();
        Submitter submitter = new Submitter(getContext());
        for (; queueMinId < queueMaxId; ) {
            BatchRequestStats batch = null;
            Cursor cursor = mContentResolver.query(uri, null,
                    Reports._ID + " > ? AND " + Reports._ID + " <= ?",
                    new String[]{String.valueOf(queueMinId), String.valueOf(queueMaxId)},
                    Reports._ID);
            if (cursor == null) {
                break;
            }

            try {
                batch = getRequestBody(cursor);
                if (batch == null) {
                    break;
                }


                if (submitter.cleanSend(batch.body)) {
                    deleteObservations(batch.minId, batch.maxId);
                    uploadedObservations += batch.observations;
                    uploadedWifis += batch.wifis;
                    uploadedCells += batch.cells;
                } else {
                    syncResult.stats.numIoExceptions += 1;
                    increaseRetryCounter(cursor, syncResult);
                }
            } finally {
                cursor.close();
                submitter.close();
            }

            if (batch != null) {
                queueMinId = batch.maxId;
            } else {
                queueMinId += REQUEST_BATCH_SIZE;
            }
        }

        try {
            updateSyncStats(uploadedObservations, uploadedCells, uploadedWifis);
        } catch (RemoteException e) {
            Log.e(LOGTAG, "Update sync stats failed", e);
            syncResult.stats.numIoExceptions += 1;
        } catch (OperationApplicationException e) {
            Log.e(LOGTAG, "Update sync stats failed", e);
            syncResult.stats.numIoExceptions += 1;
            e.printStackTrace();
        }
    }

    private long getMaxId() {
        Cursor c = mContentResolver.query(Reports.CONTENT_URI_SUMMARY,
                new String[]{Reports.MAX_ID}, null, null, null);
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    return c.getLong(0);
                }
            } finally {
                c.close();
            }
        }
        return 0;
    }

    private BatchRequestStats getRequestBody(Cursor cursor) {
        int wifiCount = 0;
        int cellCount = 0;
        JSONArray items = new JSONArray();

        int columnId = cursor.getColumnIndex(Reports._ID);
        int columnTime = cursor.getColumnIndex(Reports.TIME);
        int columnLat = cursor.getColumnIndex(Reports.LAT);
        int columnLon = cursor.getColumnIndex(Reports.LON);
        int columnAltitude = cursor.getColumnIndex(Reports.ALTITUDE);
        int columnAccuracy = cursor.getColumnIndex(Reports.ACCURACY);
        int columnRadio = cursor.getColumnIndex(Reports.RADIO);
        int columnCell = cursor.getColumnIndex(Reports.CELL);
        int columnWifi = cursor.getColumnIndex(Reports.WIFI);
        int columnCellCount = cursor.getColumnIndex(Reports.CELL_COUNT);
        int columnWifiCount = cursor.getColumnIndex(Reports.WIFI_COUNT);

        cursor.moveToPosition(-1);
        try {
            while (cursor.moveToNext()) {
                JSONObject item = new JSONObject();
                item.put("time", DateTimeUtils.formatTime(DateTimeUtils.removeDay(cursor.getLong(columnTime))));
                item.put("lat", cursor.getDouble(columnLat));
                item.put("lon", cursor.getDouble(columnLon));
                if (!cursor.isNull(columnAltitude)) {
                    item.put("altitude", cursor.getInt(columnAltitude));
                }
                if (!cursor.isNull(columnAccuracy)) {
                    item.put("accuracy", cursor.getInt(columnAccuracy));
                }
                item.put("radio", cursor.getString(columnRadio));
                item.put("cell", new JSONArray(cursor.getString(columnCell)));
                item.put("wifi", new JSONArray(cursor.getString(columnWifi)));
                items.put(item);

                cellCount += cursor.getInt(columnCellCount);
                wifiCount += cursor.getInt(columnWifiCount);
            }
        } catch (JSONException jsonex) {
            Log.e(LOGTAG, "JSONException", jsonex);
        }

        if (items.length() == 0) {
            return null;
        }

        long minId, maxId;
        cursor.moveToFirst();
        minId = cursor.getLong(columnId);
        cursor.moveToLast();
        maxId = cursor.getLong(columnId);

        JSONObject wrapper = new JSONObject(Collections.singletonMap("items", items));
        return new BatchRequestStats(wrapper.toString().getBytes(),
                wifiCount, cellCount, items.length(), minId, maxId);
    }

    private void deleteObservations(long minId, long maxId) {
        mContentResolver.delete(Reports.CONTENT_URI, Reports._ID + " BETWEEN ? AND ?",
                new String[]{String.valueOf(minId), String.valueOf(maxId)});
    }

    private void increaseRetryCounter(Cursor cursor, SyncResult result) {
        ArrayList<ContentProviderOperation> batch = new ArrayList<ContentProviderOperation>();
        int updates = 0;
        int deletes = 0;

        cursor.moveToPosition(-1);
        int columnId = cursor.getColumnIndex(Reports._ID);
        int columnRetry = cursor.getColumnIndex(Reports.RETRY_NUMBER);
        while (cursor.moveToNext()) {
            int retry = cursor.getInt(columnRetry) + 1;
            if (retry >= MAX_RETRY_COUNT) {
                batch.add(ContentProviderOperation.newDelete(Reports.CONTENT_URI)
                        .withSelection(Reports._ID + "=?", new String[]{cursor.getString(columnId)})
                        .build()
                );
                deletes += 1;
            } else {
                batch.add(ContentProviderOperation.newUpdate(Reports.CONTENT_URI)
                        .withSelection(Reports._ID + "=?", new String[]{cursor.getString(columnId)})
                        .withValue(Reports.RETRY_NUMBER, retry)
                        .build());
                updates += 1;
            }
        }

        try {
            mContentResolver.applyBatch(DatabaseContract.CONTENT_AUTHORITY, batch);
            result.stats.numDeletes += deletes;
            result.stats.numUpdates += updates;
        } catch (OperationApplicationException oae) {
            Log.e(LOGTAG, "increaseRetryCounter() error", oae);
            result.databaseError = true;

        } catch (RemoteException remoteex) {
            Log.e(LOGTAG, "increaseRetryCounter() error", remoteex);
            result.databaseError = true;
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
}
