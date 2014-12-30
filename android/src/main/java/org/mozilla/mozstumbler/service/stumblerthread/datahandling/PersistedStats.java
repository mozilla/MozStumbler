/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread.datahandling;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

class PersistedStats {
    private final File mStatsFile;

    public PersistedStats(String baseDir) {
        mStatsFile = new File(baseDir, "upload_stats.ini");
    }

    public synchronized Properties readSyncStats() throws IOException {
        if (!mStatsFile.exists()) {
            return new Properties();
        }

        final FileInputStream input = new FileInputStream(mStatsFile);
        try {
            final Properties props = new Properties();
            props.load(input);
            return props;
        } finally {
            input.close();
        }
    }

    public synchronized void incrementSyncStats(long bytesSent, long reports, long cells, long wifis) throws IOException {
        if (reports + cells + wifis < 1) {
            return;
        }

        final Properties properties = readSyncStats();
        final long time = System.currentTimeMillis();
        writeSyncStats(time,
            Long.parseLong(properties.getProperty(DataStorageContract.Stats.KEY_BYTES_SENT, "0")) + bytesSent,
            Long.parseLong(properties.getProperty(DataStorageContract.Stats.KEY_OBSERVATIONS_SENT, "0")) + reports,
            Long.parseLong(properties.getProperty(DataStorageContract.Stats.KEY_CELLS_SENT, "0")) + cells,
            Long.parseLong(properties.getProperty(DataStorageContract.Stats.KEY_WIFIS_SENT, "0")) + wifis);
    }

    public synchronized void writeSyncStats(long time, long bytesSent, long totalObs, long totalCells, long totalWifis) throws IOException {
        final FileOutputStream out = new FileOutputStream(mStatsFile);
        try {
            final Properties props = new Properties();
            props.setProperty(DataStorageContract.Stats.KEY_LAST_UPLOAD_TIME, String.valueOf(time));
            props.setProperty(DataStorageContract.Stats.KEY_BYTES_SENT, String.valueOf(bytesSent));
            props.setProperty(DataStorageContract.Stats.KEY_OBSERVATIONS_SENT, String.valueOf(totalObs));
            props.setProperty(DataStorageContract.Stats.KEY_CELLS_SENT, String.valueOf(totalCells));
            props.setProperty(DataStorageContract.Stats.KEY_WIFIS_SENT, String.valueOf(totalWifis));
            props.setProperty(DataStorageContract.Stats.KEY_VERSION, String.valueOf(DataStorageContract.Stats.VERSION_CODE));
            props.store(out, null);
        } finally {
            out.close();
        }
    }

}
