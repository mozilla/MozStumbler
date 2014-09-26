/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.uploadthread;

import android.os.AsyncTask;
import android.util.Log;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.mozilla.mozstumbler.service.core.http.HttpUtil;
import org.mozilla.mozstumbler.service.core.http.IHttpUtil;
import org.mozilla.mozstumbler.service.core.http.ILocationService;
import org.mozilla.mozstumbler.service.core.http.IResponse;
import org.mozilla.mozstumbler.service.core.http.MLS;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.DataStorageManager;
import org.mozilla.mozstumbler.service.utils.NetworkInfo;
import org.mozilla.mozstumbler.service.utils.SyncSummary;

/* Only one at a time may be uploading. If executed while another upload is in progress
* it will return immediately, and SyncResult is null.
*
* Threading:
* Uploads on a separate thread. ONLY DataStorageManager is thread-safe, do not call
* preferences, do not call any code that isn't thread-safe. You will cause suffering.
* An exception is made for AppGlobals.isDebug, a false reading is of no consequence. */
public class AsyncUploader extends AsyncTask<Void, Void, SyncSummary> {
    private static final String LOG_TAG = AppGlobals.LOG_PREFIX + AsyncUploader.class.getSimpleName();
    private final UploadSettings mSettings;
    private final Object mListenerLock = new Object();
    private AsyncUploaderListener mListener;
    private static AtomicBoolean sIsUploading = new AtomicBoolean();
    private String mNickname = "";
    private String mEmail = "";


    public static final AtomicLong sTotalBytesUploadedThisSession = new AtomicLong();

    public interface AsyncUploaderListener {
        public void onUploadComplete(SyncSummary result);
        public void onUploadProgress();
    }

    public static class UploadSettings {
        public final boolean mUseWifiOnly;
        public UploadSettings(boolean useWifiOnly) {
            mUseWifiOnly = useWifiOnly;
        }
    }

    public AsyncUploader(UploadSettings settings, AsyncUploaderListener listener) {
        mListener = listener;
        mSettings = settings;
    }

    public void setNickname(String name) {
        mNickname = name;
    }

    public void setEmail(String email) {
        mEmail = email;
    }

    public void clearListener() {
        synchronized (mListenerLock) {
            mListener = null;
        }
    }

    public static boolean isUploading() {
        return sIsUploading.get();
    }

    @Override
    protected SyncSummary doInBackground(Void... voids) {
        if (sIsUploading.get()) {
            // This if-block is not synchronized, don't care, this is an erroneous usage.
            Log.d(LOG_TAG, "Usage error: check isUploading first, only one at a time task usage is permitted.");
            return null;
        }

        sIsUploading.set(true);
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

        SyncSummary result = new SyncSummary();
        uploadReports(result, progressListener);
        return result;
    }

    @Override
    protected void onPostExecute(SyncSummary result) {
        sIsUploading.set(false);

        synchronized (mListenerLock) {
            if (mListener != null) {
                mListener.onUploadComplete(result);
            }
        }
    }
    @Override
    protected void onCancelled(SyncSummary result) {
        sIsUploading.set(false);
    }

    private void uploadReports(SyncSummary syncResult, Runnable progressListener) {
        long uploadedObservations = 0;
        long uploadedCells = 0;
        long uploadedWifis = 0;

        if (mSettings.mUseWifiOnly && !NetworkInfo.getInstance().isWifiAvailable()) {
            if (AppGlobals.isDebug) {
                Log.d(LOG_TAG, "not on WiFi, not sending");
            }
            syncResult.numIoExceptions += 1;
            return;
        }


        IHttpUtil httpUtil = new HttpUtil();
        ILocationService mls = new MLS(httpUtil);
        DataStorageManager dm = DataStorageManager.getInstance();

        String error = null;

        try {
            DataStorageManager.ReportBatch batch = dm.getFirstBatch();
            HashMap<String, String> headers = new HashMap<String, String>();
            headers.put(MLS.EMAIL_HEADER, mEmail);
            headers.put(MLS.NICKNAME_HEADER, mNickname);

            while (batch != null) {
               IResponse result = mls.submit(batch.data, headers, true);

                if (result.isSuccessCode2XX()) {
                    syncResult.totalBytesSent += result.bytesSent();

                    dm.delete(batch.filename);

                    uploadedObservations += batch.reportCount;
                    uploadedWifis += batch.wifiCount;
                    uploadedCells += batch.cellCount;
                } else {
                    if (result.isErrorCode4xx()) {
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

        sTotalBytesUploadedThisSession.addAndGet(syncResult.totalBytesSent);

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
        }
    }
}
