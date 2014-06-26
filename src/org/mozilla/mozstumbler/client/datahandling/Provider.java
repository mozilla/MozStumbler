package org.mozilla.mozstumbler.client.datahandling;

import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentValues;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import org.mozilla.mozstumbler.service.datahandling.DbCommunicator;

import java.util.ArrayList;

public class Provider extends ContentProvider {
    private static final String LOGTAG = Provider.class.getName();

    private DbCommunicator mDbCommunicator;

    @Override
    public boolean onCreate() {
        mDbCommunicator = new DbCommunicator(getContext());
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return mDbCommunicator.getType(uri);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return mDbCommunicator.query(uri, projection, selection, selectionArgs, sortOrder);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return mDbCommunicator.insert(uri, values);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return mDbCommunicator.delete(uri, selection, selectionArgs);

    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return mDbCommunicator.update(uri, values, selection, selectionArgs);
    }

    public ContentProviderResult[] applyBatch(ArrayList<ContentProviderOperation> operations)
            throws OperationApplicationException {
        final SQLiteDatabase db = mDbCommunicator.getWritableDb();
        db.beginTransaction();
        try {
            final int size = operations.size();
            final ContentProviderResult[] results = new ContentProviderResult[size];
            for (int i = 0; i < size; i++) {
                results[i] = operations.get(i).apply(this, results, i);
            }
            db.setTransactionSuccessful();
            return results;
        } finally {
            db.endTransaction();
        }
    }

}
