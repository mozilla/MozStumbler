package org.mozilla.mozstumbler;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;

final class PackageUtils {
    private PackageUtils() {
    }

    static String getAppVersion(Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            return pm.getPackageInfo(BuildConfig.PACKAGE_NAME, 0).versionName;
        } catch (NameNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
