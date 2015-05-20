/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Build.VERSION;
import android.text.TextUtils;
import android.util.Log;

import org.mozilla.mozstumbler.service.stumblerthread.motiondetection.SignificantMotionSensor;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

import java.util.regex.Pattern;

public class Prefs {
    public static final String NICKNAME_PREF = "nickname";
    public static final String EMAIL_PREF = "email";
    public static final String WIFI_ONLY = "wifi_only";
    public static final String MOTION_SENSOR_IS_SIGNIFICANT_TYPE = "motion_sensor_is_significant";
    public static final String MOTION_SENSOR_ENABLED = "motion_sensor_enabled";

    protected static final String PREFS_FILE = Prefs.class.getSimpleName();
    private static final String LOG_TAG = LoggerUtil.makeLogTag(Prefs.class);
    private static final String USER_AGENT_PREF = "user-agent";
    private static final String VALUES_VERSION_PREF = "values_version";
    private static final String FIREFOX_SCAN_ENABLED = "firefox_scan_on";
    private static final String MOZ_API_KEY = "moz_api_key";
    private static final String LAST_ATTEMPTED_UPLOAD_TIME = "last_attempted_upload_time";

    // Simulation prefs
    private static final String SIMULATE_STUMBLE = "simulate_stumble";
    private static final String SIMULATION_LAT = "simulate_lat";
    private static final String SIMULATION_LON = "simulate_lon";

    private static final String MOTION_CHANGE_DISTANCE_METERS = "motion_change_distance";
    private static final String MOTION_CHANGE_TIME_WINDOW_SECONDS = "motion_change_time";
    private static final String MOTION_SENSOR_MIN_PAUSE_SECONDS = "motion_sensor_min_pause_sec";

    private static final String SAVE_STUMBLE_LOGS = "save_stumble_logs";

    private static final String USE_OFFLINE_GEO = "use_offline_geo";

    protected static Prefs sInstance;
    private final SharedPreferences mSharedPrefs;

    private final boolean mSignificantMotionDefaultValue;

    protected Prefs(Context context) {
        mSharedPrefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_MULTI_PROCESS | Context.MODE_PRIVATE);
        if (getPrefs().getInt(VALUES_VERSION_PREF, -1) != AppGlobals.appVersionCode) {
            Log.i(LOG_TAG, "Version of the application has changed. Updating default values.");
            // Remove old keys
            getPrefs().edit()
                    .remove("reports")
                    .remove("power_saving_mode")
                    .commit();

            getPrefs().edit().putInt(VALUES_VERSION_PREF, AppGlobals.appVersionCode).commit();
            getPrefs().edit().commit();
        }

