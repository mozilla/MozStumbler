/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread.motiondetection;

import android.annotation.TargetApi;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.TriggerEventListener;
import android.os.Build;

import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.log.ILogger;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowSensorManager;

import static org.mockito.Mockito.mock;

@Implements(SensorManager.class)
public class CustomSensorManager extends ShadowSensorManager {

    ILogger Log = (ILogger) ServiceLocator.getInstance().getService(ILogger.class);
    private final static String LOG_TAG = LoggerUtil.makeLogTag(CustomSensorManager.class);

    private TriggerEventListener capturedListener;
    private Sensor capturedSensor;

    @Implementation
    @SuppressWarnings("unused")
    public boolean requestTriggerSensor(android.hardware.TriggerEventListener listener, android.hardware.Sensor sensor) {
        Log.d(LOG_TAG, "Captured trigger listener=" + listener + ":sensor=" + sensor);
        capturedListener = listener;
        capturedSensor = sensor;
        return true;
    }

    @Implementation
    public android.hardware.Sensor getDefaultSensor(int type) {
        if (type == Sensor.TYPE_SIGNIFICANT_MOTION) {
            return mock(Sensor.class);
        }
        return null;
    }


    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    @SuppressWarnings("unused")
    public void triggerEvent() {
        // Just call the onTrigger method
        capturedListener.onTrigger(null);
    }

    public String toString() {
        return "CustomSensorManager:" + super.toString();
    }


}
