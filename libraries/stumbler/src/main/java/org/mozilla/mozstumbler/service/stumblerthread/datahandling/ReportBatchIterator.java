/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread.datahandling;

class ReportBatchIterator {
    static final int BATCH_INDEX_FOR_MEM_BUFFER = -1;
    public final ReportFileList fileList;
    public int currentIndex = BATCH_INDEX_FOR_MEM_BUFFER;

    public ReportBatchIterator(ReportFileList list) {
        fileList = new ReportFileList(list);
    }
}
