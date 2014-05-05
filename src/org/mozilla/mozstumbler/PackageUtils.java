package org.mozilla.mozstumbler;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;

final public class PackageUtils {
    private PackageUtils() {
    }

    public static String getAppVersion(Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            return pm.getPackageInfo(BuildConfig.PACKAGE_NAME, 0).versionName;
        } catch (NameNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
