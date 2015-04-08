/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread.datahandling;

public class ReportBatch {
    public final String filename;
    public final byte[] data;
    public final int reportCount;
    public final int wifiCount;
    public final int cellCount;

    public ReportBatch(String filename, byte[] data, int reportCount, int wifiCount, int cellCount) {
        this.filename = filename;
        this.data = data;
        this.reportCount = reportCount;
        this.wifiCount = wifiCount;
        this.cellCount = cellCount;
    }
}
