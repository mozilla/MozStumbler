/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.util.Log;

import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

public final class NetworkInfo {
    private static final String LOG_TAG = LoggerUtil.makeLogTag(NetworkInfo.class);

    ConnectivityManager mConnectivityManager;
    static NetworkInfo sInstance;

    private NetworkInfo() {}

    /* Created at startup by app, or service, using a context. */
    public static synchronized void createGlobalInstance(Context context) {
        getInstance();
        sInstance.mConnectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public static synchronized NetworkInfo getInstance() {
        if (sInstance == null) {
            sInstance = new NetworkInfo();
        }
        return sInstance;
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
