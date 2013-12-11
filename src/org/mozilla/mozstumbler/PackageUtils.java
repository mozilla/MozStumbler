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
            return pm.getPackageInfo("org.mozilla.mozstumbler", 0).versionName;
        } catch (NameNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
    }

    static String getMetaDataString(Context context, String name) {
        PackageManager pm = context.getPackageManager();
        String packageName = context.getPackageName();

        ApplicationInfo ai;
        try {
            ai = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
        } catch (NameNotFoundException e) {
            throw new IllegalArgumentException(e);
        }

        return ai.metaData.getString(name);
    }
}
