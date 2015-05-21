/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread.datahandling;

import org.mozilla.mozstumbler.service.stumblerthread.datahandling.base.SerializedJSONRows;

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
}
