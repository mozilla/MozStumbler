/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread.datahandling;

public class DataStorageConstants {

    /*******************************************************************************/
    /*  Constants to define maximum byte storage and staleness of data on disk     */
    /*******************************************************************************/

    // Used to cap the amount of data stored. When this limit is hit, no more data is saved to disk
    // until the data is uploaded, or and data exceeds DEFAULT_MAX_WEEKS_DATA_ON_DISK.
    public static final long DEFAULT_MAX_BYTES_STORED_ON_DISK = 1024 * 250; // 250 KiB max by default
    // Used as a safeguard to ensure stumbling data is not persisted. The intended use case of the stumbler lib is not
    // for long-term storage, and so if ANY data on disk is this old, ALL data is wiped as a privacy mechanism.
    public static final int DEFAULT_MAX_WEEKS_DATA_ON_DISK = 2;

    // We allow for more data to be stored, and for longer in the dedicated stumbler application.
    public static final long CLIENT_MAX_BYTES_DISK_STORAGE = 1000 * 1000 * 20; // 20MB for Mozilla Stumbler by default, is ok?
    public static final int CLIENT_MAX_WEEKS_OLD_STORED = 4;


    /*******************************************************************************/
    // keynames for storage
    /*******************************************************************************/


    public static class ReportsColumns {
        public static final String LAT = "latitude";
        public static final String LON = "longitude";
        public static final String TIME = "timestamp";
        public static final String ACCURACY = "accuracy";
        public static final String ALTITUDE = "altitude";
        public static final String CELL = "cellTowers";
        public static final String WIFI = "wifiAccessPoints";
        public static final String RADIO = "radioType";
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
