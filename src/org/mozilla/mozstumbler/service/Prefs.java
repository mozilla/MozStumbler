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

    private final SharedPreferences mSharedPrefs;
    static private Prefs sInstance;

    private Prefs(Context context) {
        mSharedPrefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_MULTI_PROCESS | Context.MODE_PRIVATE);
        if (getPrefs().getInt(VALUES_VERSION_PREF, -1) != SharedConstants.appVersionCode) {
            Log.i(LOGTAG, "Version of the application has changed. Updating default values.");
            // Remove old keys
            getPrefs().edit()
                    .remove("reports")
                    .remove("power_saving_mode")
                    .commit();

            getPrefs().edit().putInt(VALUES_VERSION_PREF, SharedConstants.appVersionCode).commit();
            getPrefs().edit().commit();
        }
    }

    /** Prefs must be created on application startup or service startup.
     * TODO: turn into regular singleton if Context dependency can be removed. */
    public static void createGlobalInstance(Context c) {
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

    ///
    /// Getters
    ///

    public boolean getGeofenceEnabled() {
        return getBoolPrefWithDefault(GEOFENCE_SWITCH, false);
    }

    public boolean getGeofenceHere() {
        return getBoolPrefWithDefault(GEOFENCE_HERE, false);
    }

    public Location getGeofenceLocation() {
        Location loc = new Location(SharedConstants.LOCATION_ORIGIN_INTERNAL);
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

    public void setSha(String a, String b) {
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putString(a, b);
        apply(editor);
    }

    public String getSha(String a) {
        return getPrefs().getString(a, "first");
    }
    public boolean getUseWifiOnly() {
        return getBoolPrefWithDefault(WIFI_ONLY, true);
    }

    public boolean getWifiScanAlways() {
        return getBoolPrefWithDefault(WIFI_SCAN_ALWAYS, false);
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
