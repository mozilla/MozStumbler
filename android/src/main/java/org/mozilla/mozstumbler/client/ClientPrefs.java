package org.mozilla.mozstumbler.client;

import android.content.Context;
import android.content.SharedPreferences;
import org.mozilla.mozstumbler.service.Prefs;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.util.GeoPoint;

public class ClientPrefs extends Prefs {
    private static final String LOG_TAG = ClientPrefs.class.getSimpleName();
    private static final String LAT_PREF = "lat";
    private static final String LON_PREF = "lon";
    private static final String IS_FIRST_RUN = "isfirstrun";
    public static final String KEEP_SCREEN_ON_PREF = "keep_screen_on";
    public static final String ENABLE_OPTION_TO_SHOW_MLS_ON_MAP = "enable_show_mls_on_map";
    private static final String ON_MAP_SHOW_MLS_PREF = "show_mls";
    private static final String ON_MAP_SHOW_OBSERVATION_TYPE = "show_observation_type";

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

    public boolean getKeepScreenOn() {
        return getBoolPrefWithDefault(KEEP_SCREEN_ON_PREF, true);
    }

    public void setKeepScreenOn(boolean on) {
        setBoolPref(KEEP_SCREEN_ON_PREF, on);
    }

    public boolean getOnMapShowMLS() {
        return getBoolPrefWithDefault(ON_MAP_SHOW_MLS_PREF, false);
    }

    public void setOnMapShowMLS(boolean on) {
        setBoolPref(ON_MAP_SHOW_MLS_PREF, on);
    }

    public boolean getOnMapShowObservationType() {
        return getBoolPrefWithDefault(ON_MAP_SHOW_OBSERVATION_TYPE, false);
    }

    public void setOnMapShowObservationType(boolean on) {
        setBoolPref(ON_MAP_SHOW_OBSERVATION_TYPE, on);
    }

    public boolean isFirstRun() {
        return getBoolPrefWithDefault(IS_FIRST_RUN, true);
    }

    public void setFirstRun(boolean b) {
        setBoolPref(IS_FIRST_RUN, b);
    }

    public boolean isOptionEnabledToShowMLSOnMap() {
        return getBoolPrefWithDefault(ENABLE_OPTION_TO_SHOW_MLS_ON_MAP, false);
    }

    public void setOptionEnabledToShowMLSOnMap(boolean isEnabled) {
        setBoolPref(ENABLE_OPTION_TO_SHOW_MLS_ON_MAP, isEnabled);
        if (!isEnabled) {
            setOnMapShowMLS(false);
        }
    }

}
