package org.mozilla.mozstumbler.service.stumblerthread.motiondetection;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.os.Build;
import android.support.v4.content.LocalBroadcastManager;
import android.util.FloatMath;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.core.logging.Log;

public class MotionSensor {

    private static final String LOG_TAG = AppGlobals.makeLogTag(MotionSensor.class);

    private final SensorManager mSensorManager;
    private final String ACTION_USER_MOTION_DETECTED = AppGlobals.ACTION_NAMESPACE + ".USER_MOVE";
    private LegacyMotionSensor mLegacyMotionSensor;
    private Sensor mSignificantMotionSensor;
    private final Context mContext;
    private boolean mStopSignificantMotionSensor;

    public MotionSensor(Context context, BroadcastReceiver callbackReceiver) {
        mContext = context;
        LocalBroadcastManager.getInstance(context).registerReceiver(callbackReceiver,
                new IntentFilter(ACTION_USER_MOTION_DETECTED));

        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (mSensorManager == null) {
            AppGlobals.guiLogInfo("No sensor manager.");
            return;
        }

        mStopSignificantMotionSensor = false;
        if (Build.VERSION.SDK_INT >= 18) {
           mSignificantMotionSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION);
           if (mSignificantMotionSensor != null) {
               AppGlobals.guiLogInfo("Device has significant motion sensor.");
           }
        }

        // If no TYPE_SIGNIFICANT_MOTION is available, use alternate means to sense motion
        if (mSignificantMotionSensor == null) {
            mLegacyMotionSensor = new LegacyMotionSensor();
            AppGlobals.guiLogInfo("Device has legacy motion sensor.");
        }
    }
    public void start() {
        if (mSignificantMotionSensor == null) {
            if (mLegacyMotionSensor != null) {
                mLegacyMotionSensor.start();
            }
            return;
        }

        mStopSignificantMotionSensor = false;
        if (Build.VERSION.SDK_INT >= 18) {
            final TriggerEventListener tr = new TriggerEventListener() {
                @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
                @Override
                public void onTrigger(TriggerEvent event) {
                    if (mStopSignificantMotionSensor) {
                        return;
                    }
                    AppGlobals.guiLogInfo("Major motion detected.");
                    LocalBroadcastManager.getInstance(mContext).sendBroadcastSync(new Intent(ACTION_USER_MOTION_DETECTED));
                    mSensorManager.requestTriggerSensor(this, mSignificantMotionSensor);
                }
            };

            mSensorManager.requestTriggerSensor(tr, mSignificantMotionSensor);
        }
    }

    public void stop() {
        mStopSignificantMotionSensor = true;
        if (mLegacyMotionSensor != null) {
            mLegacyMotionSensor.stop();
        }
    }

    private class LegacyMotionSensor {
        private long mLastTimeThereWasMovementMs;
        private int mMovementCountWithinTimeWindow;

        // A few sensor movements are required within a time window before we say there is user motion.
        private final int TIME_WINDOW_MS = 1000;
        private final int MOVEMENTS_REQUIRED_IN_TIME_WINDOW = 3;

        private final SensorEventListener mSensorEventListener = new SensorEventListener() {
            public void onSensorChanged(SensorEvent event) {
                final float x = event.values[0];
                final float y = event.values[1];
                final float z = event.values[2];
                final float accel = FloatMath.sqrt(x * x + y * y + z * z);

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
                            LocalBroadcastManager.getInstance(mContext).
                                    sendBroadcastSync(new Intent(ACTION_USER_MOTION_DETECTED));
                        }
                    } else {
                        mMovementCountWithinTimeWindow = 0;
                    }
                }
            }

            public void onAccuracyChanged(Sensor sensor, int accuracy) {}
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
}
