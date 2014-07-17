/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

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

    public interface DatabaseIsEmptyTracker {
        public void databaseIsEmpty(boolean isEmpty);
    }

    DbCommunicator mDbCommunicator;
    DatabaseIsEmptyTracker mTracker;

    public ServerContentResolver(Context c, DatabaseIsEmptyTracker tracker) {
        mDbCommunicator = new DbCommunicator(c);
        mTracker = tracker;
    }

    @Override
    public android.net.Uri insert(android.net.Uri url, android.content.ContentValues values) {
        Uri uri = mDbCommunicator.insert(url, values);
        if (uri != null) {
            notifyDbIsEmpty(false);
        }
        return uri;
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

    @Override
    public void notifyDbIsEmpty(boolean isEmpty) {
        if (mTracker != null)
            mTracker.databaseIsEmpty(isEmpty);
    }

    public boolean isDbEmpty() {
        return mDbCommunicator.isDbEmpty();
    }


}
