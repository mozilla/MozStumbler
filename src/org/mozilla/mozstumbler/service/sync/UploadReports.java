/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.sync;

import android.content.OperationApplicationException;
import android.content.SyncResult;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.SharedConstants;
import org.mozilla.mozstumbler.service.datahandling.ContentResolverInterface;
import org.mozilla.mozstumbler.service.datahandling.DatabaseContract;
import org.mozilla.mozstumbler.service.utils.DateTimeUtils;
import org.mozilla.mozstumbler.service.utils.NetworkUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class UploadReports {
    private static final int REQUEST_BATCH_SIZE = 50;
    private static final int MAX_RETRY_COUNT = 3;

    private final ContentResolverInterface mContentResolver;

    static final String LOGTAG = UploadReports.class.getName();

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

    public UploadReports() {
        mContentResolver = SharedConstants.stumblerContentResolver;
    }

    public void uploadReports(boolean ignoreNetworkStatus, SyncResult syncResult) {
        long uploadedObservations = 0;
        long uploadedCells = 0;
        long uploadedWifis = 0;
        long queueMinId, queueMaxId;

        if (!ignoreNetworkStatus && Prefs.getInstance().getUseWifiOnly() && !NetworkUtils.getInstance().isWifiAvailable()) {
            if (SharedConstants.isDebug) Log.d(LOGTAG, "not on WiFi, not sending");
            syncResult.stats.numIoExceptions += 1;
            return;
        }

        Uri uri = DatabaseContract.Reports.CONTENT_URI.buildUpon()
                .appendQueryParameter("limit", String.valueOf(REQUEST_BATCH_SIZE)).build();
        queueMinId = 0;
        queueMaxId = getMaxId();
        Submitter submitter = new Submitter();
        while (queueMinId < queueMaxId) {
            BatchRequestStats batch = null;
            Cursor cursor = mContentResolver.query(uri, null,
                    DatabaseContract.Reports._ID + " > ? AND " + DatabaseContract.Reports._ID + " <= ?",
                    new String[]{String.valueOf(queueMinId), String.valueOf(queueMaxId)},
                    DatabaseContract.Reports._ID);

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

            queueMinId = batch.maxId;
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
        Cursor c = mContentResolver.query(DatabaseContract.Reports.CONTENT_URI_SUMMARY,
                new String[]{DatabaseContract.Reports.MAX_ID}, null, null, null);
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

        int columnId = cursor.getColumnIndex(DatabaseContract.Reports._ID);
        int columnTime = cursor.getColumnIndex(DatabaseContract.Reports.TIME);
        int columnLat = cursor.getColumnIndex(DatabaseContract.Reports.LAT);
        int columnLon = cursor.getColumnIndex(DatabaseContract.Reports.LON);
        int columnAltitude = cursor.getColumnIndex(DatabaseContract.Reports.ALTITUDE);
        int columnAccuracy = cursor.getColumnIndex(DatabaseContract.Reports.ACCURACY);
        int columnRadio = cursor.getColumnIndex(DatabaseContract.Reports.RADIO);
        int columnCell = cursor.getColumnIndex(DatabaseContract.Reports.CELL);
        int columnWifi = cursor.getColumnIndex(DatabaseContract.Reports.WIFI);
        int columnCellCount = cursor.getColumnIndex(DatabaseContract.Reports.CELL_COUNT);
        int columnWifiCount = cursor.getColumnIndex(DatabaseContract.Reports.WIFI_COUNT);

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
        mContentResolver.delete(DatabaseContract.Reports.CONTENT_URI, DatabaseContract.Reports._ID + " BETWEEN ? AND ?",
                new String[]{String.valueOf(minId), String.valueOf(maxId)});
    }

    private void increaseRetryCounter(Cursor cursor, SyncResult result) {
        ArrayList<String> idsToDelete = new ArrayList<String>();
        Map<String, String> idAndValuesToUpdate = new HashMap<String, String>();

        cursor.moveToPosition(-1);
        int columnId = cursor.getColumnIndex(DatabaseContract.Reports._ID);
        int columnRetry = cursor.getColumnIndex(DatabaseContract.Reports.RETRY_NUMBER);
        while (cursor.moveToNext()) {
            int retry = cursor.getInt(columnRetry) + 1;
            if (retry >= MAX_RETRY_COUNT) {
                idsToDelete.add(cursor.getString(columnId));
            } else {
                idAndValuesToUpdate.put(cursor.getString(columnId), "" + retry);
            }
        }

        mContentResolver.bulkDelete(DatabaseContract.Reports.CONTENT_URI, idsToDelete);
        mContentResolver.bulkUpdateOneColumn(DatabaseContract.Reports.CONTENT_URI,
                DatabaseContract.Reports.RETRY_NUMBER, idAndValuesToUpdate);
        result.stats.numDeletes += idsToDelete.size();
        result.stats.numUpdates += idAndValuesToUpdate.size();
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
        syncStats = mContentResolver.query(DatabaseContract.Stats.CONTENT_URI, null, null, null, null);
        if (syncStats != null) {
            try {
                while (syncStats.moveToNext()) {
                    String key = syncStats.getString(syncStats.getColumnIndex(DatabaseContract.Stats.KEY));
                    String value = syncStats.getString(syncStats.getColumnIndex(DatabaseContract.Stats.VALUE));

                    if (DatabaseContract.Stats.KEY_OBSERVATIONS_SENT.equals(key)) {
                        totalObservations += Long.valueOf(value);
                    } else if (DatabaseContract.Stats.KEY_CELLS_SENT.equals(key)) {
                        totalCells += Long.valueOf(value);
                    } else if (DatabaseContract.Stats.KEY_WIFIS_SENT.equals(key)) {
                        totalWifis += Long.valueOf(value);
                    }
                }
            } finally {
                syncStats.close();
            }
        }

        mContentResolver.updateSyncStats(System.currentTimeMillis(), totalObservations, totalCells, totalWifis);
    }
}
