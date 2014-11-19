package org.mozilla.mozstumbler.client;

import android.content.Context;
import android.content.SharedPreferences;

import org.acra.ACRA;
import org.mozilla.mozstumbler.BuildConfig;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.core.logging.Log;
import org.mozilla.mozstumbler.service.core.logging.MockAcraLog;
import org.mozilla.osmdroid.api.IGeoPoint;
import org.mozilla.osmdroid.util.GeoPoint;

public class ClientPrefs extends Prefs {
    private static final String LOG_TAG = AppGlobals.makeLogTag(ClientPrefs.class.getSimpleName());
    private static final String LAT_PREF = "lat";
    private static final String LON_PREF = "lon";
    private static final String IS_FIRST_RUN = "is_first_run";
    public static final String MAP_TILE_RESOLUTION_TYPE = "map_tile_res_options";
    public static final String KEEP_SCREEN_ON_PREF = "keep_screen_on";
    public static final String ENABLE_OPTION_TO_SHOW_MLS_ON_MAP = "enable_the_option_to_show_mls_on_map";
    private static final String ON_MAP_MLS_DRAW_IS_ON = "actually_draw_mls_dots_on_map";
    public static final String CRASH_REPORTING = "crash_reporting";
    private static final String MIN_BATTERY_PCT = "min_battery_pct";
    public static final int MIN_BATTERY_DEFAULT = 15;

    public enum MapTileResolutionOptions {Default, HighRes, LowRes, NoMap}

    protected ClientPrefs(Context context) {
        super(context);
    }

    public static synchronized ClientPrefs createGlobalInstance(Context c) {
        if (sInstance == null) {
            sInstance = new ClientPrefs(c);
        }
        return (ClientPrefs) sInstance;
    }

    public static synchronized ClientPrefs getInstance() {
        if (sInstance != null && sInstance.getClass().isInstance(ClientPrefs.class)) {
            throw new IllegalArgumentException("sInstance is improperly initialized");
        }
        return (ClientPrefs) sInstance;
    }

    // For Mozilla Stumbler to use for manual upgrade of old prefs.
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
        return getBoolPrefWithDefault(ON_MAP_MLS_DRAW_IS_ON, false);
    }

    public void setOnMapShowMLS(boolean on) {
        setBoolPref(ON_MAP_MLS_DRAW_IS_ON, on);
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
        // have this pref follow the parent pref, so when the parent is turned on
        // this starts in the on state, and when parent is off, this pref is off
        setOnMapShowMLS(isEnabled);
    }

    public void setCrashReportingEnabled(boolean isOn) {
        setBoolPref(CRASH_REPORTING, isOn);
        if (isOn) {
            Log.d(LOG_TAG, "Enabled crash reporting");
            ACRA.setLog(MockAcraLog.getOriginalLog());
        } else {
            Log.d(LOG_TAG, "Disabled crash reporting");
            ACRA.setLog(new MockAcraLog());
        }
    }

    public boolean isCrashReportingEnabled() {
        // default to true for GITHUB build
        return getBoolPrefWithDefault(CRASH_REPORTING, BuildConfig.GITHUB);
    }

    public void setMapTileResolutionType(int mapTileResolutionType) {
        if (mapTileResolutionType >= MapTileResolutionOptions.values().length) {
            mapTileResolutionType = 0;
        }
        setMapTileResolutionType(MapTileResolutionOptions.values()[mapTileResolutionType]);
    }

    public void setMapTileResolutionType(MapTileResolutionOptions mapTileResolutionType) {
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putInt(MAP_TILE_RESOLUTION_TYPE, mapTileResolutionType.ordinal());
        apply(editor);
    }

    public MapTileResolutionOptions getMapTileResolutionType() {
        int i = getPrefs().getInt(MAP_TILE_RESOLUTION_TYPE, 0);
        if (i >= MapTileResolutionOptions.values().length) {
            i = 0;
        }
        return MapTileResolutionOptions.values()[i];
    }

    public int getMinBatteryPercent() {
        return getPrefs().getInt(MIN_BATTERY_PCT, MIN_BATTERY_DEFAULT);
    }

    public void setMinBatteryPercent(int percent) {
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putInt(MIN_BATTERY_PCT, percent);
        apply(editor);
    }
}