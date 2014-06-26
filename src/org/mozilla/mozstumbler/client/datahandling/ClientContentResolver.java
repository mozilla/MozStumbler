package org.mozilla.mozstumbler.client.datahandling;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;
import org.mozilla.mozstumbler.service.datahandling.ContentResolverInterface;
import org.mozilla.mozstumbler.service.datahandling.DatabaseContract;
import java.util.ArrayList;
import java.util.Map;

public class ClientContentResolver implements ContentResolverInterface {
    private final static String LOGTAG = ContentResolver.class.getName();

    private ContentResolver mContentResolver;

    public ClientContentResolver(ContentResolver contentResolver) {
        mContentResolver = contentResolver;
    }

    @Override
    public Uri insert(Uri url, ContentValues values) {
        return mContentResolver.insert(url, values);
    }


    @Override
    public int delete(Uri url, String where, String[] selectionArgs) {
        return mContentResolver.delete(url, where, selectionArgs);
    }

    @Override
    public int update(Uri uri, ContentValues values, String where, String[] selectionArgs) {
        return mContentResolver.update(uri, values, where, selectionArgs);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return mContentResolver.query(uri, projection, selection, selectionArgs, sortOrder);
    }

    @Override
    public void updateSyncStats(long time, long totalObs, long totalCells, long totalWifis)
            throws RemoteException, OperationApplicationException {
        ArrayList<ContentProviderOperation> updateBatch = new ArrayList<ContentProviderOperation>(4);
        updateBatch.add(DatabaseContract.Stats.updateOperation(DatabaseContract.Stats.KEY_LAST_UPLOAD_TIME, time));
        updateBatch.add(DatabaseContract.Stats.updateOperation(DatabaseContract.Stats.KEY_OBSERVATIONS_SENT, totalObs));
        updateBatch.add(DatabaseContract.Stats.updateOperation(DatabaseContract.Stats.KEY_CELLS_SENT, totalCells));
        updateBatch.add(DatabaseContract.Stats.updateOperation(DatabaseContract.Stats.KEY_WIFIS_SENT, totalWifis));
        mContentResolver.applyBatch(DatabaseContract.CONTENT_AUTHORITY, updateBatch);
    }

    @Override
    public android.content.ContentProviderResult[] applyBatch(java.lang.String authority, java.util.ArrayList<android.content.ContentProviderOperation> operations)
            throws android.os.RemoteException, android.content.OperationApplicationException {
        return mContentResolver.applyBatch(authority, operations);
    }

    @Override
    public void bulkDelete(Uri uri, ArrayList<String> idsToDelete) {
        ArrayList<ContentProviderOperation> batch = new ArrayList<ContentProviderOperation>();
        for (String id : idsToDelete) {
            batch.add(ContentProviderOperation.newDelete(uri)
                            .withSelection(DatabaseContract.Reports._ID + "=?", new String[]{ id })
                            .build());
        }

        try {
            mContentResolver.applyBatch(DatabaseContract.CONTENT_AUTHORITY, batch);
        } catch (OperationApplicationException oae) {
            Log.e(LOGTAG, "increaseRetryCounter() error", oae);

        } catch (RemoteException remoteex) {
            Log.e(LOGTAG, "increaseRetryCounter() error", remoteex);
        }
    }

    @Override
    public void bulkUpdateOneColumn(Uri uri, String columnName, Map<String, String> idAndValuesToUpdate) {
        ArrayList<ContentProviderOperation> batch = new ArrayList<ContentProviderOperation>();
        for (String id : idAndValuesToUpdate.keySet()) {
            batch.add(ContentProviderOperation.newUpdate(uri)
                    .withSelection(DatabaseContract.Reports._ID + "=?", new String[]{id})
                    .withValue(columnName, idAndValuesToUpdate.get(id))
                    .build());
        }

        try {
            mContentResolver.applyBatch(DatabaseContract.CONTENT_AUTHORITY, batch);
        } catch (OperationApplicationException oae) {
            Log.e(LOGTAG, "increaseRetryCounter() error", oae);

        } catch (RemoteException remoteex) {
            Log.e(LOGTAG, "increaseRetryCounter() error", remoteex);
        }
    }

    public void shutdown() {

    }
}
