package org.mozilla.mozstumbler;

import android.content.Context;

final class NetworkUtils {
    private NetworkUtils() {
    }

    static String getUserAgentString(Context context) {
        String appName = context.getString(R.string.app_name);
        String appVersion = PackageUtils.getAppVersion(context);

        // "MozStumbler/X.Y.Z"
        return appName + '/' + appVersion;
    }
}
