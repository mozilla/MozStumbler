package org.mozilla.osmdroid.tileprovider.util;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import org.mozilla.mozstumbler.service.core.logging.ClientLog;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

/**
 * Utility class for reading the manifest
 */
public class ManifestUtil {

    private static final String LOG_TAG = LoggerUtil.makeLogTag(ManifestUtil.class);

    /**
     * Retrieve a key from the manifest meta data, or empty string if not found.
     */
    public static String retrieveKey(final Context aContext, final String aKey) {

        // get the key from the manifest
        final PackageManager pm = aContext.getPackageManager();
        try {
            final ApplicationInfo info = pm.getApplicationInfo(aContext.getPackageName(),
                    PackageManager.GET_META_DATA);
            if (info.metaData == null) {
                ClientLog.i(LOG_TAG, "Key " + aKey + " not found in manifest");
            } else {
                final String value = info.metaData.getString(aKey);
                if (value == null) {
                    ClientLog.i(LOG_TAG, "Key " + aKey + " not found in manifest");
                } else {
                    return value.trim();
                }
            }
        } catch (final PackageManager.NameNotFoundException e) {
            ClientLog.i(LOG_TAG, "Key " + aKey + " not found in manifest");
        }
        return "";
    }

}
