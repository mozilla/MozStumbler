/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread.datahandling;

import android.content.Context;

import org.acra.ACRA;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.core.logging.ClientLog;
import org.mozilla.mozstumbler.service.utils.Zipper;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.ISystemClock;
import org.mozilla.mozstumbler.svclocator.services.log.ILogger;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Timer;
import java.util.TimerTask;


/* Stores reports in memory (mCurrentReports) until MAX_REPORTS_IN_MEMORY,
 * then writes them to disk as a .gz file. The name of the file has
 * the time written, the # of reports, and the # of cells and wifis.
 *
 * Each .gz file is typically 1-5KB. File name example: reports-t1406863343313-r4-w25-c7.gz
 *
 * The sync stats are written as a key-value pair file (not zipped).
 *
 * The tricky bit is the mCurrentReportsSendBuffer. When the uploader code begins accessing the
 * report batches, mCurrentReports gets pushed to mCurrentReportsSendBuffer.
 * The mCurrentReports is then cleared, and can continue receiving new reports.
 * From the uploader perspective, mCurrentReportsSendBuffer looks and acts exactly like a batch file on disk.
 *
 * If the network is reasonably active, and reporting is slow enough, there is no disk I/O, it all happens
 * in-memory.
 *
 * Also of note: the in-memory buffers (both mCurrentReports and mCurrentReportsSendBuffer) are saved
 * when the service is destroyed.
 */
public class DataStorageManager {
    private static ILogger Log = (ILogger) ServiceLocator.getInstance().getService(ILogger.class);
    private static final String LOG_TAG = LoggerUtil.makeLogTag(DataStorageManager.class);

    private static final ISystemClock clock = (ISystemClock) ServiceLocator
                                                                .getInstance()
                                                                .getService(ISystemClock.class);

    static final String SEP_REPORT_COUNT = "-r";
    static final String SEP_WIFI_COUNT = "-w";
    static final String SEP_CELL_COUNT = "-c";
    static final String SEP_TIME_MS = "-t";
    static final String FILENAME_PREFIX = "reports";
    static final String MEMORY_BUFFER_NAME = "in memory send buffer";
    // Used to cap the amount of data stored. When this limit is hit, no more data is saved to disk
    // until the data is uploaded, or and data exceeds DEFAULT_MAX_WEEKS_DATA_ON_DISK.
    private static final long DEFAULT_MAX_BYTES_STORED_ON_DISK = 1024 * 250; // 250 KiB max by default
    // Used as a safeguard to ensure stumbling data is not persisted. The intended use case of the stumbler lib is not
    // for long-term storage, and so if ANY data on disk is this old, ALL data is wiped as a privacy mechanism.
    private static final int DEFAULT_MAX_WEEKS_DATA_ON_DISK = 2;
    public static DataStorageManager sInstance;
    final File mReportsDir;
    final ReportBatchBuilder mCurrentReports = new ReportBatchBuilder();
    // Set to the default value specified above.
    private final long mMaxBytesDiskStorage;
    // Set to the default value specified above.
    private final int mMaxWeeksStored;
    private final StorageIsEmptyTracker mTracker;
    protected final PersistedStats mPersistedOnDiskUploadStats;
    protected ReportBatch mCurrentReportsSendBuffer;
    protected ReportFileList mFileList;
    private ReportBatchIterator mReportBatchIterator;
    private Timer mFlushMemoryBuffersToDiskTimer;

    DataStorageManager(Context c, StorageIsEmptyTracker tracker,
                       long maxBytesStoredOnDisk, int maxWeeksDataStored) {
        mMaxBytesDiskStorage = maxBytesStoredOnDisk;
        mMaxWeeksStored = maxWeeksDataStored;
        mTracker = tracker;
        final String baseDir = getStorageDir(c);
        mReportsDir = new File(baseDir + "/reports");
        if (!mReportsDir.exists()) {
            mReportsDir.mkdirs();
        }

        mFileList = new ReportFileList();
        mFileList.update(mReportsDir);

        mPersistedOnDiskUploadStats = new PersistedStats(baseDir, c);
    }

