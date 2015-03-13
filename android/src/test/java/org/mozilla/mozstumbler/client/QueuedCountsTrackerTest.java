/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.ClientDataStorageManager;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.DataStorageManager;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.ISystemClock;
import org.mozilla.mozstumbler.svclocator.services.MockSystemClock;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
public class QueuedCountsTrackerTest {

    public class StorageTracker implements DataStorageManager.StorageIsEmptyTracker {
        public void notifyStorageStateEmpty(boolean isEmpty) {
        }
    }

    @Before
    public void setup() {
        DataStorageManager.sInstance = null;
    }

    @Test
    public void testNoDataStorageManager() {
        QueuedCountsTracker qct = QueuedCountsTracker.getInstance();

        QueuedCountsTracker.QueuedCounts qc = qct.getQueuedCounts();
        assertNotNull(qc);
        assertEquals(0, qc.mBytes);
        assertEquals(0, qc.mCellCount);
        assertEquals(0, qc.mReportCount);
        assertEquals(0, qc.mWifiCount);
    }

    @Test
    public void testCachedValues() {

        Context ctx = Robolectric.application;

        StorageTracker tracker = new StorageTracker();

        long maxBytes = 20000;
        int maxWeeks = 10;

        MockSystemClock clock = new MockSystemClock();
        ServiceLocator.getInstance().putService(ISystemClock.class, clock);

        ClientDataStorageManager.createGlobalInstance(ctx, tracker, maxBytes, maxWeeks);
        QueuedCountsTracker qct = QueuedCountsTracker.getInstance();
        assertNotNull(DataStorageManager.getInstance());


        DataStorageManager.sInstance = spy(DataStorageManager.sInstance);

        doReturn(5).when(DataStorageManager.sInstance).getQueuedWifiCount();
        doReturn(3).when(DataStorageManager.sInstance).getQueuedCellCount();
        doReturn(9).when(DataStorageManager.sInstance).getQueuedReportCount();
        doReturn(new byte[]{0x15, 0x17, 0x18}).when(DataStorageManager.sInstance).getCurrentReportsRawBytes();

        clock.setCurrentTime(900000);
        QueuedCountsTracker.QueuedCounts qc = qct.getQueuedCounts();

        assertEquals(5, qc.mWifiCount);
        assertEquals(3, qc.mCellCount);
        assertEquals(9, qc.mReportCount);
        assertEquals(23, qc.mBytes);

        doReturn(new byte[]{
                0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19,
                0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27, 0x28, 0x29
        }).when(DataStorageManager.sInstance).getCurrentReportsRawBytes();

        QueuedCountsTracker.QueuedCounts qc2 = qct.getQueuedCounts();

        // These should be the same object because we haven't pushed the clock forward.
        assertEquals(23, qc.mBytes);

        clock.setCurrentTime(999999);
        QueuedCountsTracker.QueuedCounts qc3 = qct.getQueuedCounts();
        assertEquals(40, qc3.mBytes);


    }


}