        boolean defaultSetting = false;
        try {
            if (SignificantMotionSensor.getSensor(context) != null) {
                String device = android.os.Build.MODEL.toLowerCase();
                String pattern = "(nexus \\d)|(a0001)";
                if (Pattern.compile(pattern).matcher(device).find()) {
                    // Most users aren't going to know to switch this setting to on, set the default for known good devices
                    defaultSetting = true;
                }
            }
        } catch (Exception ex) {}
        mSignificantMotionDefaultValue = defaultSetting;
    }

    // Allows code without a context handle to grab the prefs. The caller must null check the return value.
    public static Prefs getInstanceWithoutContext() {
        return sInstance;
    }

    /* Only access after CreatePrefsInstance(Context) has been called at startup. */
    public static synchronized Prefs getInstance(Context c) {
        if (sInstance == null) {
            sInstance = new Prefs(c);
        }
        return sInstance;
    }

    @TargetApi(9)
    protected static void apply(SharedPreferences.Editor editor) {
        if (VERSION.SDK_INT >= 9) {
            editor.apply();
        } else if (!editor.commit()) {
            Log.e(LOG_TAG, "", new IllegalStateException("commit() failed?!"));
        }
    }

    ///
    /// Getters
    ///
    public synchronized String getUserAgent() {
        String s = getStringPref(USER_AGENT_PREF);
        return (s == null) ? AppGlobals.appName + "/" + AppGlobals.appVersionName : s;
    }

    ///
    /// Setters
    ///
    public synchronized void setUserAgent(String userAgent) {
        setStringPref(USER_AGENT_PREF, userAgent);
    }

    public synchronized boolean getFirefoxScanEnabled() {
        return getBoolPrefWithDefault(FIREFOX_SCAN_ENABLED, false);
    }

    public synchronized void setFirefoxScanEnabled(boolean on) {
        setBoolPref(FIREFOX_SCAN_ENABLED, on);
    }

    public synchronized String getMozApiKey() {
        String s = getStringPref(MOZ_API_KEY);
        return (s == null) ? "no-mozilla-api-key" : s;
    }

    public synchronized void setMozApiKey(String s) {
        setStringPref(MOZ_API_KEY, s);
    }

    // This is the time an upload was last attempted, not necessarily successful.
    // Used to ensure upload attempts aren't happening too frequently.
    public synchronized long getLastAttemptedUploadTime() {
        return getPrefs().getLong(LAST_ATTEMPTED_UPLOAD_TIME, 0);
    }

    public synchronized void setLastAttemptedUploadTime(long time) {
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putLong(LAST_ATTEMPTED_UPLOAD_TIME, time);
        apply(editor);
    }

    public synchronized String getNickname() {
        String nickname = getStringPref(NICKNAME_PREF);
        if (nickname != null) {
            nickname = nickname.trim();
        }
        return TextUtils.isEmpty(nickname) ? null : nickname;
    }

    public synchronized void setNickname(String nick) {
        if (nick != null) {
            nick = nick.trim();
            setStringPref(NICKNAME_PREF, nick);
        }
    }

    public synchronized String getEmail() {
        String email = getStringPref(EMAIL_PREF);
        if (email != null) {
            email = email.trim();
        }
        return TextUtils.isEmpty(email) ? null : email;
    }

    public synchronized void setEmail(String email) {
        if (email != null) {
            email = email.trim();
            setStringPref(EMAIL_PREF, email);
        }
    }

    public synchronized boolean getUseWifiOnly() {
        return getBoolPrefWithDefault(WIFI_ONLY, true);
    }

    public synchronized void setUseWifiOnly(boolean state) {
        setBoolPref(WIFI_ONLY, state);
    }

    public synchronized boolean isSaveStumbleLogs() {
        return getBoolPrefWithDefault(SAVE_STUMBLE_LOGS, false);
    }

    public synchronized void setSaveStumbleLogs(boolean state) {
        setBoolPref(SAVE_STUMBLE_LOGS, state);
    }


    ///
    /// Privates
    ///

    protected String getStringPref(String key) {
        return getPrefs().getString(key, null);
    }

    protected boolean getBoolPrefWithDefault(String key, boolean def) {
        return getPrefs().getBoolean(key, def);
    }

    protected void setBoolPref(String key, Boolean state) {
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putBoolean(key, state);
        apply(editor);
    }

    protected float getFloatPrefWithDefault(String key, float def) {
        return getPrefs().getFloat(key, def);
    }

    protected void setFloatPref(String key, float value) {
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putFloat(key, value);
        apply(editor);
    }

    protected void setLongPref(String key, long value) {
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putLong(key, value);
        apply(editor);
    }

    protected void setStringPref(String key, String value) {
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putString(key, value);
        apply(editor);
    }

    @SuppressLint("InlinedApi")
    protected SharedPreferences getPrefs() {
        return mSharedPrefs;
    }

    private float getFloatPref(String name, float value) {
        return getFloatPrefWithDefault(name, value);
    }

    public boolean isSimulateStumble() {
        return getBoolPrefWithDefault(SIMULATE_STUMBLE, false);
    }

    public void setSimulateStumble(boolean b) {
        setBoolPref(SIMULATE_STUMBLE, b);
    }

    public float getSimulationLat() {
        return getFloatPref(SIMULATION_LAT, (float) 43.6472969);
    }

    protected void setSimulationLat(float value) {
        setFloatPref(SIMULATION_LAT, value);
    }

    public float getSimulationLon() {
        return getFloatPref(SIMULATION_LON, (float) -79.3943137);
    }

    protected void setSimulationLon(float value) {
        setFloatPref(SIMULATION_LON, value);
    }

    public int getMotionChangeDistanceMeters() {
        return getPrefs().getInt(MOTION_CHANGE_DISTANCE_METERS, 50);
    }

    public void setMotionChangeDistanceMeters(int value) {
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putInt(MOTION_CHANGE_DISTANCE_METERS, value);
        apply(editor);
    }

    public int getMotionChangeTimeWindowSeconds() {
        return getPrefs().getInt(MOTION_CHANGE_TIME_WINDOW_SECONDS, 120);
    }

    public void setMotionChangeTimeWindowSeconds(int value) {
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putInt(MOTION_CHANGE_TIME_WINDOW_SECONDS, value);
        apply(editor);
    }

    public boolean isMotionSensorEnabled() {
        return getPrefs().getBoolean(MOTION_SENSOR_ENABLED, true);
    }

    public void setMotionSensorEnabled(boolean on) {
        setBoolPref(MOTION_SENSOR_ENABLED, on);
    }

    public boolean isMotionSensorTypeSignificant() {
        return getBoolPrefWithDefault(MOTION_SENSOR_IS_SIGNIFICANT_TYPE, mSignificantMotionDefaultValue);
    }

    public void setMotionSensorTypeSignificant(boolean on) {
        setBoolPref(MOTION_SENSOR_IS_SIGNIFICANT_TYPE, on);
    }

    public void setMotionDetectionMinPauseTime(long seconds) {
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putLong(MOTION_SENSOR_MIN_PAUSE_SECONDS, seconds);
        apply(editor);
    }

    public long getMotionDetectionMinPauseTime() {
        return getPrefs().getLong(MOTION_SENSOR_MIN_PAUSE_SECONDS, 20);
    }

    public boolean useOfflineGeo() {
        return getPrefs().getBoolean(USE_OFFLINE_GEO, false);
    }

    public void setOfflineGeo(boolean offlineGeo) {
        setBoolPref(USE_OFFLINE_GEO, offlineGeo);
    }
}
