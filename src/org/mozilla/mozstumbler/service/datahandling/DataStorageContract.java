/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.datahandling;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.net.Uri;
import android.provider.BaseColumns;

public final class DataStorageContract {

    public static class ReportsColumns {
        public final static String LAT = "lat";
        public final static String LON = "lon";
        public final static String TIME = "timestamp";
        public final static String ACCURACY = "accuracy";
        public final static String ALTITUDE = "altitude";
        public final static String RADIO = "radio";
        public final static String CELL = "cell";
        public final static String WIFI = "wifi";
        public final static String CELL_COUNT = "cell_count";
        public final static String WIFI_COUNT = "wifi_count";
    }

    public static class Stats {
        public static final String KEY_VERSION = "version_code";
        public static final int VERSION_CODE = 1;
        public static final String KEY_BYTES_SENT = "bytes_sent";
        public static final String KEY_LAST_UPLOAD_TIME = "last_upload_time";
        public static final String KEY_OBSERVATIONS_SENT = "observations_sent";
        public static final String KEY_WIFIS_SENT = "wifis_sent";
        public static final String KEY_CELLS_SENT = "cells_sent";
    }

    private DataStorageContract() {
    }
}
