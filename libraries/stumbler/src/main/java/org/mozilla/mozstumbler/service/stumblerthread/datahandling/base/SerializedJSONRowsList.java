package org.mozilla.mozstumbler.service.stumblerthread.datahandling.base;

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

import org.acra.ACRA;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.core.logging.ClientLog;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public class SerializedJSONRowsList {
    public static class Iterator {
        public final SerializedJSONRowsList fileList;
        static final int INDEX_FOR_MEM_BUFFER = -1;
        public int currentIndex = INDEX_FOR_MEM_BUFFER;

        public Iterator(SerializedJSONRowsList list) {
            fileList = new SerializedJSONRowsList(list);
        }

        public SerializedJSONRows getAtCurrentIndex() {
            final File f = fileList.mFiles[currentIndex];
            final String filename = f.getName();
            final byte[] data = readFile(f);
            SerializedJSONRows serializedJSONRows = new SerializedJSONRows(data, SerializedJSONRows.StorageState.ON_DISK);
            serializedJSONRows.filename = f.getName();
            return serializedJSONRows;
        }

        public boolean isIndexValid(int index) {
            return  index > -1 && index < fileList.mFiles.length;
        }

        public int nextIndex() {
            return ++currentIndex;
        }

        private static byte[] readFile(File file) {
            RandomAccessFile f = null;
            try {
                f = new RandomAccessFile(file, "r");
                final byte[] data = new byte[(int) f.length()];
                f.readFully(data);
                return data;
            } catch (IOException e) {
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
    }

    public File[] mFiles;
    public long mFilesOnDiskBytes;

    public SerializedJSONRowsList(SerializedJSONRowsList other) {
        if (other == null) {
            return;
        }

        if (other.mFiles != null) {
            mFiles = other.mFiles.clone();
        }

        mFilesOnDiskBytes = other.mFilesOnDiskBytes;
    }

    public SerializedJSONRowsList() {

    }

    public void update(File directory) {
        mFiles = directory.listFiles();
        if (mFiles == null) {
            return;
        }

        if (AppGlobals.isDebug) {
            for (File f : mFiles) {
                ClientLog.d("StumblerFiles", f.getName());
            }
        }

        mFilesOnDiskBytes = 0;
        for (File f : mFiles) {
            mFilesOnDiskBytes += f.length();
        }
    }

    public synchronized boolean isDirEmpty() {
        return (mFiles == null || mFiles.length < 1);
    }
}
