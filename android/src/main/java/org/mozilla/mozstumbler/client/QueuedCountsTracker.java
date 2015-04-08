/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client;

import org.mozilla.mozstumbler.service.stumblerthread.datahandling.DataStorageManager;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.IDataStorageManager;
import org.mozilla.mozstumbler.service.utils.Zipper;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.ISystemClock;

// Tracks counts of queued data for reporting in the UI
public class QueuedCountsTracker {
    long mCachedByteCount;
    long mPrevAccessTimeMs;

    public static class QueuedCounts {
        public final int mReportCount;
        public final int mWifiCount;
        public final int mCellCount;
        public final long mBytes;

        QueuedCounts(int reportCount, int wifiCount, int cellCount, long bytes) {
            this.mReportCount = reportCount;
            this.mWifiCount = wifiCount;
            this.mCellCount = cellCount;
            this.mBytes = bytes;
        }
    }

    private static QueuedCountsTracker sInstance;

    public static QueuedCountsTracker getInstance() {
        if (sInstance == null) {
            sInstance = new QueuedCountsTracker();
        }
        return sInstance;
    }

    private QueuedCountsTracker() {}

    private long getInMemoryReportsUploadBytes() {
        ISystemClock clock = (ISystemClock) ServiceLocator.getInstance().getService(ISystemClock.class);

        IDataStorageManager dsm = DataStorageManager.getInstance();
        if (dsm == null) {
            return 0;
        }

        byte[] bytes = dsm.getCurrentReportsRawBytes();
        if (bytes == null) {
            mCachedByteCount = 0;
            return 0;
        }

        final long kTimeGapMs = 2000;
        if (clock.currentTimeMillis() - mPrevAccessTimeMs < kTimeGapMs) {
            return mCachedByteCount;
        }

        mPrevAccessTimeMs = clock.currentTimeMillis();

        bytes = Zipper.zipData(bytes);
        if (bytes == null) {
            return 0;
        }

        mCachedByteCount = bytes.length;
        return mCachedByteCount;
    }

    /* Some data is calculated on-demand, don't abuse this function */
    public QueuedCounts getQueuedCounts() {
        IDataStorageManager dsm = DataStorageManager.getInstance();
        if (dsm == null ) {
            return new QueuedCounts(0, 0, 0, 0);
        }
        long byteLength = getInMemoryReportsUploadBytes() +  dsm.getQueuedZippedDataSize();
        return new QueuedCounts(dsm.getQueuedReportCount(),
                dsm.getQueuedWifiCount(),
                dsm.getQueuedCellCount(),
                byteLength);
    }
}
