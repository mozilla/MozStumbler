/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Build.VERSION;
import android.text.TextUtils;
import android.util.Log;

import java.util.Calendar;
import java.util.Date;

public final class Prefs {
    private static final String     LOGTAG        = Prefs.class.getName();
    public  static final String     PREFS_FILE    = Prefs.class.getName();
    private static final String     NICKNAME_PREF = "nickname";
    private static final String     VALUES_VERSION_PREF = "values_version";
    private static final String     WIFI_ONLY = "wifi_only";
    private static final String     LAT_PREF = "lat_pref";
    private static final String     LON_PREF = "lon_pref";
    private static final String     GEOFENCE_HERE = "geofence_here";
    private static final String     GEOFENCE_SWITCH = "geofence_switch";
    public  static final String     WIFI_SCAN_ALWAYS = "wifi_scan_always";
    private static final String     FIREFOX_SCAN_ENABLED = "firefox_scan_on";
    private static final String     MOZ_API_KEY = "moz_api_key";
    private static final String     IS_NEW_DAY = "is_new_day";

    private static final String     STAR_SCORE_TODAY = "star_score_today";
    private static final String     RAINBOW_SCORE_TODAY = "rainbow_score_today";
    private static final String     COIN_SCORE_TODAY = "coin_score_today";
    private static final String     RAINBOW_COUNT_TODAY = "rainbow_count_today";
    private static final String     USER_TOTAL_POINTS = "user_total_points";

    private final SharedPreferences mSharedPrefs;
    static private Prefs sInstance;

