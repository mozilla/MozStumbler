package org.mozilla.mozstumbler.client.tests;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.test.ApplicationTestCase;

import org.mozilla.mozstumbler.client.IHttpUtil;
import org.mozilla.mozstumbler.client.MainActivity;
import org.mozilla.mozstumbler.client.Updater;

/**
 * <a href="http://d.android.com/tools/testing/testing_android.html">Testing Fundamentals</a>
 */
public class UpdaterTest extends ApplicationTestCase<Application> {
    public UpdaterTest() {
        super(Application.class);
    }

    public void testNoUpdatesWithoutAPIKey() {
        Activity mainActivity = new MainActivity();
        IHttpUtil httpUtil = new MockHttpUtil("abc123");
        Updater updater = new Updater(httpUtil);
        Context ctx = new MockUpdaterContext();

        assertEquals(false, updater.checkForUpdates(mainActivity, null));
        assertEquals(false, updater.checkForUpdates(mainActivity, ""));

        assertEquals(true, updater.checkForUpdates(mainActivity, "anything_could_go_here"));

    }


}