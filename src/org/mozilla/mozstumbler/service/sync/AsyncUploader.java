/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.sync;

import android.os.AsyncTask;
import org.mozilla.mozstumbler.service.AbstractCommunicator.SyncSummary;


/* Only one at a time may be uploading. If executed while another upload is in progress
* it will return immediately, and SyncResult is null. */
public class AsyncUploader extends AsyncTask<Void, Void, SyncSummary> {
    public interface AsyncUploaderListener {
        public void onUploadComplete(SyncSummary result);
        public void onUploadProgress();
    }

    private final Object mListenerLock = new Object();
    private AsyncUploaderListener mListener;
    private static boolean sIsUploading;

    // TODO: clarify how we want this accessed
    public boolean mShouldIgnoreWifiStatus = false;

    public AsyncUploader(AsyncUploaderListener listener) {
        mListener = listener;
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
        if (sIsUploading)
            return null;

        sIsUploading = true;
        SyncSummary result = new SyncSummary();
        UploadReports uploadReports = new UploadReports();
        Runnable progressListener = null;

        // no need to lock here, lock is checked again later
        if (mListener != null) {
            progressListener = new Runnable() {
                @Override
                public void run() {
                    synchronized (mListenerLock) {
                        if (mListener != null)
                            mListener.onUploadProgress();
                    }
                }
            };
        }

        uploadReports.uploadReports(mShouldIgnoreWifiStatus, result, progressListener);

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
}
