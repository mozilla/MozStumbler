/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread.datahandling;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.core.logging.ClientLog;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.base.SerializedJSONRows;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.base.SerializedJSONRowsList;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

public class ReportFileList extends SerializedJSONRowsList {
    protected int mReportCount;
    protected int mWifiCount;
    protected int mCellCount;


    public static class Iterator extends SerializedJSONRowsList.Iterator {
        Iterator(SerializedJSONRowsList list) {
            super(list);
        }

        @Override
        protected SerializedJSONRows create(File f, byte[] data) {
            AtomicInteger obs = new AtomicInteger();
            AtomicInteger wifi = new AtomicInteger();
            AtomicInteger cell = new AtomicInteger();
            getCountsFromFilename(f, obs, wifi, cell);
            ReportBatch reportBatch = new ReportBatch(data, SerializedJSONRows.StorageState.ON_DISK,
                    obs.get(), wifi.get(), cell.get());
            reportBatch.filename = f.getName();
            return reportBatch;
        }
    }

    @Override
    public Iterator getIterator() {
        return new Iterator(this);
    }

    public ReportFileList(File storageDir) {
        super(storageDir);
    }

    public ReportFileList(ReportFileList other) {
        super(other);
        mReportCount = other.mReportCount;
        mWifiCount = other.mWifiCount;
        mCellCount = other.mCellCount;
    }

    @Override
    public void update() {
        mFiles = mStorageDir.listFiles();
        if (mFiles == null) {
            return;
        }

        if (AppGlobals.isDebug) {
            for (File f : mFiles) {
                ClientLog.d("StumblerFiles", f.getName());
            }
        }

        mFilesOnDiskBytes = mReportCount = mWifiCount = mCellCount = 0;
        for (File f : mFiles) {
            AtomicInteger obs = new AtomicInteger();
            AtomicInteger wifi = new AtomicInteger();
            AtomicInteger cell = new AtomicInteger();
            getCountsFromFilename(f, obs, wifi, cell);
            mReportCount += obs.get();
            mWifiCount += wifi.get();
            mCellCount += cell.get();
            mFilesOnDiskBytes += f.length();
        }
    }

    public static void getCountsFromFilename(File f,
                                             AtomicInteger outReportCount,
                                             AtomicInteger outWifiCount,
                                             AtomicInteger outCellCount) {
        outReportCount.set((int) DataStorageManager.getLongFromFilename(f.getName(), DataStorageManager.SEP_REPORT_COUNT));
        outWifiCount.set((int) DataStorageManager.getLongFromFilename(f.getName(), DataStorageManager.SEP_WIFI_COUNT));
        outCellCount.set((int) DataStorageManager.getLongFromFilename(f.getName(), DataStorageManager.SEP_CELL_COUNT));
    }
}
