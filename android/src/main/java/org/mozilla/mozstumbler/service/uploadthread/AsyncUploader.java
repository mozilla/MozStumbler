/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.uploadthread;

import android.content.Intent;
import android.os.AsyncTask;
import android.support.v4.content.LocalBroadcastManager;
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
public class AsyncUploader extends AsyncTask<AsyncUploadParam, AsyncProgressListenerStatusWrapper, Void> {
    private static final String LOG_TAG = AppGlobals.LOG_PREFIX + AsyncUploader.class.getSimpleName();
    public static final AtomicLong sTotalBytesUploadedThisSession = new AtomicLong();

    public static final AtomicBoolean isUploading = new AtomicBoolean();
    public AsyncUploader() {}


    @Override
    protected Void doInBackground(AsyncUploadParam... params) {
       if (params.length != 1) {
           return null;
       }

       AsyncUploadParam param = params[0];
       if (!isUploading.compareAndSet(false, true)) {
           return null;
       }

       AsyncProgressListenerStatusWrapper wrapper = new AsyncProgressListenerStatusWrapper(
               param.asyncListener,
               true);

       publishProgress(wrapper);

       uploadReports(param);

       isUploading.set(false);
       wrapper = new AsyncProgressListenerStatusWrapper(param.asyncListener, false);
       publishProgress(wrapper);

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

        // @TODO: change this to send a broadcast signalling that
        // upload status has changed, and capture it in MainApp.

        if (callback.listener != null) {
            callback.listener.onUploadProgress(callback.uploading_flag);
        }
    }

    private void uploadReports(AsyncUploadParam param) {
        long uploadedObservations = 0;
        long uploadedCells = 0;
        long uploadedWifis = 0;
        long totalBytesSent = 0;

        if (param.useWifiOnly && !NetworkInfo.getInstance().isWifiAvailable()) {
            if (AppGlobals.isDebug) {
                Log.d(LOG_TAG, "not on WiFi, not sending");
            }
            return;
        }

        IHttpUtil httpUtil = new HttpUtil();
        ILocationService mls = new MLS(httpUtil);
        DataStorageManager dm = DataStorageManager.getInstance();

        String error = null;

        try {
            DataStorageManager.ReportBatch batch = dm.getFirstBatch();
            HashMap<String, String> headers = new HashMap<String, String>();
            headers.put(MLS.EMAIL_HEADER, param.emailAddress);
            headers.put(MLS.NICKNAME_HEADER, param.nickname);

            while (batch != null) {
               IResponse result = mls.submit(batch.data, headers, true);

                if (result != null && result.isSuccessCode2XX()) {
                    totalBytesSent += result.bytesSent();

                    String logMsg =  "MLS Submit: [HTTP Status:" + result.httpResponse() + "], [Bytes Sent:" + result.bytesSent() + "]";
                    AppGlobals.guiLogInfo(logMsg, "#FFFFCC", true);

                    dm.delete(batch.filename);

                    uploadedObservations += batch.reportCount;
                    uploadedWifis += batch.wifiCount;
                    uploadedCells += batch.cellCount;
                } else {
                    String logMsg = "HTTP error unknown";
                    if (result != null) {
                        logMsg = "HTTP non-success code: " + result.httpResponse();
                    }
                    
                    if (result != null && result.isErrorCode4xx()) {
                        logMsg += ", Error, deleting bad report";
                        // delete on 4xx, no point in resending
                        dm.delete(batch.filename);
                    } else {
                        DataStorageManager.getInstance().saveCurrentReportsSendBufferToDisk();
                    }
                    AppGlobals.guiLogError(logMsg);
                }

                batch = dm.getNextBatch();
            }
        }
        catch (IOException ex) {
            error = ex.toString();
        }

        sTotalBytesUploadedThisSession.addAndGet(totalBytesSent);

        try {
            dm.incrementSyncStats(totalBytesSent, uploadedObservations, uploadedCells, uploadedWifis);
        } catch (IOException ex) {
            error = ex.toString();
        } finally {
            if (error != null) {
                Log.d(LOG_TAG, error);
                AppGlobals.guiLogError(error + " (uploadReports)");
            }
        }
    }
}
