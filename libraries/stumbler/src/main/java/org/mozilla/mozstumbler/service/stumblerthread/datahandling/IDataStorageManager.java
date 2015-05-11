/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread.datahandling;

import org.mozilla.mozstumbler.service.stumblerthread.datahandling.base.SerializedJSONRows;

/*
 This interface just exists to firm up the public interface of the DataStorageManager
 */
public interface IDataStorageManager {

    // Some stats retrieval methods on the DSM
    public int getQueuedReportCount();

    public int getQueuedWifiCount();

    public int getQueuedCellCount();

    public long getQueuedZippedDataSize();

    public byte[] getCurrentReportsRawBytes();

    // Insert a report into storage
    public void insert(MLSJSONObject geoSubmitObj);

    // It feels like this method should be pushed behind the DataStorageManager implementation
    public void incrementSyncStats(long bytesSent, long reports, long cells, long wifis);

    // This is used only by the StumblerService on initial startup of the service in the
    // `onHandleIntent` method.  We use it to test if there are any files waiting to be uploaded
    // on initial process startup.
    // We should get rid of this method `isDirEmpty` and instead have a method like
    // `uploadDataOnProcessStartup` and push the logic from StumblerService down into the
    // DataStorageManager.
    public boolean isDirEmpty();

    public void saveCachedReportsToDisk();

    // Getting an instance of ReportBatch should really hang off of an iterator.
    // We have two methods because we have a special case where the first batch is
    // all reports that are currently stored only in-memory.  getNextBatch will always hit disk.
    // We should extend the ReportBatchIterator to have knowledge of the in-memory reports
    // and implement java.util.Iterator to return the ReportBatch instances.
    public SerializedJSONRows getFirstBatch();
    public SerializedJSONRows getNextBatch();

    // These 3 methods are *only* used to determine if we have stale data and if we should be
    // clearing it out.  We should collapse these into one method call and return a boolean
    // to indicate that the pending reports have been truncated.
    public long getOldestBatchTimeMs();

    public int getMaxWeeksStored();

    public void deleteAll();

    // delete should be pushed down into ReportBatch as it already has everything except
    // DataStorageManager::mReportsDir to do this work.
    public boolean delete(String filename);
}
