package org.mozilla.mozstumbler.client;

import android.content.Context;
import android.content.SharedPreferences;

import org.acra.ACRA;
import org.mozilla.mozstumbler.BuildConfig;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.core.logging.ClientLog;
import org.mozilla.mozstumbler.service.core.logging.MockAcraLog;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;
import org.mozilla.osmdroid.api.IGeoPoint;
import org.mozilla.osmdroid.util.GeoPoint;

public class ClientPrefs extends Prefs {
    public static final String MAP_TILE_RESOLUTION_TYPE = "map_tile_res_options";
    public static final String KEEP_SCREEN_ON_PREF = "keep_screen_on";
    public static final String ENABLE_OPTION_TO_SHOW_MLS_ON_MAP = "enable_the_option_to_show_mls_on_map";
    public static final String CRASH_REPORTING = "crash_reporting";
    public static final int MIN_BATTERY_DEFAULT = 15;
    public static final String LAST_VERSION = "last_version";
    public static final String IS_MAP_ZOOM_UNLIMITED = "unlimited_zoom";
    public static final String IS_SCANNING_PASSIVE = "passive_scanning";
    private static final String LOG_TAG = LoggerUtil.makeLogTag(ClientPrefs.class);
    private static final String LAT_PREF = "lat";
    private static final String LON_PREF = "lon";
    private static final String IS_FIRST_RUN = "is_first_run";
    private static final String ON_MAP_MLS_DRAW_IS_ON = "actually_draw_mls_dots_on_map";
    private static final String DEFAULT_SIMULATION_LAT_LONG = "default_simulation_lat_lon";
    private static final String MIN_BATTERY_PCT = "min_battery_pct";


    private static final String LB_BASE_URI = "org.mozilla.mozstumbler.client.lb_base_uri";
    private static final String LB_SUBMIT_URL = "org.mozilla.mozstumbler.client.lb_submit_url";

    private static final String FXA_CLIENT_ID = "org.mozilla.mozstumbler.client.fxa_client_id";
    private static final String FXA_PROFILE_SERVER = "org.mozilla.mozstumbler.client.fxa_profile_server";
    private static final String FXA_APP_CALLBACK = "org.mozilla.mozstumbler.client.fxa_app_callback";
    private static final String FXA_OAUTH2_SERVER = "org.mozilla.mozstumbler.fxa_oauth2_server";
    private static final String FXA_SCOPES = "org.mozilla.mozstumbler.client.fxa_scopes";

    protected ClientPrefs(Context context) {
        super(context);
    }

    public static synchronized ClientPrefs getInstance(Context c) {
        if (sInstance == null || sInstance.getClass() != ClientPrefs.class) {
            sInstance = new ClientPrefs(c);
        }
        return (ClientPrefs) sInstance;
    }

    public static synchronized ClientPrefs getInstanceWithoutContext() {
        if (sInstance != null && sInstance.getClass() != ClientPrefs.class) {
            return null;
        }
        return (ClientPrefs) sInstance;
    }

    // For Mozilla Stumbler to use for manual upgrade of old prefs.
    static String getPrefsFileNameForUpgrade() {
        return PREFS_FILE;
    }

    public String getFxaOauth2Server() {
        return getStringPref(FXA_OAUTH2_SERVER);
    }

    public void setFxaScopes(String scopes) {
        setStringPref(FXA_SCOPES, scopes);
    }

    public String getFxaScopes() {
        return getStringPref(FXA_SCOPES);
    }

    public void setFxaOauth2Server(String uri) {
        setStringPref(FXA_OAUTH2_SERVER, uri);
    }

    public void setFxaAppCallback(String callback_site) {
        setStringPref(FXA_APP_CALLBACK, callback_site);
    }

    public String getFxaAppCallback() {
        return getStringPref(FXA_APP_CALLBACK);
    }

    public String getLbBaseURI() {
        return getStringPref(LB_BASE_URI);
    }

    public void setLbBaseURI(String base_uri) {
        setStringPref(LB_BASE_URI, base_uri);
    }

    public String getLbSubmitUrl() {
        return getStringPref(LB_SUBMIT_URL);
    }
    public void setLbSubmitURL(String url) {
        setStringPref(LB_SUBMIT_URL, url);
    }

    public long getLastVersion() {
        return getPrefs().getLong(LAST_VERSION, 0);
    }

