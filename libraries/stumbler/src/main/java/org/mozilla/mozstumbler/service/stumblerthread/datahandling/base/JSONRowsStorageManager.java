/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread.datahandling.base;

import android.content.Context;

import org.acra.ACRA;
import org.json.JSONObject;
import org.mozilla.mozstumbler.service.core.logging.ClientLog;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.StorageIsEmptyTracker;
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

/* For managing the storage of rows of data, by storing each row as an entry in a
JSON array.
Internally, multiple rows are stored in a SerializedJSONRows object, each of these
objects contains a max number of rows.
The most recent (a.k.a active) rows object is in memory. When the in-memory rows object is
full, it is flushed to a SerializedJSONRows object.
Thse objects are then flushed to disk as files named according to the time they were written.
A goal of this class is provide persistence without writing to disk unnecessarily,
and mask this behaviour from the caller so that it can work with in-memory or disk based
chunks in the same fashion.
Data loss can happen if the in-memory chunk is not written to disk (i.e. program kill).
*/
public class JSONRowsStorageManager {
    protected static ILogger Log = (ILogger) ServiceLocator.getInstance().getService(ILogger.class);
    private static final String LOG_TAG = LoggerUtil.makeLogTag(JSONRowsStorageManager.class);

    protected static final ISystemClock clock = (ISystemClock) ServiceLocator
            .getInstance()
            .getService(ISystemClock.class);

    protected static final String SEP_TIME_MS = "-t";
    protected String FILENAME_PREFIX = "jsonrows";

    protected final long mMaxBytesDiskStorage;
    protected final int mMaxWeeksStored;
    protected final StorageIsEmptyTracker mTracker;
    protected final File mStorageDir;

    protected JSONRowsObjectBuilder mInMemoryActiveJSONRows = new JSONRowsObjectBuilder();
    protected SerializedJSONRows mInMemoryFinalizedJSONRowsObject;
    protected SerializedJSONRowsList mFileList;
    protected SerializedJSONRowsList.Iterator mJSONRowsObjectIterator;
    protected Timer mFlushMemoryBuffersToDiskTimer;

    protected JSONRowsStorageManager(Context c, StorageIsEmptyTracker tracker,
                                     long maxBytesStoredOnDisk, int maxWeeksDataStored,
                                     String storageSubdirName) {
        mMaxBytesDiskStorage = maxBytesStoredOnDisk;
        mMaxWeeksStored = maxWeeksDataStored;
        mTracker = tracker;
        final String baseDir = getSystemStorageDir(c);
        mStorageDir = new File(baseDir + "/" + storageSubdirName);
        if (!mStorageDir.exists()) {
            mStorageDir.mkdirs();
        }

        mFileList = createFileList(mStorageDir);
        mFileList.update();
    }

    protected SerializedJSONRowsList createFileList(File storageDir) {
        return new SerializedJSONRowsList(storageDir);
    }

    public static String getSystemStorageDir(Context c) {
        File dir = c.getFilesDir();

        if (!dir.exists()) {
            boolean ok = dir.mkdirs();
            if (!ok) {
                ClientLog.w(LOG_TAG, "getSystemStorageDir: error in mkdirs()");
                Log.e(LOG_TAG, "Error creating storage directory: ["+dir.getAbsolutePath()+"]");
            }
        }

        return dir.getPath();
    }

    public synchronized byte[] getActiveInMemoryBufferRawBytes() {
        return mInMemoryActiveJSONRows.peekBytes();
    }

    public synchronized int getMaxWeeksStored() {
        return mMaxWeeksStored;
    }

    public synchronized boolean isDirEmpty() {
    return (mFileList.mFiles == null || mFileList.mFiles.length < 1);
}

    /* return name of file used, or memory buffer sentinel value.
     * The return value is used to delete the file/buffer later. */
    public synchronized SerializedJSONRows getFirstBatch() {
        final boolean dirEmpty = isDirEmpty();
        final int inMemoryReportsCount = mInMemoryActiveJSONRows.entriesCount();

        if (dirEmpty && inMemoryReportsCount < 1) {
            return null;
        }

        mJSONRowsObjectIterator = new SerializedJSONRowsList.Iterator(mFileList);

        if (inMemoryReportsCount > 0) {
            mInMemoryFinalizedJSONRowsObject = mInMemoryActiveJSONRows.finalizeToJSONRowsObject();
            mInMemoryFinalizedJSONRowsObject.storageState = SerializedJSONRows.StorageState.IN_MEMORY_ONLY;
            return mInMemoryFinalizedJSONRowsObject;
        } else {
            return getNextBatch();
        }
    }

