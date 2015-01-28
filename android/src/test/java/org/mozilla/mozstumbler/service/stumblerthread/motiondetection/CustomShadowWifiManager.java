/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.mozstumbler.service.stumblerthread.motiondetection;

import android.net.wifi.WifiManager;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowWifiManager;

@Implements(WifiManager.class)
public class CustomShadowWifiManager extends ShadowWifiManager {

    @Implementation
    public boolean isWifiEnabled() {
        return true;
    }

    @Implementation
    public boolean isScanAlwaysAvailable() {
        return true;
    }
}
