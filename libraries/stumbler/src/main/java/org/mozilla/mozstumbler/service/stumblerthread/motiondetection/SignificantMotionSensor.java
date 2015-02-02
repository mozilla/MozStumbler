/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread.motiondetection;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.os.Build;
import android.support.v4.content.LocalBroadcastManager;

import org.acra.ACRA;
import org.mozilla.mozstumbler.service.AppGlobals;

public class SignificantMotionSensor implements IMotionSensor {

    private final Context mAppContext;
    private Sensor mSignificantMotionSensor;
    private boolean mStopSignificantMotionSensor;
    static SensorManager mSensorManager;

    public static SignificantMotionSensor getSensor(Context appCtx) {

        mSensorManager = (SensorManager) appCtx.getSystemService(Context.SENSOR_SERVICE);
        Sensor significantSensor;
        if (mSensorManager == null) {
            AppGlobals.guiLogInfo("No sensor manager.");
            return null;
        }

        if (Build.VERSION.SDK_INT >= 18) {
            significantSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION);
            if (significantSensor != null) {
                AppGlobals.guiLogInfo("Device has significant motion sensor.");
                return new SignificantMotionSensor(appCtx, significantSensor);
            }
        }
        return null;
    }
    private SignificantMotionSensor(Context appCtx, Sensor significantSensor) {
        mStopSignificantMotionSensor = false;
        mSignificantMotionSensor = significantSensor;
        mAppContext = appCtx;
    }

    @Override
    public void start() {
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


                    LocalBroadcastManager localBroadcastManager = null;

                    if (mAppContext == null) {
                        NullPointerException npe = new NullPointerException("mAppContext == null");
                        ACRA.getErrorReporter().handleException(npe);
                        return;
                    }
                    try {
                        localBroadcastManager = LocalBroadcastManager.getInstance(mAppContext);
                    } catch (NullPointerException npe) {
                        String ctxName = "NULL";
                        if (mAppContext != null ) {
                            ctxName = mAppContext.toString();
                        }
                        ACRA.getErrorReporter().putCustomData("mAppContext", ctxName);

                        // Send the report (Stack trace will say "Requested by Developer"
                        // That should set apart manual reports from actual crashes
                        ACRA.getErrorReporter().handleException(npe);
                        return;
                    }

                    if (localBroadcastManager == null) {
                        NullPointerException npe = new NullPointerException("localBroadcastManager == null");
                        // Send the report (Stack trace will say "Requested by Developer"
                        // That should set apart manual reports from actual crashes
                        ACRA.getErrorReporter().handleException(npe);
                        return;
                    }

                    localBroadcastManager.sendBroadcastSync(new Intent(MotionSensor.ACTION_USER_MOTION_DETECTED));


                    mSensorManager.requestTriggerSensor(this, mSignificantMotionSensor);
                }
            };

            mSensorManager.requestTriggerSensor(tr, mSignificantMotionSensor);
        }
    }

    @Override
    public void stop() {
        mStopSignificantMotionSensor = true;
    }

    @Override
    public boolean isActive() {
        return !mStopSignificantMotionSensor;
    }
}
