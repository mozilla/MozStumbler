package org.mozilla.mozstumbler.client;

import android.content.Context;
import android.content.SharedPreferences;
import org.mozilla.mozstumbler.service.Prefs;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.util.GeoPoint;

public class ClientPrefs extends Prefs {
    private static final String LOG_TAG = ClientPrefs.class.getName();
    private static final String LAT_PREF = "lat";
    private static final String LON_PREF = "lon";

    protected ClientPrefs(Context context) {
        super(context);
    }

    public static synchronized void createGlobalInstance(Context c) {
        if (sInstance != null) {
            return;
        }
        sInstance = new ClientPrefs(c);
    }

    public static synchronized ClientPrefs getInstance() {
        assert(sInstance != null);
        assert(sInstance.getClass().isInstance(ClientPrefs.class));
        return (ClientPrefs)sInstance;
    }

    // For MozStumbler to use for manual upgrade of old prefs.
    static String getPrefsFileNameForUpgrade() {
        return PREFS_FILE;
    }

    public synchronized void setLastMapCenter(IGeoPoint center) {
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putFloat(LAT_PREF, (float) center.getLatitude());
        editor.putFloat(LON_PREF, (float) center.getLongitude());
        apply(editor);
    }
    public synchronized GeoPoint getLastMapCenter() {
        final float lat = getPrefs().getFloat(LAT_PREF, 0);
        final float lon = getPrefs().getFloat(LON_PREF, 0);
        return new GeoPoint(lat, lon);
    }
}
