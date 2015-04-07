/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.uploadthread;

import android.os.AsyncTask;
import android.util.Log;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.core.http.HTTPResponse;
import org.mozilla.mozstumbler.service.core.http.ILocationService;
import org.mozilla.mozstumbler.service.core.http.IResponse;
import org.mozilla.mozstumbler.service.core.http.MLS;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.DataStorageManager;
import org.mozilla.mozstumbler.service.utils.NetworkInfo;
import org.mozilla.mozstumbler.service.utils.Zipper;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
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
public class AsyncUploader extends AsyncTask<AsyncUploadParam, AsyncProgressListenerStatusWrapper, Void> {
    public static final AtomicLong sTotalBytesUploadedThisSession = new AtomicLong();
    public static final AtomicBoolean isUploading = new AtomicBoolean();
    private static final String LOG_TAG = LoggerUtil.makeLogTag(AsyncUploader.class);
    private static AsyncUploaderListener sAsyncListener;
    ILocationService mls = (ILocationService) ServiceLocator.getInstance().getService(ILocationService.class);

    // This listener can show progress for any AsyncUploader. This global use is particularly
    // useful for UI to show progress when this has been scheduled internally in the service.
    public static void setGlobalUploadListener(AsyncUploaderListener listener) {
        sAsyncListener = listener;
    }

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


        if (Prefs.getInstanceWithoutContext().isSaveStumbleLogs()) {
            DataStorageManager.getInstance().saveCurrentReportsToDisk();
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

        DataStorageManager dm = DataStorageManager.getInstance();
        DataStorageManager.ReportBatch batch = dm.getFirstBatch();
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put(MLS.EMAIL_HEADER, param.emailAddress);
        headers.put(MLS.NICKNAME_HEADER, param.nickname);

        Prefs prefs = Prefs.getInstanceWithoutContext();
        while (batch != null) {
            IResponse result;
            if (prefs != null && prefs.isSimulateStumble()) {

                result = new HTTPResponse(200,
                        new HashMap<String, List<String>>(),
                        new byte[]{},
                        batch.data.length);
                Log.i(LOG_TAG, "Simulation skipped upload.");
            } else {
                result = mls.submit(batch.data, headers, true);
            }

            if (result != null && result.isSuccessCode2XX()) {
                totalBytesSent += result.bytesSent();

                String logMsg = "MLS geosubmit: [HTTP Status:" + result.httpStatusCode() + "], [Bytes Sent:" + result.bytesSent() + "]";
                AppGlobals.guiLogInfo(logMsg, "#FFFFCC", true, false);
                Log.d(LOG_TAG, logMsg);

                dm.delete(batch.filename);

                uploadedObservations += batch.reportCount;
                uploadedWifis += batch.wifiCount;
                uploadedCells += batch.cellCount;
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
                    dm.delete(batch.filename);
                } else {
                    dm.saveCurrentReportsToDisk();
                }
                AppGlobals.guiLogError(logMsg);
            }

            batch = dm.getNextBatch();
        }

        sTotalBytesUploadedThisSession.addAndGet(totalBytesSent);

        dm.incrementSyncStats(totalBytesSent, uploadedObservations, uploadedCells, uploadedWifis);
    }

    public interface AsyncUploaderListener {
        // This is called by Android on the UI thread
        public void onUploadProgress(boolean isUploading);
    }
}
