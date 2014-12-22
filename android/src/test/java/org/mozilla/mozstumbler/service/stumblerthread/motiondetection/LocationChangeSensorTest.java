/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.mozstumbler.service.stumblerthread.motiondetection;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.support.v4.content.LocalBroadcastManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.core.logging.Log;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.GPSScanner;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.ISystemClock;
import org.mozilla.mozstumbler.svclocator.services.MockSystemClock;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.LinkedList;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotSame;
import static junit.framework.Assert.assertSame;
import static junit.framework.Assert.assertTrue;
import static org.mozilla.mozstumbler.service.stumblerthread.ReporterTest.getLocationIntent;

@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
public class LocationChangeSensorTest {

    private static final String LOG_TAG = AppGlobals.makeLogTag(LocationChangeSensorTest.class);
    private LocationChangeSensor locationChangeSensor;
    private LinkedList<Intent> receivedIntent = new LinkedList<Intent>();

    // After DetectUnchangingLocation reports the user is not moving, and the scanning pauses,
    // then use MotionSensor to determine when to wake up and start scanning again.
    private final BroadcastReceiver callbackReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Just capture the intent for testing
            receivedIntent.add(intent);
        }
    };

    private Context ctx;
    private MockSystemClock clock;

    @Before
    public void setup() {
        ctx = Robolectric.application;

        ServiceLocator rootLocator = new ServiceLocator(null);

        clock = new MockSystemClock();
        rootLocator.putService(ISystemClock.class, clock);
        ServiceLocator.setRootInstance(rootLocator);
        clock.setCurrentTime(0);
        receivedIntent.clear();

        // Register a listener for each of the intents that are used by motion detection

        // TODO: move the intents into a single location for the package so that we know where to
        // find them all.
        LocalBroadcastManager.getInstance(ctx).registerReceiver(callbackReceiver,
                new IntentFilter(MotionSensor.ACTION_USER_MOTION_DETECTED));
        LocalBroadcastManager.getInstance(ctx).registerReceiver(callbackReceiver,
                new IntentFilter(LocationChangeSensor.ACTION_LOCATION_NOT_CHANGING));

        locationChangeSensor = new LocationChangeSensor(
                ctx,
                callbackReceiver);
    }

    private Location setPosition(double x, double y) {
        Intent intent = getLocationIntent(x, y);
        Location loc = intent.getParcelableExtra(GPSScanner.NEW_LOCATION_ARG_LOCATION);
        locationChangeSensor.onReceive(ctx, intent);
        return loc;
    }

    @Test
    public void testStartMotionDetectionAfterFirstGPSLock() {
        Prefs.getInstance(ctx).setMotionChangeDistanceMeters(10000);
        locationChangeSensor.start();

        // Motion detection should start only after the first GPS lock is acquired.

        // Note that this is not the same as having 'start()' called.   This means that
        // we are waiting for mPrefMotionChangeTimeWindowMs for the next GPS signal or else
        // we go to sleep.

        locationChangeSensor.removeTimeoutCheck();

        setPosition(0,0);
        assertTrue(locationChangeSensor.removeTimeoutCheck());
    }

    @Test
    public void testNotFirstGPSFix_MovedDistance() {
        Prefs.getInstance(ctx).setMotionChangeDistanceMeters(10000);
        locationChangeSensor.start();
        Location expectedPosition;

        // The second GPS fix must have movement > mPrefMotionChangeDistanceMeters
        // for the saved location to be updated.

        // If the second GPS fix is too soon, the accelerometers are registering movement
        // but the person hasn't actually moved geographically.  Keep the scanners on
        // and schedule the next timeout check to see if the user just stops moving around.

        expectedPosition = setPosition(20, 30);
        assertEquals(expectedPosition, locationChangeSensor.testing_getLastLocation());

        expectedPosition = setPosition(21, 30);
        // The new recorded position should be 21, 30
        assertEquals(expectedPosition, locationChangeSensor.testing_getLastLocation());

        setPosition(21.000001, 30);
        // The new recorded position should be unchanged, movement too small
        assertEquals(expectedPosition, locationChangeSensor.testing_getLastLocation());
    }

    @Test
    public void testLongWaitForSecondFix() {
        boolean isBigMovement = true;
        doLongWaitForSecondFix_MovementDistance(!isBigMovement);
        doLongWaitForSecondFix_MovementDistance(isBigMovement);
    }

    private void doLongWaitForSecondFix_MovementDistance(boolean isBigMovement) {
        Prefs.getInstance(ctx).setMotionChangeDistanceMeters(10000); // 10 km
        locationChangeSensor.start();
        Location expectedPosition;
        Robolectric.runUiThreadTasksIncludingDelayedTasks();

        expectedPosition = setPosition(20, 30);
        Robolectric.runUiThreadTasksIncludingDelayedTasks();
        assertEquals(expectedPosition, locationChangeSensor.testing_getLastLocation());

        final double movementDistance = isBigMovement? 1.0 : 0.001;
        Intent intent = getLocationIntent(20 + movementDistance, 30);
        // Muck about with the time and move to the end of time
        clock.setCurrentTime(Long.MAX_VALUE);
        locationChangeSensor.onReceive(ctx, intent);

        Robolectric.runUiThreadTasksIncludingDelayedTasks();

        if (isBigMovement) {
            assertNotSame(expectedPosition, locationChangeSensor.testing_getLastLocation());
            assertTrue(receivedIntent.size() == 0);
        } else {
            // The new recorded position should not be changed!
            assertSame(expectedPosition, locationChangeSensor.testing_getLastLocation());
            assertIsPaused();
        }
    }

    private void fakeWait(long t) {
        Robolectric.runUiThreadTasksIncludingDelayedTasks();
        clock.setCurrentTime(clock.currentTimeMillis() + t);
        Log.d(LOG_TAG, "- time is (ms):" + clock.currentTimeMillis());
    }

    private void assertIsPaused() {
        boolean foundIntent = false;

        for (Intent capturedIntent : receivedIntent) {
            if (capturedIntent.getAction().equals(LocationChangeSensor.ACTION_LOCATION_NOT_CHANGING)) {
                foundIntent = true;
            }
        }
        assertTrue(foundIntent);
        receivedIntent.clear();
    }

    @Test
    public void testSmallMovementThenPauseThenBigMovement() {
        final long tick = 1000;
        Prefs.getInstance(ctx).setMotionChangeDistanceMeters(50);
        locationChangeSensor.start();
        locationChangeSensor.testing_setTimeoutCheckTime(tick);
        locationChangeSensor.removeTimeoutCheck();
        receivedIntent.clear();

        double x = 20, y = 30;
        Location loc1 = setPosition(x, y);

        fakeWait(tick);

        // Movement below threshold, and within time window
        clock.setCurrentTime(clock.currentTimeMillis() + 50);
        Location loc2 = setPosition(x + 0.0002, y);
        assertTrue(loc1.distanceTo(loc2) < 50 /* meters*/);
        // Insufficient movement in 1sec window
        assertIsPaused();

        fakeWait(tick);

        // no further notification should happen while paused
        assertTrue(receivedIntent.size() == 0);

        Log.d(LOG_TAG, "Movement that exceeds distance threshold while in paused state.");
        locationChangeSensor.quickCheckForFalsePositiveAfterMotionSensorMovement();
        setPosition(x + 0.1, y);

        fakeWait(tick/2);

        // not enough time has passed to pause
        assertTrue(receivedIntent.size() == 0);

        fakeWait(tick);
        fakeWait(tick);
        assertIsPaused();
    }
}
