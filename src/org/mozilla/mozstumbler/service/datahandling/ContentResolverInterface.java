/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.datahandling;

import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.Map;

public interface ContentResolverInterface {

    public android.net.Uri insert(android.net.Uri url, android.content.ContentValues values);

    public android.content.ContentProviderResult[] applyBatch(java.lang.String authority, java.util.ArrayList<android.content.ContentProviderOperation> operations)
            throws android.os.RemoteException, android.content.OperationApplicationException;

    public int delete(android.net.Uri url, java.lang.String where, java.lang.String[] selectionArgs);

    public int update(android.net.Uri uri, android.content.ContentValues values, java.lang.String where, java.lang.String[] selectionArgs);

    public android.database.Cursor query(android.net.Uri uri, java.lang.String[] projection, java.lang.String selection, java.lang.String[] selectionArgs, java.lang.String sortOrder);

    public void updateSyncStats(long time, long totalObs, long totalCells, long totalWifis) throws RemoteException, OperationApplicationException;

    public void bulkDelete(Uri uri, ArrayList<String> idsToDelete);

    public void bulkUpdateOneColumn(Uri uri, String columnName, Map<String, String> idAndValuesToUpdate);

    public void shutdown();

    public void notifyDbIsEmpty(boolean isEmpty);

    public boolean isDbEmpty();
}
