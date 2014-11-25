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

public class MotionSensor {

    private static final String LOG_TAG = AppGlobals.makeLogTag(MotionSensor.class);

    private final SensorManager mSensorManager;
    public static final String ACTION_USER_MOTION_DETECTED = AppGlobals.ACTION_NAMESPACE + ".USER_MOVE";
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
            mLegacyMotionSensor = new LegacyMotionSensor(mContext.getApplicationContext());
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

}