    public void setDontShowChangelog() {
        setLongPref(LAST_VERSION, BuildConfig.VERSION_CODE);
    }

    public void clearSimulationStart() {
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.remove(LAT_PREF);
        editor.remove(LON_PREF);
        apply(editor);
    }

    public synchronized GeoPoint getLastMapCenter() {
        final float lat = getPrefs().getFloat(LAT_PREF, 0);
        final float lon = getPrefs().getFloat(LON_PREF, 0);
        return new GeoPoint(lat, lon);
    }

    public synchronized void setLastMapCenter(IGeoPoint center) {
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putFloat(LAT_PREF, (float) center.getLatitude());
        editor.putFloat(LON_PREF, (float) center.getLongitude());

        if (AppGlobals.isDebug) {
            // Save the location as the start for simulations
            setSimulationLat((float) center.getLatitude());
            setSimulationLon((float) center.getLongitude());
        }
        apply(editor);
    }

    public boolean getKeepScreenOn() {
        return getBoolPrefWithDefault(KEEP_SCREEN_ON_PREF, false);
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

    public boolean isCrashReportingEnabled() {
        // default to true for GITHUB build
        return getBoolPrefWithDefault(CRASH_REPORTING, BuildConfig.GITHUB);
    }

    public void setCrashReportingEnabled(boolean isOn) {
        setBoolPref(CRASH_REPORTING, isOn);
        if (isOn) {
            ClientLog.d(LOG_TAG, "Enabled crash reporting");
            ACRA.setLog(MockAcraLog.getOriginalLog());
        } else {
            ClientLog.d(LOG_TAG, "Disabled crash reporting");
            ACRA.setLog(new MockAcraLog());
        }
    }

    public boolean isDefaultSimulationLatLon() {
        return getBoolPrefWithDefault(DEFAULT_SIMULATION_LAT_LONG, true);
    }

    public void wroteSimulationLatLon() {
        setBoolPref(DEFAULT_SIMULATION_LAT_LONG, false);
    }

    public void setMapTileResolutionType(int mapTileResolutionType) {
        if (mapTileResolutionType >= MapTileResolutionOptions.values().length) {
            mapTileResolutionType = 0;
        }
        setMapTileResolutionType(MapTileResolutionOptions.values()[mapTileResolutionType]);
    }

    public MapTileResolutionOptions getMapTileResolutionType() {
        int i = getPrefs().getInt(MAP_TILE_RESOLUTION_TYPE, 0);
        if (i >= MapTileResolutionOptions.values().length) {
            i = 0;
        }
        return MapTileResolutionOptions.values()[i];
    }

    public void setMapTileResolutionType(MapTileResolutionOptions mapTileResolutionType) {
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putInt(MAP_TILE_RESOLUTION_TYPE, mapTileResolutionType.ordinal());
        apply(editor);
    }

    public int getMinBatteryPercent() {
        return getPrefs().getInt(MIN_BATTERY_PCT, MIN_BATTERY_DEFAULT);
    }

    public void setMinBatteryPercent(int percent) {
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putInt(MIN_BATTERY_PCT, percent);
        apply(editor);
    }

    public void setFxaClientId(String fxaClientId) {
        setStringPref(FXA_CLIENT_ID, fxaClientId);
    }

    public String getFxaClientId() {
        return getStringPref(FXA_CLIENT_ID);
    }

    public String getFxaProfileServer() {
        return getStringPref(FXA_PROFILE_SERVER);
    }

    public void setFxaProfileServer(String fxaProfileServer) {
        setStringPref(FXA_PROFILE_SERVER, fxaProfileServer);
    }

    public enum MapTileResolutionOptions {Default, HighRes, LowRes, NoMap}

    public boolean isMapZoomUnlimited() {
        return getBoolPrefWithDefault(IS_MAP_ZOOM_UNLIMITED, false);
    }

    public void setIsMapZoomUnlimited(boolean isOn) {
        setBoolPref(IS_MAP_ZOOM_UNLIMITED, isOn);
    }

    public boolean isScanningPassive() {
        return getBoolPrefWithDefault(IS_SCANNING_PASSIVE, false);
    }

    public void setIsScanningPassive(boolean isOn) {
        setBoolPref(IS_SCANNING_PASSIVE, isOn);
    }
}
