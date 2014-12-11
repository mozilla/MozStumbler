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
import org.mozilla.mozstumbler.service.stumblerthread.scanners.GPSScanner;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.ScanManager;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.ISystemClock;
import org.mozilla.mozstumbler.svclocator.services.MockSystemClock;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.LinkedList;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
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

        // Set the minimum change distance to be very large
        Prefs.getInstance(this).setMotionChangeDistanceMeters(10000);
        //
        // start the sensor
        locationChangeSensor.start();
    }

    @Test
    public void testStartMotionDetectionAfterFirstGPSLock() {
        // Motion detection should start only after the first GPS lock is acquired.

        // Note that this is not the same as having 'start()' called.   This means that
        // we are waiting for mPrefMotionChangeTimeWindowMs for the next GPS signal or else
        // we go to sleep.

        assertFalse(locationChangeSensor.checkTimeScheduled);

        Intent intent = getLocationIntent(0, 0);
        locationChangeSensor.onReceive(ctx, intent);
        assertTrue(locationChangeSensor.checkTimeScheduled);
    }

    @Test
    public void testNotFirstGPSFix_MovedDistance() {
        Intent intent;
        Location expectedPosition;
        // The second GPS fix must have movement > mPrefMotionChangeDistanceMeters
        // for the saved location to be updated.

        // If the second GPS fix is too soon, the accelerometers are registering movement
        // but the person hasn't actually moved geographically.  Keep the scanners on
        // and schedule the next timeout check to see if the user just stops moving around.

        assertFalse(locationChangeSensor.checkTimeScheduled);

        intent = getLocationIntent(20, 30);
        expectedPosition = intent.getParcelableExtra(GPSScanner.NEW_LOCATION_ARG_LOCATION);
        locationChangeSensor.onReceive(ctx, intent);
        assertEquals(expectedPosition, locationChangeSensor.mLastLocation);

        intent = getLocationIntent(21, 30);
        locationChangeSensor.onReceive(ctx, intent);

        // The new recorded position should be 21, 30
        expectedPosition = intent.getParcelableExtra(GPSScanner.NEW_LOCATION_ARG_LOCATION);
        assertEquals(expectedPosition, locationChangeSensor.mLastLocation);
    }

    @Test
    public void testNotFirstGPSFix_LongWaitForSecondFix() {
        // If the second fix that comes in arrives late (> time window for movement pref),
        // then we assume the user isn't actually moving.
        // Send off a ACTION_LOCATION_NOT_CHANGING intent.

        // The callback receiver passed in the constructor to the DetectUnchangingLocationTest
        // should get the ACTION_LOCATION_NOT_CHANGING intent
        Intent intent;
        Location expectedPosition;


        Robolectric.runUiThreadTasksIncludingDelayedTasks();

        assertFalse(locationChangeSensor.checkTimeScheduled);

        intent = getLocationIntent(20, 30);
        expectedPosition = intent.getParcelableExtra(GPSScanner.NEW_LOCATION_ARG_LOCATION);
        locationChangeSensor.onReceive(ctx, intent);
        Robolectric.runUiThreadTasksIncludingDelayedTasks();

        assertEquals(expectedPosition, locationChangeSensor.mLastLocation);

        intent = getLocationIntent(20.01, 30.01);

        // Muck about with the time and move to the end of time
        clock.setCurrentTime(Long.MAX_VALUE);
        locationChangeSensor.onReceive(ctx, intent);
        Robolectric.runUiThreadTasksIncludingDelayedTasks();

        // The new recorded position should not be changed!
        assertEquals(expectedPosition, locationChangeSensor.mLastLocation);

        boolean foundIntent = false;

        for (Intent capturedIntent: receivedIntent) {
            if (capturedIntent.getAction().equals(LocationChangeSensor.ACTION_LOCATION_NOT_CHANGING)) {
                foundIntent = true;
            }
        }
        assertTrue(foundIntent);
    }

}
