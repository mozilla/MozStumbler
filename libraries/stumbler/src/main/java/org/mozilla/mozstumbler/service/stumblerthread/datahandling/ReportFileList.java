/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread.datahandling;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.core.logging.ClientLog;

import java.io.File;

class ReportFileList {
    File[] mFiles;
    int mReportCount;
    int mWifiCount;
    int mCellCount;
    long mFilesOnDiskBytes;

    public ReportFileList() {
    }

    public ReportFileList(ReportFileList other) {
        if (other == null) {
            return;
        }

        if (other.mFiles != null) {
            mFiles = other.mFiles.clone();
        }

        mReportCount = other.mReportCount;
        mWifiCount = other.mWifiCount;
        mCellCount = other.mCellCount;
        mFilesOnDiskBytes = other.mFilesOnDiskBytes;
    }

    protected void update(File directory) {
        mFiles = directory.listFiles();
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
            mReportCount += (int) DataStorageManager.getLongFromFilename(f.getName(), DataStorageManager.SEP_REPORT_COUNT);
            mWifiCount += (int) DataStorageManager.getLongFromFilename(f.getName(), DataStorageManager.SEP_WIFI_COUNT);
            mCellCount += (int) DataStorageManager.getLongFromFilename(f.getName(), DataStorageManager.SEP_CELL_COUNT);
            mFilesOnDiskBytes += f.length();
        }
    }
}
