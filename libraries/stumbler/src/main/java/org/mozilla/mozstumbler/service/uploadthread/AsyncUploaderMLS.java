package org.mozilla.mozstumbler.service.uploadthread;

import org.mozilla.mozstumbler.service.stumblerthread.datahandling.DataStorageManager;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.ReportBatch;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.base.SerializedJSONRows;

import java.util.HashMap;

public class AsyncUploaderMLS extends AsyncUploader {
    final String observationsTally = "observations", cellsTally = "cells", wifisTally = "wifis";

    public AsyncUploaderMLS(DataStorageManager dataStorageManager) {
        super(dataStorageManager);
    }

    protected void tally(HashMap<String, Integer> tallyValues, SerializedJSONRows batch) {
        assert(tallyValues != null);
        if (!tallyValues.containsKey(observationsTally)) {
            tallyValues.put(observationsTally, 0);
            tallyValues.put(cellsTally, 0);
            tallyValues.put(wifisTally, 0);
        }

        ReportBatch reportBatch = (ReportBatch) batch;
        tallyValues.put(observationsTally, tallyValues.get(observationsTally) + reportBatch.reportCount);
        tallyValues.put(cellsTally, tallyValues.get(cellsTally) + reportBatch.cellCount);
        tallyValues.put(wifisTally, tallyValues.get(wifisTally) + reportBatch.wifiCount);

    }

    protected void tallyCompleted(HashMap<String, Integer> tally, long totalBytesSent) {
        assert(storageManager instanceof DataStorageManager);
        ((DataStorageManager) storageManager).incrementSyncStats(totalBytesSent,
                tally.get(observationsTally),
                tally.get(cellsTally),
                tally.get(wifisTally));
    }
}
