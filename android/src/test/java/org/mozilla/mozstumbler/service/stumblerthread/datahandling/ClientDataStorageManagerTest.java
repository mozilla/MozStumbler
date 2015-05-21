/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread.datahandling;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mozilla.mozstumbler.client.ClientDataStorageManager;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.ISystemClock;
import org.mozilla.mozstumbler.svclocator.services.MockSystemClock;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.LinkedList;
import java.util.Properties;

import static junit.framework.Assert.assertEquals;

@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
public class ClientDataStorageManagerTest {

    private class StorageTracker implements StorageIsEmptyTracker {
        public void notifyStorageStateEmpty(boolean isEmpty) {
        }
    }

    private final BroadcastReceiver callbackReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Just capture the intent for testing
            receivedIntent.add(intent);
        }
    };

    private LinkedList<Intent> receivedIntent = new LinkedList<Intent>();

    @Before
    public void setUp() {
        ClientDataStorageManager.sInstance = null;
    }

    @Test
    public void testForceSendMetrics() {

        receivedIntent.clear();

        Context ctx = Robolectric.application;

        LocalBroadcastManager.getInstance(ctx).registerReceiver(callbackReceiver,
                new IntentFilter(PersistedStats.ACTION_PERSISTENT_SYNC_STATUS_UPDATED));

        StorageTracker tracker = new StorageTracker();

        long maxBytes = 20000;
        int maxWeeks = 10;

        MockSystemClock clock = new MockSystemClock();

        final long ARBITRARY_CLOCK_TIME = 0;
        clock.setCurrentTime(ARBITRARY_CLOCK_TIME);

        ServiceLocator.getInstance().putService(ISystemClock.class, clock);

        ClientDataStorageManager.createGlobalInstance(ctx, tracker, maxBytes, maxWeeks);

        assertEquals(1, receivedIntent.size());
        Intent i = receivedIntent.get(0);

        Bundle bundle = i.getExtras();
        Properties props = (Properties) bundle.get(PersistedStats.EXTRAS_PERSISTENT_SYNC_STATUS_UPDATED);
        assertEquals(Long.toString(ARBITRARY_CLOCK_TIME), props.getProperty(DataStorageConstants.Stats.KEY_LAST_UPLOAD_TIME, "0"));
    }
}
