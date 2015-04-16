/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.util.Log;

import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

public class NetworkInfo {
    private static final String LOG_TAG = LoggerUtil.makeLogTag(NetworkInfo.class);
    ConnectivityManager mConnectivityManager;

    public NetworkInfo(Context context) {

        // getSystemService() should be called on the application context instead of any context.
        // This enables us to replace the system service using robolectric.
        mConnectivityManager = (ConnectivityManager) context
                                .getApplicationContext()
                                .getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public synchronized boolean isConnected() {
        if (mConnectivityManager == null) {
            Log.e(LOG_TAG, "ConnectivityManager is null!");
            return false;
        }

        android.net.NetworkInfo aNet = mConnectivityManager.getActiveNetworkInfo();
        return (aNet != null && aNet.isConnected());
    }

    public synchronized boolean isWifiAvailable() {
        if (mConnectivityManager == null) {
            Log.e(LOG_TAG, "ConnectivityManager is null!");
            return false;
        }

        android.net.NetworkInfo aNet = mConnectivityManager.getActiveNetworkInfo();
        return (aNet != null && aNet.getType() == ConnectivityManager.TYPE_WIFI);
    }
}
