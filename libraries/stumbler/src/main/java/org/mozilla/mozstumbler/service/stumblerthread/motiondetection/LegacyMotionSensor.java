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
    private boolean isActive;

    public LegacyMotionSensor(Context ctx) {
        mAppContext = ctx;
        mSensorManager = (SensorManager) mAppContext.getSystemService(Context.SENSOR_SERVICE);
    }

    // Each time the baseline average changes by greater than the threshold,
    // indicate this (see isOverThreshold) and update the baseline average to the current rolling average.
    // So, if the threshold is 1, and the current baseline average is 10,
    // then we need the rolling average to to reach 9 (or 11) for isOverThreshold to return true and
    // then the baseline average is set the rolling average.
    // This 'baseline average' method ensures that changes of acceleration can be detected over any time period.
    class RollingAverage {
        float[] mSamples;
        final float mThreshold;
        int mIdx;
        float mTotal;
        float mBaselineAverage;
        boolean mIsInitialized;

        public RollingAverage(int sampleCount, float changeThreshold) {
            mSamples = new float[sampleCount];
            mThreshold = changeThreshold;
        }

        public boolean isOverThreshold(float val) {
            mTotal -= mSamples[mIdx];
            mTotal += val;
            mSamples[mIdx] = val;
            if (++mIdx == mSamples.length) {
                mIdx = 0;
                float average = average();
                if (!mIsInitialized) {
                    mIsInitialized = true;
                    mBaselineAverage = average;
                } else if (Math.abs(average - mBaselineAverage) > mThreshold) {
                    mBaselineAverage = average;
                    return true;
                }
            }
            return false;
        }

        float average() {
            return mTotal / mSamples.length;
        }

        void clear() {
            mSamples = new float[mSamples.length];
            mIdx = 0;
            mIsInitialized = false;
        }
    }

    final RollingAverage mRollingAverage = new RollingAverage(10, 0.5f);

    /*
     This function is called by the SensorEventListener so that we have something to test.
     Real SensorEvent instances cannot be constructed on a test platform.
     */
    public void sensorChanged(int sensorType, float[] event_values) {
         if (mRollingAverage.isOverThreshold(event_values[0] + event_values[1] + event_values[2])) {
            LocalBroadcastManager.getInstance(mAppContext).
                  sendBroadcastSync(new Intent(MotionSensor.ACTION_USER_MOTION_DETECTED));
             mRollingAverage.clear();
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
