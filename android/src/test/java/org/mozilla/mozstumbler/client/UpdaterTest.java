/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.mozstumbler.client;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mozilla.mozstumbler.client.navdrawer.MainDrawerActivity;
import org.mozilla.mozstumbler.service.core.http.IHttpUtil;
import org.mozilla.mozstumbler.service.core.http.MockHttpUtil;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.ISystemClock;
import org.mozilla.mozstumbler.svclocator.services.MockSystemClock;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;


@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
public class UpdaterTest {

    private MainDrawerActivity activity;

    @Before
    public void setUp() throws Exception {
        activity = Robolectric.newInstanceOf(MainDrawerActivity.class);
        Updater.sLastUpdateCheck = 0;
    }

    @Test
    public void testActivityShouldNotBeNull() {
        assertNotNull(activity);
    }

    @Test
    public void testUpdater() {
        IHttpUtil mockHttp = new MockHttpUtil();

        Updater upd = new Updater();
        upd = spy(upd);

        // Setup mocks.
        // Replace the HTTP client and clock
        MockSystemClock clock = new MockSystemClock();
        long now = System.currentTimeMillis();
        clock.setCurrentTime(now);
        ServiceLocator.getInstance().putService(IHttpUtil.class, mockHttp);
        ServiceLocator.getInstance().putService(ISystemClock.class, clock);

        // wifi is always unavailable
        doReturn(false).when(upd).wifiExclusiveAndUnavailable(Mockito.any(Context.class));

        assertFalse(upd.checkForUpdates(activity, ""));
        now += Updater.UPDATE_CHECK_FREQ_MS;
        clock.setCurrentTime(now);

        assertFalse(upd.checkForUpdates(activity, null));
        now += Updater.UPDATE_CHECK_FREQ_MS;
        clock.setCurrentTime(now);

        assertTrue(upd.checkForUpdates(activity, "anything_else"));
        now += Updater.UPDATE_CHECK_FREQ_MS;
        clock.setCurrentTime(now);

        assertEquals("1.3.0", upd.stripBuildHostName("1.3.0.Victors-MBPr"));
        assertEquals("1.3.0", upd.stripBuildHostName("1.3.0"));
    }

    @Test
    public void testUpdaterThrottlesRequests() {
        IHttpUtil mockHttp = new MockHttpUtil();

        Updater upd = new Updater();
        upd = spy(upd);

        // Setup mocks.
        // Replace the HTTP client and clock
        MockSystemClock clock = new MockSystemClock();
        long now = System.currentTimeMillis();
        clock.setCurrentTime(now);
        ServiceLocator.getInstance().putService(IHttpUtil.class, mockHttp);
        ServiceLocator.getInstance().putService(ISystemClock.class, clock);

        // wifi is always unavailable
        doReturn(false).when(upd).wifiExclusiveAndUnavailable(Mockito.any(Context.class));

        assertFalse(upd.checkForUpdates(activity, ""));
        now += Updater.UPDATE_CHECK_FREQ_MS;
        clock.setCurrentTime(now);

        assertFalse(upd.checkForUpdates(activity, null));
        now += Updater.UPDATE_CHECK_FREQ_MS;
        clock.setCurrentTime(now);

        assertTrue(upd.checkForUpdates(activity, "anything_else"));
        now += Updater.UPDATE_CHECK_FREQ_MS;
        clock.setCurrentTime(now);
        assertTrue(upd.checkForUpdates(activity, "anything_else"));

        // This should fail as the clock hasn't been pushed forward
        assertFalse(upd.checkForUpdates(activity, "anything_else"));
    }


    @Test(expected = RuntimeException.class)
    public void testUpdaterThrowsExceptions() {
        Updater upd = new Updater();
        upd = spy(upd);

        // wifi is always unavailable
        doReturn(false).when(upd).wifiExclusiveAndUnavailable(Mockito.any(Context.class));

        upd.stripBuildHostName("1.0");
    }
}