    public synchronized SerializedJSONRows getNextBatch() {
        if (mJSONRowsObjectIterator == null) {
            return null;
        }

        int index = mJSONRowsObjectIterator.nextIndex();
        if (!mJSONRowsObjectIterator.isIndexValid(index)) {
            return null;
        }
        return mJSONRowsObjectIterator.getAtCurrentIndex();
    }

    protected File createFile(SerializedJSONRows unused) {
        final long time = clock.currentTimeMillis();
        final String name = FILENAME_PREFIX + SEP_TIME_MS + time + ".gz";
        return new File(mStorageDir, name);
    }

    // The filename is used to store info about the file (such as the time written),
    // this extracts that info to a long.
    public static long getLongFromFilename(String name, String separator) {
        final int s = name.indexOf(separator) + separator.length();
        int e = name.indexOf('-', s);
        if (e < 0) {
            e = name.indexOf('.', s);
        }
        return Long.parseLong(name.substring(s, e));
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
    private synchronized boolean saveFinalizedInMemoryDataToDisk() {
        if (mInMemoryFinalizedJSONRowsObject == null) {
            return true;
        }

        boolean result = saveToDisk(mInMemoryFinalizedJSONRowsObject);
        mInMemoryFinalizedJSONRowsObject = null;
        return result;
    }

    /*
     Return true if reports are saved.
     Return false only on a failed write to storage.
     */
    protected boolean saveToDisk(SerializedJSONRows data) {
        if (mFileList.mFilesOnDiskBytes > mMaxBytesDiskStorage) {
            return false;
        }
        data.storageState = SerializedJSONRows.StorageState.ON_DISK;
        FileOutputStream fos = null;
        File f = createFile(data);
        try {
            Log.i(LOG_TAG, "Preparing to write to : ["+f.getAbsolutePath()+"]");
            if (!f.exists()) {
                f.createNewFile();
            }
            fos = new FileOutputStream(f);
            fos.write(data.data);
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
        mFileList.update();
        return true;
    }

    /*
     Flushes the in-memory cache of the ChunkOfJSON to disk
     */
    public synchronized void saveAllInMemoryToDisk() {
        saveFinalizedInMemoryDataToDisk();
        if (mInMemoryActiveJSONRows.entriesCount() < 1) {
            return;
        }

        SerializedJSONRows json = mInMemoryActiveJSONRows.finalizeToJSONRowsObject();

        // Uncomment this block when debugging the report blobs
        //Log.d(LOG_TAG, "PII geosubmit report: " + report);
        // end debug blob

        saveToDisk(json);
    }

    protected synchronized void insertRow(JSONObject json) {
        notifyStorageIsEmpty(false);

        if (mFlushMemoryBuffersToDiskTimer != null) {
            mFlushMemoryBuffersToDiskTimer.cancel();
            mFlushMemoryBuffersToDiskTimer = null;
        }

        mInMemoryActiveJSONRows.addRow(json);

        if (mInMemoryActiveJSONRows.maxRowsReached()) {
            // save to disk
            saveAllInMemoryToDisk();
        } else {
            // Schedule a timer to flush to disk after a few mins.
            // If collection stops and wifi not available for uploading, the memory buffer is flushed to disk.
            final int kMillis = 1000 * 60 * 3;
            mFlushMemoryBuffersToDiskTimer = new Timer();
            mFlushMemoryBuffersToDiskTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    saveAllInMemoryToDisk();
                }
            }, kMillis);
        }
    }

    /* Pass filename returned from dataToSend() */
    public synchronized boolean delete(SerializedJSONRows data) {
        if (data.storageState == SerializedJSONRows.StorageState.IN_MEMORY_ONLY) {
            mInMemoryFinalizedJSONRowsObject = null;
            return true;
        }

        final File file = new File(mStorageDir, data.filename);
        boolean ok = file.delete();
        mFileList.update();
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

        mFileList.update();
    }

    private void notifyStorageIsEmpty(boolean isEmpty) {
        if (mTracker != null) {
            mTracker.notifyStorageStateEmpty(isEmpty);
        }
    }
}
