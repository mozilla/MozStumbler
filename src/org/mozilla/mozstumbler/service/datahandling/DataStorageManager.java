/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.datahandling;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.utils.Zipper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/** Stores reports in memory (mCurrentReports) until MAX_REPORTS_IN_MEMORY,
 * then writes them to disk as a .gz file. The name of the file has
 * the time written, the # of reports, and the # of cells and wifis.
 *
 * Each .gz file is typically 1-5KB.
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
    private final String LOG_TAG = DataStorageManager.class.getName();
    private final int MAX_REPORTS_IN_MEMORY = 50;
    private long mMaxBytesDiskStorage = 1024 * 1024 * 3; // 3 megabytes max by default
    private int mMaxWeeksStored = 2;
    private ReportBatchBuilder mCurrentReports = new ReportBatchBuilder();
    private int mTotalReportCount;
    private File mReportsDir;
    private File mStatsFile;
    private ReportBatch mCurrentReportsSendBuffer;
    private ReportBatchIterator mReportBatchIterator;
    private DatabaseIsEmptyTracker mTracker;
    private ReportFileList mFileList;

    final static String SEP_REPORT_COUNT = "-r";
    final static String SEP_WIFI_COUNT = "-w";
    final static String SEP_CELL_COUNT = "-c";
    final static String SEP_TIME_MS = "-t";
    final static String FILENAME_PREFIX = "reports";
    final static String MEMORY_BUFFER_NAME = "in memory send buffer";

    public static class QueuedCounts {
        public int reportCount;
        public int wifiCount;
        public int cellCount;
        public long bytes;
    }

    /** Some data is calculated on-demand, don't abuse this function */
    public QueuedCounts getQueuedCounts() {
        QueuedCounts q = new QueuedCounts();
        q.reportCount = mFileList.reportCount + mCurrentReports.reports.size();
        q.wifiCount = mFileList.wifiCount + mCurrentReports.wifiCount;
        q.cellCount = mFileList.cellCount + mCurrentReports.cellCount;
        q.bytes = 0;

        if (mCurrentReports.reports.size() > 0) {
            try {
                q.bytes = Zipper.zipData(finalizeReports(mCurrentReports.reports).getBytes()).length;
            } catch (IOException ex) {
            }

            if (mFileList.reportCount > 0) {
                q.bytes += mFileList.filesOnDiskBytes;
            }
        }

        if (mCurrentReportsSendBuffer != null) {
            q.reportCount += mCurrentReportsSendBuffer.reportCount;
            q.wifiCount += mCurrentReportsSendBuffer.wifiCount;
            q.cellCount += mCurrentReportsSendBuffer.cellCount;
            q.bytes += mCurrentReportsSendBuffer.data.length;
        }
        return q;
    }

    private static class ReportFileList {
        File[] files;
        int reportCount;
        int wifiCount;
        int cellCount;
        long filesOnDiskBytes;

        public ReportFileList() {}
        public ReportFileList(ReportFileList other) {
            files = other.files.clone();
            reportCount = other.reportCount;
            wifiCount = other.wifiCount;
            cellCount = other.cellCount;
            filesOnDiskBytes = other.filesOnDiskBytes;
        }

        void update(File directory) {
            files = directory.listFiles();
            if (files == null)
                return;

            filesOnDiskBytes = reportCount = wifiCount = cellCount = 0;
            for (File f : files) {
                reportCount += (int)getLongFromFilename(f.getName(), SEP_REPORT_COUNT);
                wifiCount += (int)getLongFromFilename(f.getName(), SEP_WIFI_COUNT);
                cellCount += (int)getLongFromFilename(f.getName(), SEP_CELL_COUNT);
                filesOnDiskBytes += f.length();
            }
        }
    }

    public static class ReportBatch {
        public byte[] data;
        public String filename;
        public int reportCount;
        public int wifiCount;
        public int cellCount;
    }

    private static class ReportBatchBuilder {
        public ArrayList<String> reports = new ArrayList<String>();
        public int wifiCount;
        public int cellCount;
    }

    private static class ReportBatchIterator {
        public ReportBatchIterator(ReportFileList list) {
            fileList = new ReportFileList(list);
        }

        static final int BATCH_INDEX_FOR_MEM_BUFFER  = -1;
        public int currentIndex = BATCH_INDEX_FOR_MEM_BUFFER;
        public final ReportFileList fileList;
    }

    public interface DatabaseIsEmptyTracker {
        public void databaseIsEmpty(boolean isEmpty);
    }

    public Map<String, Long> getOldDbStats(Context context) {
        final File dbFile = new File(context.getFilesDir().getParent() + "/databases/" + "stumbler.db");
        if (!dbFile.exists())
            return null;

        SQLiteDatabase db = SQLiteDatabase.openDatabase(dbFile.toString(), null, 0);

        Cursor cursor = null;
        try {
            cursor = db.rawQuery("select * from stats", null);
            if (cursor == null)
                return null;

            cursor.moveToFirst();
            Map<String, Long> kv = new HashMap<String, Long>();
            while (!cursor.isAfterLast()) {
                String key = cursor.getString(cursor.getColumnIndex("key"));
                Long value = cursor.getLong(cursor.getColumnIndex("value"));
                kv.put(key, value);
                cursor.moveToNext();
            }
            assert(kv.size() == 4);
            return kv;
        } finally {
            if (cursor != null)
                cursor.close();
            db.close();
            dbFile.delete();
        }
    }

    String getStorageDir(Context c) {
        File dir = null;
        if (AppGlobals.isDebug) {
            // in debug, put files in public location
            dir = c.getExternalFilesDir(null);
            if (dir != null) {
                dir = new File(dir.getAbsolutePath() + "/mozstumbler");
            }
        }

        if (dir == null) {
            dir = c.getFilesDir();
        }

        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir.getPath();
    }
    
    public DataStorageManager(Context c, DatabaseIsEmptyTracker tracker) {
        mTracker = tracker;
        String baseDir = getStorageDir(c);
        mStatsFile = new File(baseDir, "upload_stats.ini");
        mReportsDir = new File(baseDir + "/reports");
        if (!mReportsDir.exists()) {
            mReportsDir.mkdirs();
        }
        mFileList = new ReportFileList();
        mFileList.update(mReportsDir);

        // upgrade from db, if needed
        Map<String, Long> oldStats = getOldDbStats(c);
        if (oldStats != null) {
            long last_upload_time = oldStats.get("last_upload_time");
            long observations_sent = oldStats.get("observations_sent");
            long wifis_sent = oldStats.get("wifis_sent");
            long cells_sent = oldStats.get("cells_sent");
            try {
                writeSyncStats(last_upload_time, 0, observations_sent, wifis_sent, cells_sent);
            } catch (IOException ex) {
                Log.e(LOG_TAG, "Exception in DataStorageManager upgrading db:", ex);
            }
        }
    }

    public void setMaxStorageOnDisk(long maxBytes) {
        mMaxBytesDiskStorage = maxBytes;
    }

    public void setMaxWeeksStored(int weeks) {
        mMaxWeeksStored = weeks;
    }

    public int getMaxWeeksStored() {
        return mMaxWeeksStored;
    }

    public static byte[] readFile(File file) throws IOException {
        RandomAccessFile f = new RandomAccessFile(file, "r");
        try {
            byte[] data = new byte[(int)f.length()];
            f.readFully(data);
            return data;
        } finally {
            f.close();
        }
    }

    public boolean isDirEmpty() {
        return (mFileList.files == null || mFileList.files.length < 1);
    }

    /** Pass filename returned from dataToSend() */
    public synchronized boolean delete(String filename) {
        if (filename == MEMORY_BUFFER_NAME) {
            mCurrentReportsSendBuffer = null;
            return true;
        }

        File file = new File(mReportsDir, filename);
        boolean ok = file.delete();
        mFileList.update(mReportsDir);
        return ok;
    }

    static long getLongFromFilename(String name, String separator) {
        int s = name.indexOf(separator) + separator.length();
        int e = name.indexOf('-', s);
        if (e < 0)
            e = name.indexOf('.', s);
        return Long.parseLong(name.substring(s, e));
    }

    /** return name of file used, or memory buffer sentinel value.
     * The return value is used to delete the file/buffer later.*/
    public synchronized ReportBatch getFirstBatch() throws IOException {
        boolean dirEmpty = isDirEmpty();
        if (dirEmpty && mCurrentReports.reports.size() < 1)
            return null;

        mReportBatchIterator = new ReportBatchIterator(mFileList);

        if (mCurrentReports.reports.size() > 0) {
            ReportBatch result = new ReportBatch();
            mCurrentReportsSendBuffer = result;
            result.filename = MEMORY_BUFFER_NAME;
            result.reportCount = mCurrentReports.reports.size();
            result.data = Zipper.zipData(finalizeReports(mCurrentReports.reports).getBytes());
            result.wifiCount = mCurrentReports.wifiCount;
            result.cellCount = mCurrentReports.cellCount;
            clearCurrentReports();
            return result;
        } else {
            return getNextBatch();
        }
    }

    void clearCurrentReports() {
        mCurrentReports.reports.clear();
        mCurrentReports.wifiCount = mCurrentReports.cellCount = 0;
    }

    public synchronized ReportBatch getNextBatch() throws IOException {
        if (mReportBatchIterator == null)
            return null;

        mReportBatchIterator.currentIndex++;
        if (mReportBatchIterator.currentIndex < 0 ||
            mReportBatchIterator.currentIndex > mReportBatchIterator.fileList.files.length - 1) {
            return null;
        }

        File f = mReportBatchIterator.fileList.files[mReportBatchIterator.currentIndex];
        ReportBatch result = new ReportBatch();
        result.filename = f.getName();
        result.reportCount = (int)getLongFromFilename(f.getName(), SEP_REPORT_COUNT);
        result.wifiCount = (int)getLongFromFilename(f.getName(), SEP_WIFI_COUNT);
        result.cellCount = (int)getLongFromFilename(f.getName(), SEP_CELL_COUNT);
        result.data = readFile(f);
        return result;
    }

    File createFile(int reportCount, int wifiCount, int cellCount) {
        long time = System.currentTimeMillis();
        String name = FILENAME_PREFIX + 
                      SEP_TIME_MS + time + 
                      SEP_REPORT_COUNT + reportCount + 
                      SEP_WIFI_COUNT + wifiCount + 
                      SEP_CELL_COUNT + cellCount + ".gz";
        File file = new File(mReportsDir, name);
        return file;
    }

    public long getOldestBatchTimeMs() {
        if (isDirEmpty())
            return 0;

        long oldest = Long.MAX_VALUE;
        for (File f : mFileList.files) {
            long t = getLongFromFilename(f.getName(), SEP_TIME_MS);
            if (t < oldest)
                oldest = t;
        }
        return oldest;
    }

    public void saveCurrentReportsSendBufferToDisk() throws IOException {
        if (mCurrentReportsSendBuffer == null)
            return;

        saveToDisk(mCurrentReportsSendBuffer.data, 
                   mCurrentReportsSendBuffer.reportCount,
                   mCurrentReportsSendBuffer.wifiCount,
                   mCurrentReportsSendBuffer.cellCount);
        mCurrentReportsSendBuffer = null;
    }

    public synchronized void saveToDisk(byte[] bytes, int reportCount, int wifiCount, int cellCount)
      throws IOException {
        if (mFileList.filesOnDiskBytes > mMaxBytesDiskStorage)
            return;

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(createFile(reportCount, wifiCount, cellCount));
            fos.write(bytes);
        } finally {
            if (fos != null)
                fos.close();
        }
        mFileList.update(mReportsDir);
    }

    String finalizeReports(ArrayList<String> reports) {
        final String kPrefix = "{\"items\":[";
        final String kSuffix = "]}";

        StringBuilder sb = new StringBuilder();
        sb.append(kPrefix);
        String sep = "";
        String separator = ",";
        for(String s: reports) {
            sb.append(sep).append(s);
            sep = separator;
        }

        String result = sb.append(kSuffix).toString();
        Log.d(LOG_TAG, result);
        return result;
    }

    public synchronized void saveCurrentReportsToDisk() throws IOException {
        saveCurrentReportsSendBufferToDisk();
        byte[] bytes = Zipper.zipData(finalizeReports(mCurrentReports.reports).getBytes());
        saveToDisk(bytes, mCurrentReports.reports.size(), mCurrentReports.wifiCount, mCurrentReports.cellCount);
        clearCurrentReports();
    }

    public synchronized void insert(String report, int wifiCount, int cellCount) throws IOException {
        notifyDbIsEmpty(false);

        mCurrentReports.reports.add(report);
        mCurrentReports.wifiCount = wifiCount;
        mCurrentReports.cellCount = cellCount;

        mTotalReportCount++;

        if (mCurrentReports.reports.size() >= MAX_REPORTS_IN_MEMORY) {
            // save to disk
            saveCurrentReportsToDisk();
        }
    }

    public Properties readSyncStats() throws IOException {
        if (!mStatsFile.exists())
            return new Properties();

        FileInputStream input = new FileInputStream(mStatsFile);
        try {
            Properties props = new Properties();
            props.load(input);
            return props;
        }
        finally {
            input.close();
        }
    }

    public void incrementSyncStats(long bytesSent, long reports, long cells, long wifis) throws IOException {
        if (reports + cells + wifis < 1)
            return;

        Properties properties = readSyncStats();
        long time = System.currentTimeMillis();
        writeSyncStats(time,
            Long.parseLong(properties.getProperty(DataStorageContract.Stats.KEY_BYTES_SENT, "0")) + bytesSent,
            Long.parseLong(properties.getProperty(DataStorageContract.Stats.KEY_OBSERVATIONS_SENT, "0")) + reports,
            Long.parseLong(properties.getProperty(DataStorageContract.Stats.KEY_CELLS_SENT, "0")) + cells,
            Long.parseLong(properties.getProperty(DataStorageContract.Stats.KEY_WIFIS_SENT, "0")) + wifis);
    }

    public void writeSyncStats(long time, long bytesSent, long totalObs, long totalCells, long totalWifis) throws IOException {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(mStatsFile);
            Properties props = new Properties();
            props.setProperty(DataStorageContract.Stats.KEY_LAST_UPLOAD_TIME, String.valueOf(time));
            props.setProperty(DataStorageContract.Stats.KEY_BYTES_SENT, String.valueOf(bytesSent));
            props.setProperty(DataStorageContract.Stats.KEY_OBSERVATIONS_SENT, String.valueOf(totalObs));
            props.setProperty(DataStorageContract.Stats.KEY_CELLS_SENT, String.valueOf(totalCells));
            props.setProperty(DataStorageContract.Stats.KEY_WIFIS_SENT, String.valueOf(totalWifis));
            props.setProperty(DataStorageContract.Stats.KEY_VERSION, String.valueOf(DataStorageContract.Stats.VERSION_CODE));
            props.store(out, null);
        } finally {
            if (out != null)
                out.close();
        }
    }

    public synchronized void deleteAll() {
        for (File f : mFileList.files) {
            f.delete();
        }
        mFileList.update(mReportsDir);
    }

    public void notifyDbIsEmpty(boolean isEmpty) {
        if (mTracker != null)
            mTracker.databaseIsEmpty(isEmpty);
    }
}
