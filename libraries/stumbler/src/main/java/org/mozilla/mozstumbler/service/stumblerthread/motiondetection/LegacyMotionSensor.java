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

import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

public class LegacyMotionSensor implements IMotionSensor {
    private static final String LOG_TAG = LoggerUtil.makeLogTag(LegacyMotionSensor.class);
    // I'm assuming 30 iterations is good enough to get convergence.
    private static int iterations_for_convergence = 30;
    final SensorManager mSensorManager;
    final SensorEventListener mSensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            // This must call a method in the LegacyMotionSensor
            // or else we have no way of instrumenting the class with tests.
            sensorChanged(event.sensor.getType(), event.values);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };
    private final Context mAppContext;
    // A few sensor movements are required within a time window before we say there is user motion.
    private final int TIME_WINDOW_MS = 2000;
    private final int MOVEMENTS_REQUIRED_IN_TIME_WINDOW = 4;
    private final float alpha = (float) 0.8;
    public float computed_gravity = 0;
    private long mLastTimeThereWasMovementMs;
    private int mMovementCountWithinTimeWindow;
    private float[] gravity = {0, 0, 0};
    private float[] linear_acceleration = {0, 0, 0};
    private boolean isActive;

    public LegacyMotionSensor(Context ctx) {
        mAppContext = ctx;
        mSensorManager = (SensorManager) mAppContext.getSystemService(Context.SENSOR_SERVICE);
    }

    /*
     This function is called by the SensorEventListener so that we have something to test.
     Real SensorEvent instances cannot be constructed on a test platform.
     */
    public void sensorChanged(int sensorType, float[] event_values) {
        gravity[0] = alpha * gravity[0] + (1 - alpha) * event_values[0];
        gravity[1] = alpha * gravity[1] + (1 - alpha) * event_values[1];
        gravity[2] = alpha * gravity[2] + (1 - alpha) * event_values[2];

        linear_acceleration[0] = event_values[0] - gravity[0];
        linear_acceleration[1] = event_values[1] - gravity[1];
        linear_acceleration[2] = event_values[2] - gravity[2];

        computed_gravity = FloatMath.sqrt(gravity[0] * gravity[0] +
                gravity[1] * gravity[1] +
                gravity[2] * gravity[2]);

        if (iterations_for_convergence > 0) {
            // We don't use an epsilon to detect convergence because empirically
            // devices don't seem to comply to the 0.05m/s^2 error that Google
            // specifies.  The Motorola XT1032 seems to get ~10.10 m/s^s for gravity
            // when it's tilted to stand on it's edge.  When laid flat,
            // it gets pretty close with 9.89m/s^2 which is still far from 9.81m/s^2
            iterations_for_convergence--;
            return;
        }

        float x = linear_acceleration[0];
        float y = linear_acceleration[1];
        float z = linear_acceleration[2];

        final float accel = FloatMath.sqrt(x * x + y * y + z * z);

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

    @Override
    public void start() {
        mSensorManager.registerListener(mSensorEventListener,
                mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_NORMAL);
        
        isActive = true;
    }

    @Override
    public void stop() {
        mSensorManager.unregisterListener(mSensorEventListener);
        isActive = false;
    }

    @Override
    public boolean isActive() {
        return isActive;
    }
}
