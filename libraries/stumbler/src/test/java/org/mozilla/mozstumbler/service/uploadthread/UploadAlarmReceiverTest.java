/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.uploadthread;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mozilla.mozstumbler.service.utils.NetworkInfo;
import org.mozilla.mozstumbler.service.utils.NetworkInfoTestUtil;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.log.DebugLogger;
import org.mozilla.mozstumbler.svclocator.services.log.ILogger;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.robolectric.bytecode.ShadowWrangler.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(emulateSdk = 18)
public class UploadAlarmReceiverTest {

    @Before
    public void setUp() {
        ServiceLocator.getInstance().putService(ILogger.class, new DebugLogger());
    }

    @Test
    public void testNPEBug_1130052_startService() {
        // This is a testcase for bugzilla bug 1130052
        // Verify that passing in NULL as the intent to onReceive to the UploadAlarmReceiver
        // will invoke a well formed startService call with a non-null intent.
        Context ctx = Robolectric.application;
        ctx = spy(ctx);

        UploadAlarmReceiver uar = new UploadAlarmReceiver();
        uar.onReceive(ctx, null);

        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);

        // Verify that the startService call was invoked as expected
        verify(ctx, times(1)).startService(intentCaptor.capture());
        Intent intent = intentCaptor.getValue();

        // Names are tricky to match for inner classes.
        assertTrue(intent.getComponent().getClassName().contains("UploadAlarmService"));
    }


    @Test
    public void testNPEBug_1130052_NoNPEOnAlarmService() {
        // This is a testcase for bugzilla bug 1130052

        // Verify that passing in NULL as the intent to onHandleIntent to the UploadAlarmService
        // will not cause a NPE
        UploadAlarmReceiver.UploadAlarmService uas = new UploadAlarmReceiver.UploadAlarmService();
        uas.onHandleIntent(null);
    }

    @Test
    public void testNPEBug_1130052_ValidIntentOnAlarmService() {
        // This is a testcase for bugzilla bug 1130052


        Context ctx = Robolectric.application;
        NetworkInfo.createGlobalInstance(ctx);

        // Setup NetworkInfo to say that wifi is available.
        NetworkInfo niSpy = spy(NetworkInfo.getInstance());
        doReturn(true).when(niSpy).isWifiAvailable();

        NetworkInfoTestUtil.setNetworkInfo(niSpy);

            // Verify that passing in valid intent to the onHandleIntent to the UploadAlarmService
        // will cause
        UploadAlarmReceiver.UploadAlarmService uas = new UploadAlarmReceiver.UploadAlarmService();

        // Wrap the UAS in a spy so that we can assert method calls
        uas = spy(uas);

        uas.onHandleIntent(new Intent("any_other_intent"));

        // verify that the upload() method was invoked once.
        verify(uas, times(1)).upload(true);
    }


}
