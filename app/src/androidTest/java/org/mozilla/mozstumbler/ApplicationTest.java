package org.mozilla.mozstumbler;

import android.app.Application;
import android.test.ApplicationTestCase;

import org.mozilla.mozstumbler.client.Updater;

/**
 * <a href="http://d.android.com/tools/testing/testing_android.html">Testing Fundamentals</a>
 */
public class ApplicationTest extends ApplicationTestCase<Application> {
    public ApplicationTest() {
        super(Application.class);
    }

    public void testUpdater() {
        // TODO: add a test against the Updater code here
        assertTrue(true);
    }

}