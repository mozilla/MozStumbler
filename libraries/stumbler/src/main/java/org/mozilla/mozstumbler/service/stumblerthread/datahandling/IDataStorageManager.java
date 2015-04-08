/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread.datahandling;

import java.io.IOException;

/*
 This interface just exists to firm up the public interface of the DataStorageManager
 */
public interface IDataStorageManager {

    public int getQueuedReportCount();

    public int getQueuedWifiCount();

    public int getQueuedCellCount();

    public long getQueuedZippedDataSize();

    public byte[] getCurrentReportsRawBytes();

    public boolean delete(String filename);

    public void saveCurrentReportsSendBufferToDisk() throws IOException;

    public ReportBatch getFirstBatch() throws IOException;

    public ReportBatch getNextBatch() throws IOException;

    public void incrementSyncStats(long bytesSent, long reports, long cells, long wifis) throws IOException;

    public void insert(String report, int wifiCount, int cellCount) throws IOException;

    public void saveCurrentReportsToDisk() throws IOException;

    public boolean isDirEmpty();

    public long getOldestBatchTimeMs();

    public int getMaxWeeksStored();

    public void deleteAll();

}
