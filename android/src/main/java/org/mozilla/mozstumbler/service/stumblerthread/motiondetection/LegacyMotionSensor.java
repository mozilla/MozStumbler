/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.mozstumbler.service.stumblerthread.motiondetection;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.FloatMath;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.core.logging.Log;

public class LegacyMotionSensor {
    private static final String LOG_TAG = AppGlobals.makeLogTag(LegacyMotionSensor.class);
    private final Context mAppContext;
    private final SensorManager mSensorManager;
    private long mLastTimeThereWasMovementMs;
    private int mMovementCountWithinTimeWindow;

    // A few sensor movements are required within a time window before we say there is user motion.
    private final int TIME_WINDOW_MS = 1000;
    private final int MOVEMENTS_REQUIRED_IN_TIME_WINDOW = 3;

    private float[] gravity = {0, 0, 0};
    private float[] linear_acceleration = {0, 0, 0};

    // I'm assuming 30 iterations is good enough to get convergence.
    private static int iterations_for_convergence = 30;
    final float alpha = (float) 0.8;
    private static final float converged_accel_epsilon = (float) 0.3;

    public LegacyMotionSensor(Context ctx)
    {
        mAppContext = ctx;
        mSensorManager = (SensorManager) mAppContext.getSystemService(Context.SENSOR_SERVICE);
    }

    private float computed_gravity = 0;

    private final SensorEventListener mSensorEventListener = new SensorEventListener() {
        public void onSensorChanged(SensorEvent event) {

            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
                gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
                gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];

                linear_acceleration[0] = event.values[0] - gravity[0];
                linear_acceleration[1] = event.values[1] - gravity[1];
                linear_acceleration[2] = event.values[2] - gravity[2];

                computed_gravity = FloatMath.sqrt(gravity[0] * gravity[0] +
                        gravity[1]* gravity[1] +
                        gravity[2]*gravity[2]);

                Log.d(LOG_TAG, "Gravity is : " + computed_gravity);
                if (iterations_for_convergence > 0) {
                    iterations_for_convergence --;
                    Log.d(LOG_TAG, "Raw event values:  "+event.values[0] +", " + event.values[1] + ", " + event.values[2]);
                    return;
                }
            } else {
                linear_acceleration[0] = event.values[0];
                linear_acceleration[1] = event.values[1];
                linear_acceleration[2] = event.values[2];
            }

            float x = linear_acceleration[0];
            float y = linear_acceleration[1];
            float z = linear_acceleration[2];

            final float accel = FloatMath.sqrt(x * x + y * y + z * z);
            Log.d(LOG_TAG, "Got acceleration of " + accel);

            // I found this to be a very low threshold, slight movements of the device are greater than 1.0.
            // False positives are fine, what we don't want is devices that can't be woken up easily.
            final float arbitraryThresholdForMovement = 1.0f;

            if (accel > arbitraryThresholdForMovement) {
                final long prevTime = mLastTimeThereWasMovementMs;
                mLastTimeThereWasMovementMs = System.currentTimeMillis();

                if (mLastTimeThereWasMovementMs - prevTime < TIME_WINDOW_MS) {
                    mMovementCountWithinTimeWindow++;
                    if (mMovementCountWithinTimeWindow >= MOVEMENTS_REQUIRED_IN_TIME_WINDOW) {
                        mMovementCountWithinTimeWindow = 0;
                        LocalBroadcastManager.getInstance(mAppContext).
                                sendBroadcastSync(new Intent(MotionSensor.ACTION_USER_MOTION_DETECTED));
                    }
                } else {
                    mMovementCountWithinTimeWindow = 0;
                }
            }
        }

        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    void start() {
        mSensorManager.registerListener(mSensorEventListener,
                mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION),
                SensorManager.SENSOR_DELAY_NORMAL);

        // Some devices are really terrible. The Moto G XT1032 should respond to the
        // TYPE_LINEAR_ACCELERATION, but will only respond to the TYPE_ACCELEROMETER (API 3).
        // Luckily, both event types emit the same struct.  Who knows why there are two
        // different constants.
        mSensorManager.registerListener(mSensorEventListener,
                mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_NORMAL);
    }

    void stop() {
        mSensorManager.unregisterListener(mSensorEventListener);
    }
}
