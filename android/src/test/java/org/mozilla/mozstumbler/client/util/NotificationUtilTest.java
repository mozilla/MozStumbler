/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.util;


import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
public class NotificationUtilTest {

    @Test
    public void testNotificationUpdateNPESafe() {
        // NoticationUtil should not die if the NotificationManager comes back as null

        Context ctx = Robolectric.application;
        ctx = spy(ctx);
        doReturn(null).when(ctx).getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationUtil nu = new NotificationUtil(ctx);
        nu = spy(nu);

        final long ARBITRARY_TIME = 555;
        nu.updateLastUploadedLabel(ARBITRARY_TIME);
        verify(ctx, times(1)).getSystemService(Context.NOTIFICATION_SERVICE);
        verify(nu, times(1)).update();
    }


}
