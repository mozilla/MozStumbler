/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread.datahandling;

import android.content.Context;

import org.mozilla.mozstumbler.service.stumblerthread.datahandling.base.SerializedJSONRows;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.base.JSONRowsStorageManager;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.base.SerializedJSONRowsList;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

import java.io.File;


/* Stores reports in memory (mCachedReportBatches) until MAX_REPORTS_IN_MEMORY,
 * then writes them to disk as a .gz file. The name of the file has
 * the time written, the # of reports, and the # of cells and wifis.
 *
 * Each .gz file is typically 1-5KB. File name example: reports-t1406863343313-r4-w25-c7.gz
 *
 * The sync stats are written as a key-value pair file (not zipped).
 *
 * The tricky bit is the mCurrentReportsSendBuffer. When the uploader code begins accessing the
 * report batches, mCachedReportBatches gets pushed to mCurrentReportsSendBuffer.
 * The mCachedReportBatches is then cleared, and can continue receiving new reports.
 * From the uploader perspective, mCurrentReportsSendBuffer looks and acts exactly like a batch file on disk.
 *
 * If the network is reasonably active, and reporting is slow enough, there is no disk I/O, it all happens
 * in-memory.
 *
 * Also of note: the in-memory buffers (both mCachedReportBatches and mCurrentReportsSendBuffer) are saved
 * when the service is destroyed.
 */
public class DataStorageManager extends JSONRowsStorageManager implements IDataStorageManager {

    private static final String LOG_TAG = LoggerUtil.makeLogTag(DataStorageManager.class);
    static final String SEP_REPORT_COUNT = "-r";
    static final String SEP_WIFI_COUNT = "-w";
    static final String SEP_CELL_COUNT = "-c";
    public static DataStorageManager sInstance;
    protected final PersistedStats mPersistedOnDiskUploadStats;

    protected DataStorageManager(Context c, StorageIsEmptyTracker tracker,
                       long maxBytesStoredOnDisk, int maxWeeksDataStored) {
        super(c, tracker, maxBytesStoredOnDisk, maxWeeksDataStored, "reports");
        FILENAME_PREFIX = "report";
        mPersistedOnDiskUploadStats = new PersistedStats(getSystemStorageDir(c), c);
        mInMemoryActiveJSONRows = new ReportBatchBuilder();
    }

    public static synchronized void createGlobalInstance(Context context, StorageIsEmptyTracker tracker) {
        DataStorageManager.createGlobalInstance(context, tracker,
                DataStorageConstants.DEFAULT_MAX_BYTES_STORED_ON_DISK, DataStorageConstants.DEFAULT_MAX_WEEKS_DATA_ON_DISK);
    }

    public static synchronized DataStorageManager createGlobalInstance(Context context, StorageIsEmptyTracker tracker,
                                                                       long maxBytesStoredOnDisk, int maxWeeksDataStored) {
        if (sInstance == null) {
            sInstance = new DataStorageManager(context, tracker, maxBytesStoredOnDisk, maxWeeksDataStored);
        }
        return sInstance;
    }

    // This method only exists to help with unit testing when we need
    // to switch the singleton version of the DataStorageManager.
    public static void removeInstance() {
        sInstance = null;
    }

    public static synchronized IDataStorageManager getInstance() {
        return sInstance;
    }

    private ReportBatchBuilder getReportBatchBuilder() {
        assert(mInMemoryActiveJSONRows instanceof ReportBatchBuilder);
        return (ReportBatchBuilder) mInMemoryActiveJSONRows;
    }

    private ReportBatch getFinalizedReportBatch() {
        assert(mInMemoryFinalizedJSONRowsObject instanceof ReportBatch);
        return (ReportBatch)mInMemoryFinalizedJSONRowsObject;
    }

    private ReportFileList getFileList() {
        assert(mFileList instanceof ReportFileList);
        return (ReportFileList) mFileList;
    }

    @Override
    public void saveCachedReportsToDisk() {
        saveAllInMemoryToDisk();
    }

    @Override
    public synchronized int getQueuedReportCount() {
        int reportCount = getFileList().mReportCount + getReportBatchBuilder().entriesCount();
        if (mInMemoryFinalizedJSONRowsObject != null) {
            reportCount += getFinalizedReportBatch().reportCount;
        }
        return reportCount;
    }

    @Override
    public synchronized int getQueuedWifiCount() {
        int count = getFileList().mWifiCount + getReportBatchBuilder().getWifiCount();
        if (mInMemoryFinalizedJSONRowsObject != null) {
            count += getFinalizedReportBatch().wifiCount;
        }
        return count;
    }

    @Override
    public synchronized int getQueuedCellCount() {
        int count = getFileList().mCellCount + getReportBatchBuilder().getCellCount();
        if (mInMemoryFinalizedJSONRowsObject != null) {
            count += getFinalizedReportBatch().cellCount;
        }
        return count;
    }

    @Override
    public synchronized byte[] getCurrentReportsRawBytes() {
        return getActiveInMemoryBufferRawBytes();
    }

    @Override
    public synchronized long getQueuedZippedDataSize() {
        long byteLength = mFileList.mFilesOnDiskBytes;
        if (mInMemoryFinalizedJSONRowsObject != null) {
            byteLength += mInMemoryFinalizedJSONRowsObject.data.length;
        }
        return byteLength;
    }

    @Override
    protected File createFile(SerializedJSONRows _data) {
        assert (_data instanceof ReportBatch);
        ReportBatch data = (ReportBatch)_data;
        final long time = clock.currentTimeMillis();
        final String name = FILENAME_PREFIX +
                SEP_TIME_MS + time +
                SEP_REPORT_COUNT + data.reportCount +
                SEP_WIFI_COUNT + data.wifiCount +
                SEP_CELL_COUNT + data.cellCount + ".gz";
        return new File(mStorageDir, name);
    }

    @Override
    public void insert(MLSJSONObject geoSubmitObj) {
        super.insertRow(geoSubmitObj);
    }

    @Override
    public synchronized void incrementSyncStats(long bytesSent, long reports, long cells, long wifis) {
        mPersistedOnDiskUploadStats.incrementSyncStats(bytesSent, reports, cells, wifis);
    }

    @Override
    protected SerializedJSONRowsList createFileList(File storageDir) {
        return new ReportFileList(storageDir);
    }
}
