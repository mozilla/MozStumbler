/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.sync;

import android.util.Log;
import org.mozilla.mozstumbler.service.AbstractCommunicator;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.datahandling.DataStorageManager;
import org.mozilla.mozstumbler.service.utils.NetworkUtils;
import java.io.IOException;

public class UploadReports {

    static final String LOG_TAG = UploadReports.class.getName();

    public void uploadReports(boolean ignoreNetworkStatus, AbstractCommunicator.SyncSummary syncResult, Runnable progressListener) {
        long uploadedObservations = 0;
        long uploadedCells = 0;
        long uploadedWifis = 0;

        if (!ignoreNetworkStatus && Prefs.getInstance().getUseWifiOnly() && !NetworkUtils.getInstance().isWifiAvailable()) {
            if (AppGlobals.isDebug) Log.d(LOG_TAG, "not on WiFi, not sending");
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

                if (progressListener != null)
                    progressListener.run();

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
