/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread.datahandling;

public final class DataStorageContract {

    private DataStorageContract() {
    }

    public static class ReportsColumns {
        public static final String LAT = "latitude";
        public static final String LON = "longitude";
        public static final String TIME = "timestamp";
        public static final String ACCURACY = "accuracy";
        public static final String ALTITUDE = "altitude";
        public static final String CELL = "cellTowers";
        public static final String WIFI = "wifiAccessPoints";
    }

    public static class Stats {
        public static final String KEY_VERSION = "version_code";
        public static final int VERSION_CODE = 2;
        public static final String KEY_BYTES_SENT = "bytes_sent";
        public static final String KEY_LAST_UPLOAD_TIME = "last_upload_time";
        public static final String KEY_OBSERVATIONS_SENT = "observations_sent";
        public static final String KEY_WIFIS_SENT = "wifis_sent";
        public static final String KEY_CELLS_SENT = "cells_sent";
        public static final String KEY_OBSERVATIONS_PER_DAY = "obs_per_day";
    }
}
