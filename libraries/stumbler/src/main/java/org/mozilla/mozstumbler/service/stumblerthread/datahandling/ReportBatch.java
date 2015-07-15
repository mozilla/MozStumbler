/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread.datahandling;

import org.mozilla.mozstumbler.service.stumblerthread.datahandling.base.SerializedJSONRows;
import org.mozilla.mozstumbler.service.uploadthread.AsyncUploaderMLS;

import java.util.HashMap;

public class ReportBatch extends SerializedJSONRows {
    public final int reportCount;
    public final int wifiCount;
    public final int cellCount;

    public ReportBatch(byte[] data, StorageState storageState, int reportCount, int wifiCount, int cellCount) {
        super(data, storageState);
        this.reportCount = reportCount;
        this.wifiCount = wifiCount;
        this.cellCount = cellCount;
    }

    @Override
    public void tally(HashMap<String, Integer> tallyValues) {
        assert(tallyValues != null);
        if (!tallyValues.containsKey(AsyncUploaderMLS.OBSERVATIONS_TALLY)) {
            tallyValues.put(AsyncUploaderMLS.OBSERVATIONS_TALLY, 0);
            tallyValues.put(AsyncUploaderMLS.CELLS_TALLY, 0);
            tallyValues.put(AsyncUploaderMLS.WIFIS_TALLY, 0);
        }
        tallyValues.put(AsyncUploaderMLS.OBSERVATIONS_TALLY, tallyValues.get(AsyncUploaderMLS.OBSERVATIONS_TALLY) + reportCount);
        tallyValues.put(AsyncUploaderMLS.CELLS_TALLY, tallyValues.get(AsyncUploaderMLS.CELLS_TALLY) + cellCount);
        tallyValues.put(AsyncUploaderMLS.WIFIS_TALLY, tallyValues.get(AsyncUploaderMLS.WIFIS_TALLY) + wifiCount);
    }
}
