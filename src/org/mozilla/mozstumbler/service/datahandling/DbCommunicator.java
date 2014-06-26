package org.mozilla.mozstumbler.service.datahandling;

import android.content.UriMatcher;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;
import android.provider.BaseColumns;
import android.support.v4.database.DatabaseUtilsCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mozilla.mozstumbler.service.datahandling.DatabaseContract.CONTENT_AUTHORITY;

public class DbCommunicator {
        private static final String LOGTAG = DbCommunicator.class.getName();
        private static final int SYNC_OBSERVATIONS_PERIOD = 200;
        private static final int REPORTS = 1;
        private static final int REPORTS_ID = 2;
        private static final int REPORTS_SUMMARY = 3;
        private static final int SYNC_STATS = 4;

        private static final UriMatcher sUriMatcher = buildUriMatcher();
        private static HashMap<String, String> sReportSummaryProjectionMap = buildReportSummaryProjectionMap();

        private Database mDbHelper;
        private int mInsertedObservations;

        private final Context mContext;


        public SQLiteDatabase getWritableDb() {
            return mDbHelper.getWritableDatabase();
        }

        public void closeDb() {
            mDbHelper.close();
        }

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
            map.put(DatabaseContract.Reports.TOTAL_CELL_COUNT, "SUM(" + DatabaseContract.Reports.CELL_COUNT + ") AS " + DatabaseContract.Reports.TOTAL_CELL_COUNT);
            map.put(DatabaseContract.Reports.TOTAL_WIFI_COUNT, "SUM(" + DatabaseContract.Reports.WIFI_COUNT + ") AS " + DatabaseContract.Reports.TOTAL_WIFI_COUNT);
            map.put(DatabaseContract.Reports.TOTAL_OBSERVATION_COUNT, "COUNT(" + DatabaseContract.Reports._ID + ") AS " + DatabaseContract.Reports.TOTAL_OBSERVATION_COUNT);
            map.put(DatabaseContract.Reports.MAX_ID, "MAX(" + DatabaseContract.Reports._ID + ") AS " + DatabaseContract.Reports.MAX_ID);
            return map;
        }

        public DbCommunicator(Context context) {
            mDbHelper = new Database(context);
            mContext = context;
        }

        public void bulkDelete(Uri uri, ArrayList<String> idsToDelete) {
            if (sUriMatcher.match(uri) != REPORTS) {
                throw new UnsupportedOperationException("Unsupported uri for delete:" + uri.toString());
            }

            final SQLiteDatabase db = getWritableDb();
            final SQLiteStatement statement =
                    db.compileStatement("DELETE FROM " + REPORTS + " WHERE " + BaseColumns._ID + "=?");
            db.beginTransaction();
            try {
                for (String id : idsToDelete) {
                    statement.clearBindings();
                    statement.bindString(1, id);
                    statement.execute();
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            mContext.getContentResolver().notifyChange(DatabaseContract.Reports.CONTENT_URI_SUMMARY, null, false);
        }

    public void bulkUpdateOneColumn(Uri uri, String columnName, Map<String, String> idAndValuesToUpdate) {
        if (sUriMatcher.match(uri) != REPORTS) {
            throw new UnsupportedOperationException("Unsupported uri for delete:" + uri.toString());
        }

        final SQLiteDatabase db = getWritableDb();

        final SQLiteStatement statement =
                db.compileStatement("UPDATE " + REPORTS + " SET " + columnName + " =? " + " WHERE " + BaseColumns._ID + "=?");
        db.beginTransaction();
        try {

            for (String id : idAndValuesToUpdate.keySet()) {
                statement.clearBindings();
                statement.bindString(1, idAndValuesToUpdate.get(id));
                statement.bindString(2, id);
                statement.execute();
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        mContext.getContentResolver().notifyChange(DatabaseContract.Reports.CONTENT_URI_SUMMARY, null, false);
    }

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
                    cursor.setNotificationUri(mContext.getContentResolver(), DatabaseContract.Reports.CONTENT_URI_SUMMARY);
                    break;
                case SYNC_STATS:
                    cursor = getSyncStats(projection);
                    cursor.setNotificationUri(mContext.getContentResolver(), DatabaseContract.Stats.CONTENT_URI);
                    break;
                default:
                    throw new UnsupportedOperationException("Unknown uri: " + uri);
            }

            return cursor;
        }

        public String getType(Uri uri) {
            switch (sUriMatcher.match(uri)) {
                case REPORTS:
                    return DatabaseContract.Reports.CONTENT_TYPE;
                case REPORTS_ID:
                    return DatabaseContract.Reports.CONTENT_ITEM_TYPE;
                case REPORTS_SUMMARY:
                    return DatabaseContract.Reports.CONTENT_ITEM_SUMMARY_TYPE;
                case SYNC_STATS:
                    return DatabaseContract.Stats.CONTENT_ITEM_TYPE;
                default:
                    throw new UnsupportedOperationException("Unknown uri: " + uri);
            }
        }

        public Uri insert(Uri uri, ContentValues values) {
            switch (sUriMatcher.match(uri)) {
                case REPORTS:
                    long rowId = getWritableDb().insertOrThrow(Database.TABLE_REPORTS, null, values);
                    if (rowId >= 0) {
                        final ContentResolver resolver = mContext.getContentResolver();
                        mInsertedObservations = (mInsertedObservations + 1) % SYNC_OBSERVATIONS_PERIOD;
                        if (mInsertedObservations == 0) {
                            resolver.notifyChange(uri, null, true);
                        }
                        resolver.notifyChange(DatabaseContract.Reports.CONTENT_URI_SUMMARY, null, false);
                    }
                    return ContentUris.withAppendedId(uri, rowId);
                default:
                    throw new UnsupportedOperationException("Unknown uri: " + uri);
            }
        }

        public int delete(Uri uri, String selection, String[] selectionArgs) {
            final int rowsAffected;

            switch (sUriMatcher.match(uri)) {
                case REPORTS:
                    rowsAffected = getWritableDb().delete(Database.TABLE_REPORTS, selection, selectionArgs);
                    if (rowsAffected > 0) {
                        mContext.getContentResolver().notifyChange(DatabaseContract.Reports.CONTENT_URI_SUMMARY, null, false);
                    }
                    return rowsAffected;
                case REPORTS_ID:
                    String rowId = uri.getLastPathSegment();
                    if (rowId == null) throw new IllegalArgumentException("ID not defined");
                    selection = DatabaseUtilsCompat.concatenateWhere(selection, BaseColumns._ID + "=?");
                    selectionArgs = DatabaseUtilsCompat.appendSelectionArgs(selectionArgs, new String[]{rowId});
                    rowsAffected = getWritableDb().delete(Database.TABLE_REPORTS, selection, selectionArgs);
                    if (rowsAffected > 0) {
                        mContext.getContentResolver().notifyChange(DatabaseContract.Reports.CONTENT_URI_SUMMARY, null, false);
                    }
                    return rowsAffected;
                default:
                    throw new UnsupportedOperationException("Unknown uri: " + uri);
            }
        }

        public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
            int rowsAffected;

            switch (sUriMatcher.match(uri)) {
                case REPORTS:
                    rowsAffected = getWritableDb().update(Database.TABLE_REPORTS, values, selection, selectionArgs);
                    return rowsAffected;
                case REPORTS_ID:
                    String rowId = uri.getLastPathSegment();
                    if (rowId == null) throw new IllegalArgumentException("ID not defined");
                    selection = DatabaseUtilsCompat.concatenateWhere(selection, BaseColumns._ID + "=?");
                    selectionArgs = DatabaseUtilsCompat.appendSelectionArgs(selectionArgs, new String[]{rowId});
                    rowsAffected = getWritableDb().update(Database.TABLE_REPORTS, values, selection, selectionArgs);
                    return rowsAffected;
                case SYNC_STATS:
                    rowsAffected = getWritableDb().update(Database.TABLE_STATS, values, selection, selectionArgs);
                    mContext.getContentResolver().notifyChange(DatabaseContract.Stats.CONTENT_URI, null, false);
                    return rowsAffected;
            }
            return 0;
        }

        private Cursor getReports(String[] projection, String selection, String[] selectionArgs,
                                  String sortOrder, String limit) {
            final SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
            builder.setTables(Database.TABLE_REPORTS);
            return builder.query(mDbHelper.getReadableDatabase(),
                    projection, selection, selectionArgs, null, null,
                    sortOrder == null ? DatabaseContract.Reports.DEFAULT_SORT : sortOrder, limit);
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

