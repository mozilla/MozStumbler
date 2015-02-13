/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.mozstumbler.service.stumblerthread.motiondetection;

import android.content.Context;
import android.hardware.Sensor;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mozilla.mozstumbler.svclocator.services.MockSystemClock;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
public class LegacyMotionSensorTest {

    MockSystemClock clock = new MockSystemClock();

    @Test
    public void testConvergence() {

        Context ctx = Robolectric.application;

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

    double mLat, mLon;

    public Location getNextGPSLocation() {
        Location mockLocation = new Location(LocationManager.GPS_PROVIDER); // a string
        mockLocation.setLatitude(mLat);
        mockLocation.setLongitude(mLon);
        mockLocation.setAltitude(42.0);  // meters above sea level
        mockLocation.setAccuracy(5);
        mockLocation.setTime(clock.currentTimeMillis());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            mockLocation.setElapsedRealtimeNanos(clock.currentTimeMillis() * 1000000);
        }

        // This is rougly ~5m-ish.
        mLat += 0.00003;
        mLon += 0.00003;

        return mockLocation;
    }

    @Test
    public void testFalsePositiveFilterInitialization() {
        MotionSensor motionSensor = new MotionSensor(Robolectric.application);
        clock.setCurrentTime(1000);
        assertTrue(motionSensor.mFalsePositiveFilter.mTimeStartedMs == 0);
        motionSensor.start();
        motionSensor.mFalsePositiveFilter.mLastLocation = getNextGPSLocation();
        assertTrue(motionSensor.mFalsePositiveFilter.mTimeStartedMs > 0);
        motionSensor.stop();
        assertTrue(motionSensor.mFalsePositiveFilter.mTimeStartedMs > 0);
        assertTrue(motionSensor.mFalsePositiveFilter.mLastLocation != null);
        motionSensor.scannerFullyStopped();
        assertTrue(motionSensor.mFalsePositiveFilter.mTimeStartedMs == 0);
        assertTrue(motionSensor.mFalsePositiveFilter.mLastLocation == null);
    }

    @Test
    public void testFalsePositiveFilterTriggers() {
        MotionSensor motionSensor = new MotionSensor(Robolectric.application);
        clock.setCurrentTime(1000);
        motionSensor.start();
        motionSensor.mFalsePositiveFilter.mLastLocation = getNextGPSLocation();
        motionSensor.mFalsePositiveFilter.mListener.onLocationChanged(getNextGPSLocation());
        assertTrue(motionSensor.mFalsePositiveFilter.mLastLocation != null);
        mLat += 0.01; // a big change in location
        motionSensor.mFalsePositiveFilter.mListener.onLocationChanged(getNextGPSLocation());
        assertTrue(motionSensor.mFalsePositiveFilter.mLastLocation == null);
    }


}
