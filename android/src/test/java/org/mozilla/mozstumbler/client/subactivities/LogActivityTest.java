package org.mozilla.mozstumbler.client.subactivities;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Point;
import android.os.Build;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.core.logging.Log;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowTextView;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertThat;
import static org.mozilla.mozstumbler.client.subactivities.LogActivity.ConsoleView;
import static org.robolectric.Robolectric.shadowOf;

@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
public class LogActivityTest {

    private static final String LOG_TAG = AppGlobals.makeLogTag(LogActivityTest.class);

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Test
    @Config(shadows = {ShadowTextView.class})
    public void testLogActivityView() {
        Context ctx = Robolectric.application;
        ConsoleView consoleView = new ConsoleView(ctx);

        // Force the layout to be 100x400 pixels
        // You need to set both the console view and the inner textview
        // layout sizes.
        consoleView.layout(0, 0, 100, 400);
        consoleView.tv.layout(0, 0, 100, 400);
        assertTrue(consoleView.enable_scroll);

        // Just check that we can properly scroll the Y-axis
        consoleView.setScrollY(0);
        assertEquals(0, consoleView.getScrollY());
        consoleView.setScrollY(50);
        assertEquals(50, consoleView.getScrollY());

        // Scrolled to Y=0, the scrolling should be enabled.
        consoleView.setScrollY(0);
        assertEquals(0, consoleView.getScrollY());
        assertTrue(consoleView.enable_scroll);
        consoleView.print("This is a test");
        assertEquals(0, consoleView.getScrollY());

        // Scrolled to Y=-50, the scrolling should be disabled.
        consoleView.setScrollY(-50);
        assertEquals(-50, consoleView.getScrollY());
        assertFalse(consoleView.enable_scroll);
        consoleView.print("This is a test");
        assertEquals(-50, consoleView.getScrollY());
    }
}
