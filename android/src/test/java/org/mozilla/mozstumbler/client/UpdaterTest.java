/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.mozstumbler.client;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mozilla.mozstumbler.client.navdrawer.MainDrawerActivity;
import org.mozilla.mozstumbler.service.core.http.IHttpUtil;
import org.mozilla.mozstumbler.service.core.http.MockHttpUtil;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
public class UpdaterTest {
    class TestUpdater extends Updater {
        public TestUpdater(IHttpUtil simpleHttp) {
            super(simpleHttp);
        }

        @Override
        public boolean wifiExclusiveAndUnavailable() {
            return false;
        }
    }

    private MainDrawerActivity activity;

    @Before
    public void setUp() throws Exception {
        activity = Robolectric.newInstanceOf(MainDrawerActivity.class);
    }

    @Test
    public void activityShouldNotBeNull() {
        assertNotNull(activity);
    }

    @Test
    public void testUpdater() {
        IHttpUtil mockHttp = new MockHttpUtil();
        Updater upd = new TestUpdater(mockHttp);
        assertFalse(upd.checkForUpdates(activity, ""));
        assertFalse(upd.checkForUpdates(activity, null));
        assertTrue(upd.checkForUpdates(activity, "anything_else"));

        assertEquals("1.3.0", upd.stripBuildHostName("1.3.0.Victors-MBPr"));
        assertEquals("1.3.0", upd.stripBuildHostName("1.3.0"));

    }

    @Test(expected=RuntimeException.class)
    public void testUpdaterThrowsExceptions() {
        Updater upd = new TestUpdater(null);
        upd.stripBuildHostName("1.0");
    }

}