    public static String getStorageDir(Context c) {
        File dir = null;

        // Uncomment the following block if you're debugging
        // persistent log storage on a non-rooted device
        /*
        if (AppGlobals.isDebug) {
            // in debug, put files in public location
            dir = c.getExternalFilesDir(null);
            if (dir != null) {
                dir = new File(dir.getAbsolutePath() + File.separator + MOZ_STUMBLER_RELPATH);
            }
        }
        */

        if (dir == null) {
            dir = c.getFilesDir();
        }

        if (!dir.exists()) {
            boolean ok = dir.mkdirs();
            if (!ok) {
                ClientLog.w(LOG_TAG, "getStorageDir: error in mkdirs()");
                Log.e(LOG_TAG, "Error creating storage directory: ["+dir.getAbsolutePath()+"]");
            }
        }

        return dir.getPath();
    }

    public static synchronized void createGlobalInstance(Context context, StorageIsEmptyTracker tracker) {
        DataStorageManager.createGlobalInstance(context, tracker,
                DEFAULT_MAX_BYTES_STORED_ON_DISK, DEFAULT_MAX_WEEKS_DATA_ON_DISK);
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

    public static synchronized DataStorageManager getInstance() {
        return sInstance;
    }

    private static byte[] readFile(File file) {
        RandomAccessFile f = null;
        try {
            f = new RandomAccessFile(file, "r");
            final byte[] data = new byte[(int) f.length()];
            f.readFully(data);
            return data;
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error reading reports file: " + e.toString());
            ACRA.getErrorReporter().handleSilentException(e);
            return new byte[]{};
        } finally {
            if (f != null) {
                try {
                    f.close();
                } catch (IOException e) {
                    // eat it
                }
            }
        }
    }

    private static long getLongFromFilename(String name, String separator) {
        final int s = name.indexOf(separator) + separator.length();
        int e = name.indexOf('-', s);
        if (e < 0) {
            e = name.indexOf('.', s);
        }
        return Long.parseLong(name.substring(s, e));
    }

    public synchronized int getQueuedReportCount() {
        int reportCount = mFileList.mReportCount + mCurrentReports.reportsCount();
        if (mCurrentReportsSendBuffer != null) {
            reportCount += mCurrentReportsSendBuffer.reportCount;
        }
        return reportCount;
    }

    public synchronized int getQueuedWifiCount() {
        int count = mFileList.mWifiCount + mCurrentReports.wifiCount;
        if (mCurrentReportsSendBuffer != null) {
            count += mCurrentReportsSendBuffer.wifiCount;
        }
        return count;
    }

    public synchronized int getQueuedCellCount() {
        int count = mFileList.mCellCount + mCurrentReports.cellCount;
        if (mCurrentReportsSendBuffer != null) {
            count += mCurrentReportsSendBuffer.cellCount;
        }
        return count;
    }

    public synchronized byte[] getCurrentReportsRawBytes() {
        return (mCurrentReports.reportsCount() < 1) ? null :
                mCurrentReports.finalizeReports().getBytes();
    }

    public synchronized long getQueuedZippedDataSize() {
        long byteLength = mFileList.mFilesOnDiskBytes;
        if (mCurrentReportsSendBuffer != null) {
            byteLength += mCurrentReportsSendBuffer.data.length;
        }
        return byteLength;
    }

    public synchronized int getMaxWeeksStored() {
        return mMaxWeeksStored;
    }

    public synchronized boolean isDirEmpty() {
        return (mFileList.mFiles == null || mFileList.mFiles.length < 1);
    }

    /* return name of file used, or memory buffer sentinel value.
     * The return value is used to delete the file/buffer later. */
    public synchronized ReportBatch getFirstBatch() {
        final boolean dirEmpty = isDirEmpty();
        final int currentReportsCount = mCurrentReports.reportsCount();

        if (dirEmpty && currentReportsCount < 1) {
            return null;
        }

        mReportBatchIterator = new ReportBatchIterator(mFileList);

        if (currentReportsCount > 0) {
            final String filename = MEMORY_BUFFER_NAME;

            String report = mCurrentReports.finalizeReports();
            // Uncomment this block when debugging the report blobs
            //Log.d(LOG_TAG, "PII geosubmit report: " + report);
            // end debug blob

            final byte[] data = Zipper.zipData(report.getBytes());
            final int wifiCount = mCurrentReports.wifiCount;
            final int cellCount = mCurrentReports.cellCount;

            clearCurrentReports();
            final ReportBatch result = new ReportBatch(filename, data, currentReportsCount, wifiCount, cellCount);
            mCurrentReportsSendBuffer = result;
            return result;
        } else {
            return getNextBatch();
        }
    }

    private void clearCurrentReports() {
        mCurrentReports.clearReports();
        mCurrentReports.wifiCount = mCurrentReports.cellCount = 0;
    }

    public synchronized ReportBatch getNextBatch() {
        if (mReportBatchIterator == null) {
            return null;
        }

        mReportBatchIterator.currentIndex++;
        if (mReportBatchIterator.currentIndex < 0 ||
                mReportBatchIterator.currentIndex > mReportBatchIterator.fileList.mFiles.length - 1) {
            return null;
        }

        final File f = mReportBatchIterator.fileList.mFiles[mReportBatchIterator.currentIndex];
        final String filename = f.getName();
        final int reportCount = (int) getLongFromFilename(f.getName(), SEP_REPORT_COUNT);
        final int wifiCount = (int) getLongFromFilename(f.getName(), SEP_WIFI_COUNT);
        final int cellCount = (int) getLongFromFilename(f.getName(), SEP_CELL_COUNT);
        final byte[] data = readFile(f);
        return new ReportBatch(filename, data, reportCount, wifiCount, cellCount);
    }

    private File createFile(int reportCount, int wifiCount, int cellCount) {
        final long time = clock.currentTimeMillis();
        final String name = FILENAME_PREFIX +
                SEP_TIME_MS + time +
                SEP_REPORT_COUNT + reportCount +
                SEP_WIFI_COUNT + wifiCount +
                SEP_CELL_COUNT + cellCount + ".gz";
        return new File(mReportsDir, name);
    }

    public synchronized long getOldestBatchTimeMs() {
        if (isDirEmpty()) {
            return 0;
        }

        long oldest = Long.MAX_VALUE;
        for (File f : mFileList.mFiles) {
            final long t = getLongFromFilename(f.getName(), SEP_TIME_MS);
            if (t < oldest) {
                oldest = t;
            }
        }
        return oldest;
    }

    /*
     Return true if the current reports (if any exist) are properly saved.
     Return false only on a failed write to storage.
     */
    private synchronized boolean saveCurrentReportsSendBufferToDisk() {
        if (mCurrentReportsSendBuffer == null || mCurrentReportsSendBuffer.reportCount < 1) {
            return true;
        }

        boolean result = saveToDisk(mCurrentReportsSendBuffer.data,
                mCurrentReportsSendBuffer.reportCount,
                mCurrentReportsSendBuffer.wifiCount,
                mCurrentReportsSendBuffer.cellCount);
        mCurrentReportsSendBuffer = null;
        return result;
    }

    /*
     Return true if reports are saved.
     Return false only on a failed write to storage.
     */
    private boolean saveToDisk(byte[] bytes, int reportCount, int wifiCount, int cellCount) {
        if (mFileList.mFilesOnDiskBytes > mMaxBytesDiskStorage) {
            return false;
        }

        FileOutputStream fos = null;
        File f = createFile(reportCount, wifiCount, cellCount);
        try {
            Log.i(LOG_TAG, "Preparing to write to : ["+f.getAbsolutePath()+"]");
            if (!f.exists()) {
                f.createNewFile();
            }
            fos = new FileOutputStream(f);
            fos.write(bytes);
        } catch (IOException e) {
           Log.e(LOG_TAG, "Error writing reports to disk: " + e.toString());
           ACRA.getErrorReporter().handleSilentException(e);
           // Try to remove the file safely if we can't save it.
           if (f.exists()) {
               f.delete();
           }
           return false;
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    // eat it - nothing we can do here
                }
            }
        }
        mFileList.update(mReportsDir);
        return true;
    }

    public synchronized void saveCurrentReportsToDisk() {
        saveCurrentReportsSendBufferToDisk();
        if (mCurrentReports.reportsCount() < 1) {
            return;
        }
        String report = mCurrentReports.finalizeReports();
        // Uncomment this block when debugging the report blobs
        //Log.d(LOG_TAG, "PII geosubmit report: " + report);
        // end debug blob


        final byte[] bytes = Zipper.zipData(report.getBytes());
        saveToDisk(bytes, mCurrentReports.reportsCount(), mCurrentReports.wifiCount, mCurrentReports.cellCount);
        clearCurrentReports();
    }

    public synchronized void insert(String report, int wifiCount, int cellCount) {
        notifyStorageIsEmpty(false);

        if (mFlushMemoryBuffersToDiskTimer != null) {
            mFlushMemoryBuffersToDiskTimer.cancel();
            mFlushMemoryBuffersToDiskTimer = null;
        }

        mCurrentReports.addReport(report);

        mCurrentReports.wifiCount += wifiCount;
        mCurrentReports.cellCount += cellCount;

        if (mCurrentReports.maxReportsReached()) {
            // save to disk
            saveCurrentReportsToDisk();
        } else {
            // Schedule a timer to flush to disk after a few mins.
            // If collection stops and wifi not available for uploading, the memory buffer is flushed to disk.
            final int kMillis = 1000 * 60 * 3;
            mFlushMemoryBuffersToDiskTimer = new Timer();
            mFlushMemoryBuffersToDiskTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    saveCurrentReportsSendBufferToDisk();
                }
            }, kMillis);
        }
    }

    /* Pass filename returned from dataToSend() */
    public synchronized boolean delete(String filename) {
        if (filename.equals(MEMORY_BUFFER_NAME)) {
            mCurrentReportsSendBuffer = null;
            return true;
        }

        final File file = new File(mReportsDir, filename);
        boolean ok = file.delete();
        mFileList.update(mReportsDir);
        return ok;
    }

    /*
     This method just deletes all files.  Note that this is a special case and is only run when
     the stumbler assumes that the logs are either out of date, or are corrupted.

     We do *not* try to move these files into the sdcard as they are probably just corrupt anyway.
     */
    public synchronized void deleteAll() {
        if (mFileList.mFiles == null) {
            return;
        }

        for (File f : mFileList.mFiles) {
            f.delete();
        }

        mFileList.update(mReportsDir);
    }

    private void notifyStorageIsEmpty(boolean isEmpty) {
        if (mTracker != null) {
            mTracker.notifyStorageStateEmpty(isEmpty);
        }
    }

    public synchronized void incrementSyncStats(long bytesSent, long reports, long cells, long wifis) {
        mPersistedOnDiskUploadStats.incrementSyncStats(bytesSent, reports, cells, wifis);

    }

    public interface StorageIsEmptyTracker {
        public void notifyStorageStateEmpty(boolean isEmpty);
    }


    protected static class ReportFileList {
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
                mReportCount += (int) getLongFromFilename(f.getName(), SEP_REPORT_COUNT);
                mWifiCount += (int) getLongFromFilename(f.getName(), SEP_WIFI_COUNT);
                mCellCount += (int) getLongFromFilename(f.getName(), SEP_CELL_COUNT);
                mFilesOnDiskBytes += f.length();
            }
        }
    }

    public static class ReportBatch {
        public final String filename;
        public final byte[] data;
        public final int reportCount;
        public final int wifiCount;
        public final int cellCount;

        public ReportBatch(String filename, byte[] data, int reportCount, int wifiCount, int cellCount) {
            this.filename = filename;
            this.data = data;
            this.reportCount = reportCount;
            this.wifiCount = wifiCount;
            this.cellCount = cellCount;
        }
    }

    private static class ReportBatchIterator {
        static final int BATCH_INDEX_FOR_MEM_BUFFER = -1;
        public final ReportFileList fileList;
        public int currentIndex = BATCH_INDEX_FOR_MEM_BUFFER;

        public ReportBatchIterator(ReportFileList list) {
            fileList = new ReportFileList(list);
        }
    }
}
