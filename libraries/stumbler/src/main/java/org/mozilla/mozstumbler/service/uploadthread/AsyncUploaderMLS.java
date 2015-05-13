package org.mozilla.mozstumbler.service.uploadthread;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.core.http.ILocationService;
import org.mozilla.mozstumbler.service.core.http.ISubmitService;
import org.mozilla.mozstumbler.service.core.http.MLSLocationService;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.DataStorageManager;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.ReportBatch;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.base.SerializedJSONRows;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;

import java.util.HashMap;

public class AsyncUploaderMLS extends AsyncUploader {
    private final String OBSERVATIONS_TALLY = "observations", CELLS_TALLY = "cells", WIFIS_TALLY = "wifis";
    private final Context mContext;

    public static final String ACTION_MLS_UPLOAD_COMPLETED = AppGlobals.ACTION_NAMESPACE + ".action_mls_upload_executed";

    public AsyncUploaderMLS(DataStorageManager dataStorageManager, Context context) {
        super(dataStorageManager);
        mContext = context;
    }

    @Override
    protected ISubmitService getSubmitter() {
        return (ILocationService) ServiceLocator.getInstance().getService(ILocationService.class);
    }

    @Override
    protected HashMap<String,String> getHeaders(AsyncUploadParam param) {
        HashMap<String,String> headers = new HashMap<String, String>();
        headers.put(MLSLocationService.EMAIL_HEADER, param.emailAddress);
        headers.put(MLSLocationService.NICKNAME_HEADER, param.nickname);
        return headers;
    }

    protected void tally(HashMap<String, Integer> tallyValues, SerializedJSONRows batch) {
        assert(tallyValues != null);
        if (!tallyValues.containsKey(OBSERVATIONS_TALLY)) {
            tallyValues.put(OBSERVATIONS_TALLY, 0);
            tallyValues.put(CELLS_TALLY, 0);
            tallyValues.put(WIFIS_TALLY, 0);
        }

        ReportBatch reportBatch = (ReportBatch) batch;
        tallyValues.put(OBSERVATIONS_TALLY, tallyValues.get(OBSERVATIONS_TALLY) + reportBatch.reportCount);
        tallyValues.put(CELLS_TALLY, tallyValues.get(CELLS_TALLY) + reportBatch.cellCount);
        tallyValues.put(WIFIS_TALLY, tallyValues.get(WIFIS_TALLY) + reportBatch.wifiCount);
    }

    protected void tallyCompleted(HashMap<String, Integer> tally, long totalBytesSent) {
        assert(storageManager instanceof DataStorageManager);
        ((DataStorageManager) storageManager).incrementSyncStats(totalBytesSent,
                tally.get(OBSERVATIONS_TALLY),
                tally.get(CELLS_TALLY),
                tally.get(WIFIS_TALLY));

        LocalBroadcastManager.getInstance(mContext).sendBroadcast(new Intent(ACTION_MLS_UPLOAD_COMPLETED));
    }
}
