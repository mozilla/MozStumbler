/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.navdrawer;

import android.content.ComponentName;
import android.content.Context;
import android.widget.RemoteViews;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.ClientStumblerService;
import org.mozilla.mozstumbler.service.core.logging.Log;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.AppWidgetManagerProxy;
import org.mozilla.mozstumbler.svclocator.services.IAppWidgetManagerProxy;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ActivityController;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;


@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
public class MainDrawerActivityTest {

    @Before
    public void setup() {
        while (!Log.messageBuffer.isEmpty()) {
            Log.messageBuffer.popLast();
        }
    }


    @Test
    public void testUpdateWidget() {
        // Sometimes, the widget will die because AppWidgetManager::updateAppWidget will throw
        // a runtime-exception.  We don't want the whole application to die if this is the case.
        // Just stop updating the widget.

        ActivityController<MainDrawerActivity> controller = Robolectric.buildActivity(MainDrawerActivity.class);
        MainDrawerActivity activity = controller.get();

        ClientStumblerService clientStumberSvc = mock(ClientStumblerService.class);

        // Mock out the AppWidgetManager

        AppWidgetManagerProxy myMockProxy = spy(new AppWidgetManagerProxy());

        doThrow(new RuntimeException()).when(myMockProxy).updateAppWidget(any(Context.class),
                any(ComponentName.class),
                any(RemoteViews.class));


        activity.remoteViews =  new RemoteViews(Robolectric.application.getPackageName(),
                R.layout.toggle_widget);

        ServiceLocator.getInstance().putService(IAppWidgetManagerProxy.class, myMockProxy);
        activity.updateWidget(clientStumberSvc);

        // Check that a runtime exception was logged
        assertEquals(1, Log.messageBuffer.size());

        String msg = Log.messageBuffer.getLast();
        assertTrue(msg.contains("Error with updating widget"));
    }

}
