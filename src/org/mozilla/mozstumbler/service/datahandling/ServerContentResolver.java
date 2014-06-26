package org.mozilla.mozstumbler.service.datahandling;

import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ServerContentResolver implements ContentResolverInterface {

    DbCommunicator mDbCommunicator;

    public ServerContentResolver(Context c) {
        mDbCommunicator = new DbCommunicator(c);
    }

    @Override
    public android.net.Uri insert(android.net.Uri url, android.content.ContentValues values) {
        return mDbCommunicator.insert(url, values);
    }

    @Override
    public android.content.ContentProviderResult[] applyBatch(java.lang.String authority, java.util.ArrayList<android.content.ContentProviderOperation> operations)
            throws android.os.RemoteException, android.content.OperationApplicationException {
        return null;
    }

    @Override
    public int delete(android.net.Uri url, java.lang.String where, java.lang.String[] selectionArgs) {
        return mDbCommunicator.delete(url, where, selectionArgs);
    }

    @Override
    public int update(android.net.Uri uri, android.content.ContentValues values, java.lang.String where, java.lang.String[] selectionArgs) {
        return mDbCommunicator.update(uri, values, where, selectionArgs);
    }

    @Override
    public android.database.Cursor query(android.net.Uri uri, java.lang.String[] projection, java.lang.String selection, java.lang.String[] selectionArgs, java.lang.String sortOrder) {
        return mDbCommunicator.query(uri, projection, selection, selectionArgs, sortOrder);
    }

    @Override
    public void updateSyncStats(long time, long totalObs, long totalCells, long totalWifis)
            throws RemoteException, OperationApplicationException {

        Map<String, Long> map = new HashMap<String, Long>();
        map.put(DatabaseContract.Stats.KEY_LAST_UPLOAD_TIME, time);
        map.put(DatabaseContract.Stats.KEY_OBSERVATIONS_SENT, totalObs);
        map.put(DatabaseContract.Stats.KEY_CELLS_SENT, totalCells);
        map.put(DatabaseContract.Stats.KEY_WIFIS_SENT, totalWifis);

        for (String key : map.keySet()) {
            ContentValues values = new ContentValues(2);
            values.put(DatabaseContract.Stats.KEY, key);
            values.put(DatabaseContract.Stats.VALUE, map.get(key));
            mDbCommunicator.update(DatabaseContract.Stats.CONTENT_URI, values, DatabaseContract.Stats.KEY + "=?", new String[] { key });
        }
    }

    @Override
    public void bulkDelete(Uri uri, ArrayList<String> idsToDelete) {
       mDbCommunicator.bulkDelete(uri, idsToDelete);
    }

    @Override
    public void bulkUpdateOneColumn(Uri uri, String columnName, Map<String, String> idAndValuesToUpdate) {
        mDbCommunicator.bulkUpdateOneColumn(uri, columnName, idAndValuesToUpdate);
    }

    @Override
    public void shutdown() {
        mDbCommunicator.closeDb();
    }

}
