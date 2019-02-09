/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread.motiondetection;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.SensorManager;
import android.support.v4.content.LocalBroadcastManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mozilla.mozstumbler.svclocator.ServiceConfig;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.log.ILogger;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.robolectric.Robolectric.shadowOf;


@RunWith(RobolectricTestRunner.class)
@Config(emulateSdk = 18,
        shadows = {CustomSensorManager.class})
public class SignificantMotionSensorTest {

    ILogger Log;
    private final static String LOG_TAG = LoggerUtil.makeLogTag(SignificantMotionSensorTest.class);
    Intent captured = null;


    @Before
    public void setUp() {
        ServiceConfig cfg = new ServiceConfig();
        cfg.put(ILogger.class, ServiceConfig.load("org.mozilla.mozstumbler.svclocator.services.log.DebugLogger"));
        ServiceLocator.newRoot(cfg);
        Log = (ILogger) ServiceLocator.getInstance().getService(ILogger.class);
    }

    @Test
    public void testBasicSignificantMotionSensorTest() {
        // This is a basic test of significant motion detection.
        // This code instruments the SensorManager to invoke triggerEvent
        // on a listener which simulates the code path used when Android detects a
        // significant motion has occurred.

        // Register a listener for motion detected so that we can verify that a significant
        // motion will cause the intent to be broadcast.
        LocalBroadcastManager instance = LocalBroadcastManager.getInstance(Robolectric.application);
        final BroadcastReceiver callbackReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                captured = intent;
            }
        };
        instance.registerReceiver(callbackReceiver, new IntentFilter(MotionSensor.ACTION_USER_MOTION_DETECTED));


        // We need to obtain the 'shadow' of the SensorManager so that we can invoke the
        // custom method to trigger an event
        SensorManager sensorManager = (SensorManager) Robolectric.application.getSystemService(Context.SENSOR_SERVICE);
        CustomSensorManager shadow = (CustomSensorManager) shadowOf(sensorManager);

        // Create the MotionSensor
        Context ctx = Robolectric.application;
        SignificantMotionSensor sensor = SignificantMotionSensor.getSensor(ctx);

        assertFalse(sensor.isActive());
        sensor.start();
        Log.d(LOG_TAG, "Started significant sensor");
        assertTrue(sensor.isActive());

        assertNull(captured);
        shadow.triggerEvent();

        // Verify that the significant motion event has triggered the intent to be broadcast
        assertNotNull(captured);
        assertEquals(captured.getAction(), MotionSensor.ACTION_USER_MOTION_DETECTED);

        sensor.stop();
        assertFalse(sensor.isActive());
    }


}

