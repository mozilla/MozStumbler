package org.mozilla.mozstumbler.service.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

public final class NetworkUtils {
    private static final String LOGTAG = NetworkUtils.class.getName();

    ConnectivityManager mConnectivityManager;
    static NetworkUtils sInstance;

    /* Created at startup by app, or service, using a context. */
    static public void createGlobalInstance(Context context) {
        sInstance = new NetworkUtils();
        sInstance.mConnectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    /* If accessed before singleton instantiation will abort. */
    public static NetworkUtils getInstance() {
        assert(sInstance != null);
        return sInstance;
    }

    public boolean isWifiAvailable() {
        if (mConnectivityManager == null) {
            Log.e(LOGTAG, "ConnectivityManager is null!");
            return false;
        }

        NetworkInfo aNet = mConnectivityManager.getActiveNetworkInfo();
        return (aNet != null && aNet.getType() == ConnectivityManager.TYPE_WIFI);
    }

}
