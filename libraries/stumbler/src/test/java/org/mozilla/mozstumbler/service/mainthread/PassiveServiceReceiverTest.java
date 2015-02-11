/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.mainthread;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.stumblerthread.StumblerService;
import org.mozilla.mozstumbler.service.stumblerthread.motiondetection.CustomSensorManager;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.log.DebugLogger;
import org.mozilla.mozstumbler.svclocator.services.log.ILogger;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.LinkedList;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(RobolectricTestRunner.class)
@Config(emulateSdk = 18,
        shadows = {CustomSensorManager.class})
public class PassiveServiceReceiverTest {


    private static final String LOG_TAG = LoggerUtil.makeLogTag(PassiveServiceReceiverTest.class);
    private ILogger Log = (ILogger) ServiceLocator.getInstance().getService(ILogger.class);

    private Context appCtx;


    @Before
    public void setUp() {
        appCtx = spy(Robolectric.application);

        // Setup the debug logger
        ServiceLocator.getInstance().putService(ILogger.class, new DebugLogger());

        StumblerService.sFirefoxStumblingEnabled.set(false);
    }

    public void doAndroidTest(Intent intent) {
        PassiveServiceReceiver psr = new PassiveServiceReceiver();
        // Robolectric won't hand off messages using the BroadcastReceiver
        // and LocalBroadcastManager, so just manually call onReceive
        psr.onReceive(appCtx, intent);

        ArgumentCaptor<Intent> actualIntent = ArgumentCaptor.forClass(Intent.class);

        verify(appCtx, times(1)).startService(actualIntent.capture());
        Intent secondIntent = actualIntent.getValue();
        assertEquals(StumblerService.class.getCanonicalName(), secondIntent.getComponent().getClassName());
        assertTrue(secondIntent.getBooleanExtra(StumblerService.ACTION_NOT_FROM_HOST_APP, false));
    }


    @Test
    public void testIntentBootCompleted() {
        // this is an Android initiated intent
        Intent intent = new Intent("android.intent.action.BOOT_COMPLETED");

        doAndroidTest(intent);
    }


    @Test
    public void testIntentGpsEnabledChange() {
        // this is an Android initiated intent
        String action = "android.location.GPS_ENABLED_CHANGE";
        Intent intent = new Intent(action);

        doAndroidTest(intent);
    }

    @Test
    public void testIntentGpsFixChange() {
        // this is an Android initiated intent
        Intent intent = new Intent("android.location.GPS_FIX_CHANGE");
        doAndroidTest(intent);
    }

    @Test
    public void testIntentManualStart() {
        // Do some local setup to capture intents
        final LinkedList<Intent> receivedIntents = new LinkedList<Intent>();
        final BroadcastReceiver callbackReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Just capture the intent for testing
                receivedIntents.add(intent);
            }
        };
        appCtx.registerReceiver(callbackReceiver,
                new IntentFilter(AppGlobals.ACTION_TEST_SETTING_ENABLED));

        receivedIntents.clear();

        /////////////////////////////////////
        // explicitly start the service

        final String MOZ_API_KEY = "a_moz_api_key";
        final String USER_AGENT = "custom_user_agent";

        Intent intent = PassiveServiceReceiver.createStartIntent(MOZ_API_KEY, USER_AGENT);

        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);

        // Pass the intent to start the service.
        PassiveServiceReceiver psr = new PassiveServiceReceiver();
        psr.onReceive(appCtx, intent);

        // check the state of the receiver and stumbler service now.
        assertTrue(StumblerService.sFirefoxStumblingEnabled.get());
        verify(appCtx, times(1)).startService(intentCaptor.capture());
        Intent actualIntent = intentCaptor.getValue();

        // verify that the service was asked to start with
        // passive, moz_api_key and the user agent setup.
        assertTrue(actualIntent.getBooleanExtra(StumblerService.ACTION_START_PASSIVE, false));
        assertEquals(actualIntent.getStringExtra(StumblerService.ACTION_EXTRA_MOZ_API_KEY),
                MOZ_API_KEY);
        assertEquals(actualIntent.getStringExtra(StumblerService.ACTION_EXTRA_USER_AGENT),
                USER_AGENT);

        //Check the class that is being started.
        assertEquals(StumblerService.class.getCanonicalName(),
                actualIntent.getComponent().getClassName());

        // Double check the test setting enabled flag. This is normally used by Fennec.
        Intent capturedIntent = receivedIntents.getFirst();
        assertEquals(capturedIntent.getAction(), AppGlobals.ACTION_TEST_SETTING_ENABLED);
    }

    @Test
    public void testStumblerServiceOnHandleIntent() {

        // Ok, so now we need to see that the intent is actually processed correctly by
        // StumblerService.  robolectric doesn't pass the start intent down to
        // StumblerService::onHandleIntent for us, so we'll just push it down ourselves.
        final String MOZ_API_KEY = "a_moz_api_key";
        final String USER_AGENT = "custom_user_agent";
        Intent intent = PassiveServiceReceiver.createStartIntent(MOZ_API_KEY, USER_AGENT);

        StumblerService ss = new StumblerService();

        // Stub out startScanning as we don't want to actually engage any of GPSScanner, WifiScanner
        ss = spy(ss);
        doNothing().when(ss).startScanning();

        ss.onHandleIntent(intent);

        // Just check that startScanning was invoked.  Assume that the ScanManager has it's own
        // test coverage for that method.
        verify(ss, times(1)).startScanning();
    }

    @Test
    public void testIntentManualStop() {
        // Do some local setup to capture intents
        final LinkedList<Intent> receivedIntents = new LinkedList<Intent>();
        final BroadcastReceiver callbackReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Just capture the intent for testing
                receivedIntents.add(intent);
            }
        };
        appCtx.registerReceiver(callbackReceiver,
                new IntentFilter(AppGlobals.ACTION_TEST_SETTING_DISABLED));

        receivedIntents.clear();
        /////////////////////////////////


        // Check that a stop request stops the service
        Intent intent = PassiveServiceReceiver.createStopIntent();
        PassiveServiceReceiver psr = new PassiveServiceReceiver();
        psr.onReceive(appCtx, intent);

        // Now verify that stopService is actually invoked on the context with StumblerService
        ArgumentCaptor<Intent> actualIntent = ArgumentCaptor.forClass(Intent.class);
        verify(appCtx, times(1)).stopService(actualIntent.capture());
        Intent secondIntent = actualIntent.getValue();
        assertEquals(StumblerService.class.getCanonicalName(), secondIntent.getComponent().getClassName());

        // Double check the test setting disabled flag. This is normally used by Fennec.
        Intent capturedIntent = receivedIntents.getFirst();
        assertEquals(capturedIntent.getAction(), AppGlobals.ACTION_TEST_SETTING_DISABLED);
    }

}
