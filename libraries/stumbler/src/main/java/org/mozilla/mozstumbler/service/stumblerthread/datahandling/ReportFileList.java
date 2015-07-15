/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread.datahandling;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.core.logging.ClientLog;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.base.SerializedJSONRowsList;

import java.io.File;

public class ReportFileList extends SerializedJSONRowsList {
    protected int mReportCount;
    protected int mWifiCount;
    protected int mCellCount;

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
            mReportCount += (int) DataStorageManager.getLongFromFilename(f.getName(), DataStorageManager.SEP_REPORT_COUNT);
            mWifiCount += (int) DataStorageManager.getLongFromFilename(f.getName(), DataStorageManager.SEP_WIFI_COUNT);
            mCellCount += (int) DataStorageManager.getLongFromFilename(f.getName(), DataStorageManager.SEP_CELL_COUNT);
            mFilesOnDiskBytes += f.length();
        }
    }
}
