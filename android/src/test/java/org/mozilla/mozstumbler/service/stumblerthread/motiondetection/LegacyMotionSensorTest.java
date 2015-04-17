/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.mozstumbler.service.stumblerthread.motiondetection;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.ISystemClock;
import org.mozilla.mozstumbler.svclocator.services.MockSystemClock;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static junit.framework.Assert.assertTrue;
import static org.robolectric.Robolectric.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(emulateSdk = 18,
        shadows = {CustomLocationManager.class})
public class LegacyMotionSensorTest {

    MockSystemClock clock;
    final long ONE_SEC_MS = 1000;

    @Before
    public void setup() {
        clock = new MockSystemClock();
        ServiceLocator.getInstance().putService(ISystemClock.class, clock);

        // We need to obtain the 'shadow' of the SensorManager so that we can invoke the
        // custom method to trigger an event
        LocationManager lm = (LocationManager) Robolectric.application.getSystemService(Context.LOCATION_SERVICE);
        CustomLocationManager shadow = (CustomLocationManager) shadowOf(lm);
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
        assertTrue(motionSensor.mFalsePositiveFilter.mLastLocation == null);
        motionSensor.start();
        motionSensor.mFalsePositiveFilter.mLastLocation = getNextGPSLocation();
        motionSensor.stop();
        assertTrue(motionSensor.mFalsePositiveFilter.mLastLocation != null);
        motionSensor.scannerFullyStopped();
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

    private long howLongUntilMotionSensorRun(long motionDetectDefaultPauseTimeMs, MotionSensor motionSensor) {
        long i = ONE_SEC_MS;
        for (; i < motionDetectDefaultPauseTimeMs + ONE_SEC_MS; i += ONE_SEC_MS) {
            Robolectric.pauseMainLooper();
            Robolectric.idleMainLooper(ONE_SEC_MS);
            if (!motionSensor.mIsStartMotionSensorRunnableScheduled) {
                break;
            }
        }
        return i;
    }

    @Test
    public void testIncrementalDelayStartingMotionSensor() {
        MotionSensor motionSensor = new MotionSensor(Robolectric.application);
        clock.setCurrentTime(ONE_SEC_MS);
        motionSensor.start();
        assertTrue(motionSensor.mIsStartMotionSensorRunnableScheduled);
        Robolectric.runUiThreadTasksIncludingDelayedTasks();
        assertTrue(!motionSensor.mIsStartMotionSensorRunnableScheduled);
        motionSensor.stop();

        final long delayMs = Prefs.getInstance(Robolectric.application).getMotionDetectionMinPauseTime() * ONE_SEC_MS;
        motionSensor.mFalsePositiveFilter.mLastLocation = getNextGPSLocation();
        motionSensor.mFalsePositiveFilter.mLastLocation.setTime(ONE_SEC_MS);

        clock.setCurrentTime(motionSensor.mFalsePositiveFilter.mLastLocation.getTime() + 60 * ONE_SEC_MS);
        motionSensor.start();
        assertTrue(motionSensor.mIsStartMotionSensorRunnableScheduled);
        long howLong = howLongUntilMotionSensorRun(delayMs, motionSensor);
        // check that it is within 1 sec of expected
        assertTrue(Math.abs(delayMs / 5 - howLong) < ONE_SEC_MS);

        motionSensor.stop();
        clock.setCurrentTime(motionSensor.mFalsePositiveFilter.mLastLocation.getTime() + 60 * ONE_SEC_MS * 4);
        motionSensor.start();
        assertTrue(motionSensor.mIsStartMotionSensorRunnableScheduled);
        howLong = howLongUntilMotionSensorRun(delayMs, motionSensor);
        assertTrue(Math.abs(delayMs * 4/5 - howLong) < ONE_SEC_MS);

        motionSensor.stop();
        clock.setCurrentTime(motionSensor.mFalsePositiveFilter.mLastLocation.getTime() + 60 * ONE_SEC_MS * 999);
        motionSensor.start();
        assertTrue(motionSensor.mIsStartMotionSensorRunnableScheduled);
        howLong = howLongUntilMotionSensorRun(delayMs, motionSensor);
        assertTrue(Math.abs(delayMs - howLong) < ONE_SEC_MS);
    }
}
