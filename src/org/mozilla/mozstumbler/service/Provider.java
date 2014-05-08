package org.mozilla.mozstumbler.service;

import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.OperationApplicationException;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;
import android.support.v4.database.DatabaseUtilsCompat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import static org.mozilla.mozstumbler.DatabaseContract.CONTENT_AUTHORITY;
import static org.mozilla.mozstumbler.DatabaseContract.Reports;
import static org.mozilla.mozstumbler.DatabaseContract.Stats;

public class Provider extends ContentProvider {
    private static final String LOGTAG = Provider.class.getName();
    private static final int SYNC_OBSERVATIONS_PERIOD = 200;
    private static final int REPORTS = 1;
    private static final int REPORTS_ID = 2;
    private static final int REPORTS_SUMMARY = 3;
    private static final int SYNC_STATS = 4;

    private static final UriMatcher sUriMatcher = buildUriMatcher();
    private static HashMap<String, String> sReportSummaryProjectionMap = buildReportSummaryProjectionMap();

    private Database mDbHelper;
    private int mInsertedObservations;

    private static UriMatcher buildUriMatcher() {
        final UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);
        final String authority = CONTENT_AUTHORITY;

        matcher.addURI(authority, "reports", REPORTS);
        matcher.addURI(authority, "reports/summary", REPORTS_SUMMARY);
        matcher.addURI(authority, "reports/*", REPORTS_ID);
        matcher.addURI(authority, "sync_stats", SYNC_STATS);

