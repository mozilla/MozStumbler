package org.mozilla.mozstumbler.provider;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.net.Uri;
import android.provider.BaseColumns;

public final class DatabaseContract {
    public static final String CONTENT_AUTHORITY = "org.mozilla.mozstumbler.provider";
    private static final Uri BASE_CONTENT_URI = Uri.parse("content://" + CONTENT_AUTHORITY);

    private static final String PATH_REPORTS = "reports";
    private static final String PATH_REPORT_SUMMARY = "summary";
    private static final String PATH_SYNC_STATS = "sync_stats";

    interface ReportsColumns {
        String REPORT = "report";
        String OBSERVATION_COUNT = "observation_count";
        String CELL_COUNT = "cell_count";
        String WIFI_COUNT = "wifi_count";
        String RETRY_NUMBER = "retry";
    }

    interface StatsColumns {
        String KEY = "key";
        String VALUE = "value";
    }

    public static class Reports implements ReportsColumns, BaseColumns {
        public static final Uri CONTENT_URI =
                BASE_CONTENT_URI.buildUpon().appendPath(PATH_REPORTS).build();
        public static final Uri CONTENT_URI_SUMMARY =
                BASE_CONTENT_URI.buildUpon().appendPath(PATH_REPORTS).appendPath(PATH_REPORT_SUMMARY).build();
        public static final String CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE
                + "/vnd.mozstumbler.reports";
        public static final String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE
                + "vnd.mozstumbler.reports";
        public static final String CONTENT_ITEM_SUMMARY_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE
                + "vnd.mozstumbler.reports_summary";
        public static final String DEFAULT_SORT = _ID;

        public static final String TOTAL_REPORT_COUNT = "total_report_count";
        public static final String TOTAL_OBSERVATION_COUNT = "total_observation_count";
        public static final String TOTAL_CELL_COUNT = "total_cell_count";
        public static final String TOTAL_WIFI_COUNT = "total_wifi_count";

        public static Uri buildReportUri(long reportId) {
            return ContentUris.withAppendedId(CONTENT_URI, reportId);
        }
    }

    public static class Stats implements StatsColumns {
        public static final Uri CONTENT_URI =
                BASE_CONTENT_URI.buildUpon().appendPath(PATH_SYNC_STATS).build();
        public static final String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE
                + "vnd.mozstumbler.stats";

        public static final String KEY_LAST_UPLOAD_TIME = "last_upload_time";

        public static final String KEY_OBSERVATIONS_SENT = "observations_sent";

        public static final String KEY_WIFIS_SENT = "wifis_sent";

        public static final String KEY_CELLS_SENT = "cells_sent";

        public static ContentValues values(String key, String value) {
            ContentValues values = new ContentValues(2);
            values.put(KEY, key);
            values.put(VALUE, value);
            return values;
        }

        public static ContentProviderOperation updateOperation(String key, Object value) {
            return ContentProviderOperation.newUpdate(CONTENT_URI)
                    .withValue(VALUE, value)
                    .withSelection(KEY + "=?", new String[]{key})
                    .build();
        }
    }

    private DatabaseContract() {
    }
}
