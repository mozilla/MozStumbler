/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.mozstumbler.service.stumblerthread.motiondetection;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSensorManager;

import static junit.framework.Assert.assertNotNull;
import static org.robolectric.Robolectric.shadowOf;

@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
public class LegacyMotionSensorTest {

    @Test
    public void testConvergence() {

        Context ctx = Robolectric.application;

        // Setup a ShadowSensorManager
        SensorManager sensorManager = (SensorManager)
                Robolectric.application.getSystemService(Context.SENSOR_SERVICE);
        ShadowSensorManager shadowSensorManager = shadowOf(sensorManager);

        LegacyMotionSensor lms = new LegacyMotionSensor(ctx);
        assertNotNull(lms.mSensorManager);

        // Register against the SENSOR_SERVICE
        lms.start();

        float[][] values = {{-0.2353596f, 0.1569064f, 9.963556f},
                {-0.19613299f, 0.0784532f, 9.963556f},
                {-0.2745862f, 0.19613299f, 10.002783f},
                {-0.2353596f, 0.19613299f, 9.92433f},
                {-0.3138128f, 0.19613299f, 9.845877f},
                {-0.3138128f, 0.1176798f, 9.885103f},
                {-0.2745862f, 0.1176798f, 9.963556f},
                {-0.3138128f, 0.1569064f, 10.002783f},
                {-0.2745862f, 0.0784532f, 9.885103f},
                {-0.19613299f, 0.1176798f, 9.92433f},
                {-0.2353596f, 0.1176798f, 9.92433f},
                {-0.19613299f, 0.2353596f, 10.002783f},
                {-0.19613299f, 0.2745862f, 9.963556f},
                {-0.39226598f, 0.3138128f, 10.042009f},
                {-0.35303938f, 0.1569064f, 9.963556f},
                {-0.39226598f, 0.1569064f, 9.92433f},
                {-0.39226598f, 0.1569064f, 10.042009f},
                {-0.2745862f, 0.2745862f, 9.963556f},
                {-0.2353596f, 0.1176798f, 9.92433f},
                {-0.3138128f, 0.1569064f, 9.963556f},
                {-0.3138128f, 0.19613299f, 9.963556f},
                {-0.2745862f, 0.1569064f, 10.002783f},
                {-0.3138128f, 0.1569064f, 9.845877f},
                {-0.2745862f, 0.0784532f, 9.963556f},
                {-0.35303938f, -0.0392266f, 9.963556f},
                {-0.2745862f, 0.0392266f, 9.963556f},
                {-0.39226598f, 0.0392266f, 9.885103f},
                {-0.1569064f, 0.1176798f, 9.963556f},
                {-0.3138128f, 0.0392266f, 9.963556f},
                {-0.3138128f, 0.0784532f, 10.002783f}};


        // Push events through now.
        for (float[] row : values) {
            lms.sensorChanged(Sensor.TYPE_ACCELEROMETER, row);
        }

        // This is close enough.  Android sensors have a lot of noise.
        assert (Math.abs(lms.computed_gravity - 9.81) < 0.25);
    }
}