        return matcher;
    }

    private static HashMap<String, String> buildReportSummaryProjectionMap() {
        final HashMap<String, String> map = new HashMap<String, String>();
        map.put(Reports.TOTAL_CELL_COUNT, "SUM(" + Reports.CELL_COUNT + ") AS " + Reports.TOTAL_CELL_COUNT);
        map.put(Reports.TOTAL_WIFI_COUNT, "SUM(" + Reports.WIFI_COUNT + ") AS " + Reports.TOTAL_WIFI_COUNT);
        map.put(Reports.TOTAL_OBSERVATION_COUNT, "COUNT(" + Reports._ID + ") AS " + Reports.TOTAL_OBSERVATION_COUNT);
        map.put(Reports.MAX_ID, "MAX(" + Reports._ID + ") AS " + Reports.MAX_ID);
        return map;
    }

    @Override
    public boolean onCreate() {
        mDbHelper = new Database(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Cursor cursor;
        final List<String> limitQueryParameters;
        String limit;
        String id;

        limitQueryParameters = uri.getQueryParameters("limit");
        limit = limitQueryParameters.isEmpty() ? null : limitQueryParameters.get(limitQueryParameters.size() - 1);

        switch (sUriMatcher.match(uri)) {
            case REPORTS:
                cursor = getReports(projection, selection, selectionArgs, sortOrder, limit);
                break;
            case REPORTS_ID:
                id = uri.getLastPathSegment();
                if (id == null) {
                    throw new IllegalArgumentException("report ID not defined");
                }
                selection = DatabaseUtilsCompat.concatenateWhere(selection,
                        BaseColumns._ID + "=?");
                selectionArgs = DatabaseUtilsCompat.appendSelectionArgs(selectionArgs,
                        new String[]{id});
                cursor = getReports(projection, selection, selectionArgs, sortOrder, limit);
                break;
            case REPORTS_SUMMARY:
                cursor = getReportsSummary();
                cursor.setNotificationUri(getContext().getContentResolver(), Reports.CONTENT_URI_SUMMARY);
                break;
            case SYNC_STATS:
                cursor = getSyncStats(projection);
                cursor.setNotificationUri(getContext().getContentResolver(), Stats.CONTENT_URI);
                break;
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }

        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case REPORTS:
                return Reports.CONTENT_TYPE;
            case REPORTS_ID:
                return Reports.CONTENT_ITEM_TYPE;
            case REPORTS_SUMMARY:
                return Reports.CONTENT_ITEM_SUMMARY_TYPE;
            case SYNC_STATS:
                return Stats.CONTENT_ITEM_TYPE;
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        final SQLiteDatabase db;
        long rowId;

        switch (sUriMatcher.match(uri)) {
            case REPORTS:
                db = mDbHelper.getWritableDatabase();
                rowId = db.insertOrThrow(Database.TABLE_REPORTS, null, values);
                if (rowId >= 0) {
                    final ContentResolver resolver = getContext().getContentResolver();
                    mInsertedObservations = (mInsertedObservations + 1) % SYNC_OBSERVATIONS_PERIOD;
                    if (mInsertedObservations == 0) {
                        resolver.notifyChange(uri, null, true);
                    }
                    resolver.notifyChange(Reports.CONTENT_URI_SUMMARY, null, false);
                }
                return ContentUris.withAppendedId(uri, rowId);
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        final SQLiteDatabase db;
        final int rowsAffected;
        String rowId;

        switch (sUriMatcher.match(uri)) {
            case REPORTS:
                db = mDbHelper.getWritableDatabase();
                rowsAffected = db.delete(Database.TABLE_REPORTS, selection, selectionArgs);
                if (rowsAffected > 0) {
                    getContext().getContentResolver().notifyChange(Reports.CONTENT_URI_SUMMARY, null, false);
                }
                return rowsAffected;
            case REPORTS_ID:
                db = mDbHelper.getWritableDatabase();
                rowId = uri.getLastPathSegment();
                if (rowId == null) throw new IllegalArgumentException("ID not defined");
                selection = DatabaseUtilsCompat.concatenateWhere(selection, BaseColumns._ID + "=?");
                selectionArgs = DatabaseUtilsCompat.appendSelectionArgs(selectionArgs, new String[]{rowId});
                rowsAffected = db.delete(Database.TABLE_REPORTS, selection, selectionArgs);
                if (rowsAffected > 0) {
                    getContext().getContentResolver().notifyChange(Reports.CONTENT_URI_SUMMARY, null, false);
                }
                return rowsAffected;
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        final SQLiteDatabase db;
        final int rowsAffected;
        String rowId;

        switch (sUriMatcher.match(uri)) {
            case REPORTS:
                db = mDbHelper.getWritableDatabase();
                rowsAffected = db.update(Database.TABLE_REPORTS, values, selection, selectionArgs);
                return rowsAffected;
            case REPORTS_ID:
                db = mDbHelper.getWritableDatabase();
                rowId = uri.getLastPathSegment();
                if (rowId == null) throw new IllegalArgumentException("ID not defined");
                selection = DatabaseUtilsCompat.concatenateWhere(selection, BaseColumns._ID + "=?");
                selectionArgs = DatabaseUtilsCompat.appendSelectionArgs(selectionArgs, new String[]{rowId});
                rowsAffected = db.update(Database.TABLE_REPORTS, values, selection, selectionArgs);
                return rowsAffected;
            case SYNC_STATS:
                db = mDbHelper.getWritableDatabase();
                rowsAffected = db.update(Database.TABLE_STATS, values, selection, selectionArgs);
                getContext().getContentResolver().notifyChange(Stats.CONTENT_URI, null, false);
                return rowsAffected;
        }
        return 0;
    }

    @Override
    public ContentProviderResult[] applyBatch(ArrayList<ContentProviderOperation> operations)
            throws OperationApplicationException {
        final SQLiteDatabase db = mDbHelper.getWritableDatabase();
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

    private Cursor getReports(String[] projection, String selection, String[] selectionArgs,
                              String sortOrder, String limit) {
        final SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
        builder.setTables(Database.TABLE_REPORTS);
        return builder.query(mDbHelper.getReadableDatabase(),
                projection, selection, selectionArgs, null, null,
                sortOrder == null ? Reports.DEFAULT_SORT : sortOrder, limit);
    }

    private Cursor getReportsSummary() {
        final SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
        builder.setTables(Database.TABLE_REPORTS);
        builder.setProjectionMap(sReportSummaryProjectionMap);
        return builder.query(mDbHelper.getReadableDatabase(), null, null, null, null, null, null);
    }

    private Cursor getSyncStats(String[] projection) {
        final SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
        builder.setTables(Database.TABLE_STATS);
        return builder.query(mDbHelper.getReadableDatabase(), projection, null, null, null, null, null);
    }
}
