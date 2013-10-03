package org.mozilla.mozstumbler;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

final class NetworkUtils {
    private static final String LOGTAG = NetworkUtils.class.getName();

    private NetworkUtils() {
    }

    @SuppressWarnings("deprecation")
    static boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            Log.e(LOGTAG, "ConnectivityManager is null!");
            return false;
        }

        if (!cm.getBackgroundDataSetting()) {
            Log.w(LOGTAG, "Background data is restricted!");
            return false;
        }

        NetworkInfo network = cm.getActiveNetworkInfo();
        if (network == null) {
            Log.w(LOGTAG, "No active network!");
            return false;
        }

        if (!network.isAvailable()) {
            Log.w(LOGTAG, "Active network is not available!");
            return false;
        }

        if (!network.isConnected()) {
            Log.w(LOGTAG, "Active network is not connected!");
            return false;
        }

        if (network.isRoaming()) {
            Log.w(LOGTAG, "Active network is roaming!");
            return false;
        }

        return true; // Network is OK!
    }

    static String getUserAgentString(Context context) {
        String appName = context.getString(R.string.app_name);
        String appVersion = PackageUtils.getAppVersion(context);

        // "MozStumbler/X.Y.Z"
        return appName + '/' + appVersion;
    }
}
