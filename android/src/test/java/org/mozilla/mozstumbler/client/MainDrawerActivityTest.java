package org.mozilla.mozstumbler.client;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mozilla.mozstumbler.client.http.IHttpUtil;
import org.mozilla.mozstumbler.client.http.MockHttpUtil;
import org.mozilla.mozstumbler.client.navdrawer.MainDrawerActivity;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
public class MainDrawerActivityTest {

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

        class TestUpdater extends Updater {
            public TestUpdater(IHttpUtil simpleHttp) {
                super(simpleHttp);
            }

            @Override
            public boolean wifiExclusiveAndUnavailable() {
                return false;
            }
        }

        IHttpUtil mockHttp = new MockHttpUtil();


        Updater upd = new TestUpdater(mockHttp);
        assertFalse(upd.checkForUpdates(activity, ""));
        assertFalse(upd.checkForUpdates(activity, null));
        assertTrue(upd.checkForUpdates(activity, "anything_else"));
    }

}