    private Prefs(Context context) {
        mSharedPrefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_MULTI_PROCESS | Context.MODE_PRIVATE);
        if (getPrefs().getInt(VALUES_VERSION_PREF, -1) != AppGlobals.appVersionCode) {
            Log.i(LOGTAG, "Version of the application has changed. Updating default values.");
            // Remove old keys
            getPrefs().edit()
                    .remove("reports")
                    .remove("power_saving_mode")
                    .commit();

            getPrefs().edit().putInt(VALUES_VERSION_PREF, AppGlobals.appVersionCode).commit();
            getPrefs().edit().commit();
        }
    }

    /** Prefs must be created on application startup or service startup.
     * TODO: turn into regular singleton if Context dependency can be removed. */
    public static void createGlobalInstance(Context c) {
        if (sInstance != null)
            return;
        sInstance = new Prefs(c);
    }

    /** Only access after CreatePrefsInstance(Context) has been called at startup. */
    public static Prefs getInstance() {
        assert(sInstance != null);
        return sInstance;
    }

    ///
    /// Setters
    ///

    public void setUseWifiOnly(boolean state) {
        setBoolPref(WIFI_ONLY, state);
    }

    public void setGeofenceEnabled(boolean state) {
        setBoolPref(GEOFENCE_SWITCH, state);
    }

    public void setGeofenceHere(boolean flag) {
        setBoolPref(GEOFENCE_HERE, flag);
    }

    public void setGeofenceLocation(Location location) {
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putFloat(LAT_PREF, (float)location.getLatitude());
        editor.putFloat(LON_PREF, (float) location.getLongitude());
        apply(editor);
    }

    public void setMozApiKey(String s) {
        setStringPref(MOZ_API_KEY, s);
    }

    ///
    /// Getters
    ///
    public boolean getFirefoxScanEnabled() {
        return getBoolPrefWithDefault(FIREFOX_SCAN_ENABLED, false);
    }

    public String getMozApiKey() {
        String s = getStringPref(MOZ_API_KEY);
        return (s == null)? "no-mozilla-api-key" : s;
    }

    public boolean getGeofenceEnabled() {
        return getBoolPrefWithDefault(GEOFENCE_SWITCH, false);
    }

    public boolean getGeofenceHere() {
        return getBoolPrefWithDefault(GEOFENCE_HERE, false);
    }

    public Location getGeofenceLocation() {
        Location loc = new Location(AppGlobals.LOCATION_ORIGIN_INTERNAL);
        loc.setLatitude(getPrefs().getFloat(LAT_PREF, 0));
        loc.setLongitude(getPrefs().getFloat(LON_PREF,0));
        return loc;
    }

    public String getNickname() {
        String nickname = getStringPref(NICKNAME_PREF);
        if (nickname != null) {
            nickname = nickname.trim();
        }
        return TextUtils.isEmpty(nickname) ? null : nickname;
    }

    public void setFirefoxScanEnabled(boolean on) {
        setBoolPref(FIREFOX_SCAN_ENABLED, on);
    }

    public void setNickname(String nick) {
        if (nick != null) {
            nick = nick.trim();
            if (nick.length() > 0) {
                setStringPref(NICKNAME_PREF, nick);
            }
        }
    }

    public boolean getUseWifiOnly() {
        return getBoolPrefWithDefault(WIFI_ONLY, true);
    }

    public boolean getWifiScanAlways() {
        return getBoolPrefWithDefault(WIFI_SCAN_ALWAYS, false);
    }

    public void setWifiScanAlways(boolean b) {
        setBoolPref(WIFI_SCAN_ALWAYS, b);
    }

    public int getSavedDate() {
        if (getPrefs().contains(IS_NEW_DAY)) {
            return getPrefs().getInt(IS_NEW_DAY, -1);
        } else {
            return -1;
        }
    }

    public void saveTodayDate() {
        Calendar cal = Calendar.getInstance();
        int dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);

        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putInt(IS_NEW_DAY, dayOfMonth);
        apply(editor);
    }

    public int getStarScoreToday() {
        if (getPrefs().contains(STAR_SCORE_TODAY)) {
            return getPrefs().getInt(STAR_SCORE_TODAY, -1);
        } else {
            return -1;
        }
    }

    public int getRainbowScoreToday() {
        if (getPrefs().contains(RAINBOW_SCORE_TODAY)) {
            return getPrefs().getInt(RAINBOW_SCORE_TODAY, -1);
        } else {
            return -1;
        }
    }

    public int getCoinScoreToday() {
        if (getPrefs().contains(COIN_SCORE_TODAY)) {
            return getPrefs().getInt(COIN_SCORE_TODAY, -1);
        } else {
            return -1;
        }
    }

    public int getRainbowCountToday() {
        if (getPrefs().contains(RAINBOW_COUNT_TODAY)) {
            return getPrefs().getInt(RAINBOW_COUNT_TODAY, -1);
        } else {
            return -1;
        }
    }

    public int getUserTotalPoints() {
        if (getPrefs().contains(USER_TOTAL_POINTS)) {
            return getPrefs().getInt(USER_TOTAL_POINTS, -1);
        } else {
            return -1;
        }
    }

    public void saveStarScoreToday(int newStarScore) {
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putInt(STAR_SCORE_TODAY, newStarScore);
        apply(editor);
    }

    public void saveRainbowScoreToday(int newRainbowScore) {
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putInt(RAINBOW_SCORE_TODAY, newRainbowScore);
        apply(editor);
    }

    public void saveCoinScoreToday(int newCoinScore) {
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putInt(COIN_SCORE_TODAY, newCoinScore);
        apply(editor);
    }

    public void saveRainbowCountToday(int newRainbowCount) {
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putInt(RAINBOW_COUNT_TODAY, newRainbowCount);
        apply(editor);
    }

    public void saveUserTotalPoints(int newTotalPoints) {
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putInt(USER_TOTAL_POINTS, newTotalPoints);
        apply(editor);
    }

    ///
    /// Privates
    ///

    private String getStringPref(String key) {
        return getPrefs().getString(key, null);
    }

    private boolean getBoolPrefWithDefault(String key, boolean def) {
        return getPrefs().getBoolean(key, def);
    }

    private void setBoolPref(String key, Boolean state) {
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putBoolean(key,state);
        apply(editor);
    }

    private void setStringPref(String key, String value) {
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putString(key, value);
        apply(editor);
    }

    @TargetApi(9)
    private static void apply(SharedPreferences.Editor editor) {
        if (VERSION.SDK_INT >= 9) {
            editor.apply();
        } else if (!editor.commit()) {
            Log.e(LOGTAG, "", new IllegalStateException("commit() failed?!"));
        }
    }

    @SuppressLint("InlinedApi")
    private SharedPreferences getPrefs() {
        return mSharedPrefs;
    }
}
