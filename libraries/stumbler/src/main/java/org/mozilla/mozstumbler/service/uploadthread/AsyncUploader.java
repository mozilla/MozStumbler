/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.uploadthread;

import android.os.AsyncTask;
import android.util.Log;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.core.http.HTTPResponse;
import org.mozilla.mozstumbler.service.core.http.IResponse;
import org.mozilla.mozstumbler.service.core.http.ISubmitService;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.base.JSONRowsStorageManager;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.base.SerializedJSONRows;
import org.mozilla.mozstumbler.service.utils.Zipper;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/* Only one at a time may be uploading. If executed while another upload is in progress
* it will return immediately, and SyncResult is null.
*
* Threading:
* Uploads on a separate thread. ONLY DataStorageManager is thread-safe, do not call
* preferences, do not call any code that isn't thread-safe. You will cause suffering.
* An exception is made for AppGlobals.isDebug, a false reading is of no consequence.
*
* AsyncUploader is used in 3 places.
*   1. the MetricsView in the upload button.
*   2. MainApp where it is invoked immediately when scanning is stopped
*   3. On a timer by the UploadAlarmReceiver.
*
* We have a weak method of managing access to the AsyncUploader by using an
* AtomicBoolean AsyncUploader.isUploading and use it as a guard before
* initiating execution of AsyncUploader.
*
* */
public abstract class AsyncUploader extends AsyncTask<AsyncUploadParam, AsyncProgressListenerStatusWrapper, Void> {
    public static final AtomicLong sTotalBytesUploadedThisSession = new AtomicLong();
    public static final AtomicBoolean isUploading = new AtomicBoolean();
    private static final String LOG_TAG = LoggerUtil.makeLogTag(AsyncUploader.class);
    private static AsyncUploaderListener sAsyncListener;
    protected final JSONRowsStorageManager storageManager;

    public AsyncUploader(JSONRowsStorageManager storageManager) {
        this.storageManager = storageManager;
    }

    // This listener can show progress for any AsyncUploader. This global use is particularly
    // useful for UI to show progress when this has been scheduled internally in the service.
    public static void setGlobalUploadListener(AsyncUploaderListener listener) {
        sAsyncListener = listener;
    }

    protected abstract ISubmitService getSubmitter();

    @Override
    protected Void doInBackground(AsyncUploadParam... params) {
        AsyncProgressListenerStatusWrapper wrapper;

        if (params.length != 1) {
            return null;
        }

        AsyncUploadParam param = params[0];
        if (!isUploading.compareAndSet(false, true)) {
            return null;
        }

        if (sAsyncListener != null) {
            wrapper = new AsyncProgressListenerStatusWrapper(sAsyncListener, true);
            sAsyncListener.onUploadProgress(true);
            publishProgress(wrapper);
        }

        uploadReports(param);

        isUploading.set(false);
        if (sAsyncListener != null) {
            wrapper = new AsyncProgressListenerStatusWrapper(sAsyncListener, false);
            publishProgress(wrapper);
        }

        return null;
    }

    /*
    The android framework calls this only on the UI thread
    */
    @Override
    protected void onProgressUpdate(AsyncProgressListenerStatusWrapper... params) {
        if (params.length != 1) {
            return;
        }

        AsyncProgressListenerStatusWrapper callback = params[0];

        if (callback.listener != null) {
            callback.listener.onUploadProgress(callback.uploading_flag);
        }
    }

    private void uploadReports(AsyncUploadParam param) {
        long totalBytesSent = 0;
        HashMap<String, Integer> tally = new HashMap<String, Integer>();

        if (!checkCanUpload(param)) {
            return;
        }

        SerializedJSONRows batch = storageManager.getFirstBatch();
        HashMap<String, String> headers = getHeaders(param);

        Prefs prefs = Prefs.getInstanceWithoutContext();
        while (batch != null) {
            IResponse result = null;
            if (prefs != null && prefs.isSimulateStumble()) {

                result = new HTTPResponse(200,
                        new HashMap<String, List<String>>(),
                        new byte[0],
                        batch.data.length);
                Log.i(LOG_TAG, "Simulation skipped upload.");
            } else {
                result = getSubmitter().submit(batch.data, headers, true);
            }

            if (result != null && result.isSuccessCode2XX()) {
                totalBytesSent += result.bytesSent();

                String logMsg = "submit: [HTTP Status:" + result.httpStatusCode() + "], [Bytes Sent:" + result.bytesSent() + "]";
                AppGlobals.guiLogInfo(logMsg, "#FFFFCC", true, false);
                Log.d(LOG_TAG, logMsg);

                batch.tally(tally);

                storageManager.delete(batch);
            } else {
                String logMsg = "HTTP error unknown";
                if (result != null) {
                    logMsg = "HTTP non-success code: " + result.httpStatusCode();
                }

                if (result != null && result.isErrorCode400BadRequest()) {
                    logMsg += ", 400 Error, deleting bad report";
                    if (AppGlobals.guiLogMessageBuffer != null) { // if true, this is a GUI app
                        String unzipped = Zipper.unzipData(batch.data);
                        if (unzipped == null) {
                            unzipped = "Corrupt gzip data found.";
                        }
                        AppGlobals.guiLogInfo(unzipped, "red", false, true);
                    }
                    storageManager.delete(batch);
                } else {
                    storageManager.saveAllInMemoryToDisk();
                }
                AppGlobals.guiLogError(logMsg);
            }

            batch = storageManager.getNextBatch();
        }

        sTotalBytesUploadedThisSession.addAndGet(totalBytesSent);

        tallyCompleted(tally, totalBytesSent);
    }

    protected boolean checkCanUpload(AsyncUploadParam param) {
        if (param.useWifiOnly && !param.isWifiAvailable) {
            if (AppGlobals.isDebug) {
                Log.d(LOG_TAG, "not on WiFi, not sending");
            }
            return false;
        }
        return true;
    }

    protected void tallyCompleted(HashMap<String, Integer> tallyValues, long totalBytesSent) {
    }

    protected abstract HashMap<String,String> getHeaders(AsyncUploadParam param);

    public interface AsyncUploaderListener {
        // This is called by Android on the UI thread
        public void onUploadProgress(boolean isUploading);
    }
}

