/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.mozstumbler.service.stumblerthread.datahandling;

import android.content.Context;
import android.os.Environment;

import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.core.logging.ClientLog;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class ClientDataStorageManager extends DataStorageManager {

    private static final String LOG_TAG = LoggerUtil.makeLogTag(ClientDataStorageManager.class);

    private ClientDataStorageManager(Context c, StorageIsEmptyTracker tracker, long maxBytesStoredOnDisk, int maxWeeksDataStored) {
        super(c, tracker, maxBytesStoredOnDisk, maxWeeksDataStored);

        mPersistedOnDiskUploadStats.forceBroadcastOfSyncStats();
    }

    public static String sdcardArchivePath() {
        return Environment.getExternalStorageDirectory() + File.separator + "MozStumbler";
    }

    // This 'overrides' the static createGlobalInstance method
    // DataStorageManager.  Sorta.  You can't really override static methods.
    public static synchronized DataStorageManager createGlobalInstance(Context context, StorageIsEmptyTracker tracker,
                                                                       long maxBytesStoredOnDisk, int maxWeeksDataStored) {

        if (sInstance == null) {
            sInstance = new ClientDataStorageManager(context, tracker, maxBytesStoredOnDisk, maxWeeksDataStored);
        }
        return sInstance;
    }

    /* Pass filename returned from dataToSend() */
    @Override
    public synchronized boolean delete(String filename) {
        if (filename.equals(MEMORY_BUFFER_NAME)) {
            return super.delete(filename);
        }

        final File file = new File(mStorageDir, filename);
        boolean ok = true;

        if (Prefs.getInstanceWithoutContext().isSaveStumbleLogs()) {
            File newFile = new File(sdcardArchivePath() + File.separator + filename);
            ok = copyAndDelete(file, newFile);

            if (!ok) {
                ok = file.delete();
            }
        } else {
            ok = file.delete();
        }

        mFileList.update(mStorageDir);
        return ok;
    }

    private boolean copyAndDelete(File aFile, File bFile) {
        boolean ok = true;

        FileInputStream inStream = null;
        FileOutputStream outStream = null;

        try {
            inStream = new FileInputStream(aFile);
            outStream = new FileOutputStream(bFile);
        } catch (FileNotFoundException e) {
            if (inStream != null) {
                try {
                    inStream.close();
                } catch (IOException ioEx) {
                    ClientLog.e(LOG_TAG, "error shutting down streams during a failed copy", ioEx);
                }
            }
            if (outStream != null) {
                try {
                    outStream.close();
                } catch (IOException ioEx) {
                    ClientLog.e(LOG_TAG, "error shutting down streams during a failed copy", ioEx);
                }
            }
            return false;
        }

        byte[] buffer = new byte[1024];
        int length;
        //copy the file content in bytes
        try {
            while ((length = inStream.read(buffer)) > 0) {
                outStream.write(buffer, 0, length);
            }
        } catch (IOException e) {
            ClientLog.e(LOG_TAG, "Error copying bytes over", e);

            if (inStream != null) {
                try {
                    inStream.close();
                } catch (IOException ioEx) {
                    ClientLog.e(LOG_TAG, "error shutting down streams during a failed copy", ioEx);
                }
            }
            if (outStream != null) {
                try {
                    outStream.close();
                } catch (IOException ioEx) {
                    ClientLog.e(LOG_TAG, "error shutting down streams during a failed copy", ioEx);
                }
            }
            return false;
        }

        if (inStream != null) {
            try {
                inStream.close();
            } catch (IOException ioEx) {
                ClientLog.e(LOG_TAG, "error shutting down streams during a failed copy", ioEx);
                ok = false;
            }
        }

        if (outStream != null) {
            try {
                outStream.close();
            } catch (IOException ioEx) {
                ClientLog.e(LOG_TAG, "error shutting down streams during a failed copy", ioEx);
                ok = false;
            }
        }

        // delete the original file
        return ok && aFile.delete();
    }
}
