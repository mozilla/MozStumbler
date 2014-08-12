/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.sync;

import android.os.AsyncTask;
import android.util.Log;
import java.io.IOException;
import org.mozilla.mozstumbler.service.AbstractCommunicator;
import org.mozilla.mozstumbler.service.AbstractCommunicator.SyncSummary;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.datahandling.DataStorageManager;
import org.mozilla.mozstumbler.service.utils.NetworkUtils;

/* Only one at a time may be uploading. If executed while another upload is in progress
* it will return immediately, and SyncResult is null.
*
* Threading:
* Uploads on a separate thread. ONLY DataStorageManager is thread-safe, do not call
* preferences, do not call any code that isn't thread-safe. You will cause suffering.
* An exception is made for AppGlobals.isDebug, a false reading is of no consequence. */
public class AsyncUploader extends AsyncTask<Void, Void, SyncSummary> {
    public interface AsyncUploaderListener {
        public void onUploadComplete(SyncSummary result);
        public void onUploadProgress();
    }

    private final UploadSettings mSettings;

    private final Object mListenerLock = new Object();
    private AsyncUploaderListener mListener;
    private static boolean sIsUploading;

    public static class UploadSettings {
        public final boolean mShouldIgnoreWifiStatus;
        public final boolean mUseWifiOnly;
        public UploadSettings(boolean shouldIgnoreWifiStatus, boolean useWifiOnly) {
            mShouldIgnoreWifiStatus = shouldIgnoreWifiStatus;
            mUseWifiOnly = useWifiOnly;
        }
    }

    public AsyncUploader(UploadSettings settings, AsyncUploaderListener listener) {
        mListener = listener;
        mSettings = settings;
    }

    public void clearListener() {
        synchronized (mListenerLock) {
            mListener = null;
        }
    }

    public static boolean getIsUploading() {
        return sIsUploading;
    }

    @Override
    protected SyncSummary doInBackground(Void... voids) {
        if (sIsUploading) {
            return null;
        }

        sIsUploading = true;
        SyncSummary result = new SyncSummary();
        Runnable progressListener = null;

        // no need to lock here, lock is checked again later
        if (mListener != null) {
            progressListener = new Runnable() {
                @Override
                public void run() {
                    synchronized (mListenerLock) {
                        if (mListener != null) {
                            mListener.onUploadProgress();
                        }
                    }
                }
            };
        }

        uploadReports(result, progressListener);

        return result;
    }
    @Override
    protected void onPostExecute(SyncSummary result) {
        sIsUploading = false;

        synchronized (mListenerLock) {
            if (mListener != null) {
                mListener.onUploadComplete(result);
            }
        }
    }
    @Override
    protected void onCancelled(SyncSummary result) {
        sIsUploading = false;
    }

    final String LOG_TAG = "Stumbler:" + AsyncUploader.class.getSimpleName();

    private void uploadReports(AbstractCommunicator.SyncSummary syncResult, Runnable progressListener) {
        long uploadedObservations = 0;
        long uploadedCells = 0;
        long uploadedWifis = 0;

        if (!mSettings.mShouldIgnoreWifiStatus && mSettings.mUseWifiOnly && !NetworkUtils.getInstance().isWifiAvailable()) {
            if (AppGlobals.isDebug) {
                Log.d(LOG_TAG, "not on WiFi, not sending");
            }
            syncResult.numIoExceptions += 1;
            return;
        }

        Submitter submitter = new Submitter();
        DataStorageManager dm = DataStorageManager.getInstance();

        String error = null;

        try {
            DataStorageManager.ReportBatch batch = dm.getFirstBatch();
            while (batch != null) {
                AbstractCommunicator.NetworkSendResult result = submitter.cleanSend(batch.data);

                if (result.errorCode == 0) {
                    syncResult.totalBytesSent += result.bytesSent;

                    dm.delete(batch.filename);

                    uploadedObservations += batch.reportCount;
                    uploadedWifis += batch.wifiCount;
                    uploadedCells += batch.cellCount;
                } else {
                    if (result.errorCode / 100 == 4) {
                        // delete on 4xx, no point in resending
                        dm.delete(batch.filename);
                    } else {
                        DataStorageManager.getInstance().saveCurrentReportsSendBufferToDisk();
                    }
                    syncResult.numIoExceptions += 1;
                }

                if (progressListener != null) {
                    progressListener.run();
                }

                batch = dm.getNextBatch();
            }
        }
        catch (IOException ex) {
            error = ex.toString();
        }

        try {
            dm.incrementSyncStats(syncResult.totalBytesSent, uploadedObservations, uploadedCells, uploadedWifis);
        } catch (IOException ex) {
            error = ex.toString();
        } finally {
            if (error != null) {
                syncResult.numIoExceptions += 1;
                Log.d(LOG_TAG, error);
                AppGlobals.guiLogError(error + " (uploadReports)");
            }
            submitter.close();
        }
    }
}
