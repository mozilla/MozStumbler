package org.mozilla.mozstumbler.preferences;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build.VERSION;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import org.mozilla.mozstumbler.BuildConfig;
import org.mozilla.mozstumbler.R;

public final class Prefs {
    private static final String     LOGTAG        = Prefs.class.getName();
            static final String     PREFS_FILE    = Prefs.class.getName();
    private static final String     NICKNAME_PREF = "nickname";
    private static final String     POWER_SAVING_MODE_PREF = "power_saving_mode";
    private static final String     VALUES_VERSION_PREF = "values_version";
    private static final String     WIFI_ONLY = "wifi_only";
    private static final String     LAT_PREF = "lat_pref";
    private static final String     LON_PREF = "lon_pref";
    private static final String     GEOFENCE_HERE = "geofence_here";
    private static final String     GEOFENCE_SWITCH = "geofence_switch";
            static final String     WIFI_SCAN_ALWAYS = "wifi_scan_always";

    private final Context mContext;

    public Prefs(Context context) {
        mContext = context;
    }

    @SuppressLint("InlinedApi")
    public void setDefaultValues() {
        final SharedPreferences prefs = getPrefs();
        if (prefs.getInt(VALUES_VERSION_PREF, -1) != BuildConfig.VERSION_CODE) {
            Log.i(LOGTAG, "Version of the application has changed. Updating default values.");
            PreferenceManager.setDefaultValues(mContext, PREFS_FILE,
                    Context.MODE_MULTI_PROCESS, R.xml.preferences, true);
            prefs.edit().putInt(VALUES_VERSION_PREF, BuildConfig.VERSION_CODE).commit();
        }
    }

    ///
    /// Setters
    ///

    public void setGeofenceState(boolean state) {
        setBoolPref(GEOFENCE_SWITCH,state);
    }

    public void setGeofenceHere(boolean flag) {
        setBoolPref(GEOFENCE_HERE,flag);
    }

    public void setLatLonPref(float la, float lo) {
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putFloat(LAT_PREF,la);
        editor.putFloat(LON_PREF,lo);
        apply(editor);
        Log.d(LOGTAG, "Geofence set: " + la + "," + lo);
    }

    ///
    /// Getters
    ///

    public boolean getGeofenceState() {
        return getBoolPref(GEOFENCE_SWITCH);
    }

    public boolean getGeofenceHere() {
        return getBoolPref(GEOFENCE_HERE,false);
    }

    public float getLat() {
        return getPrefs().getFloat(LAT_PREF, 0);
    }

    public float getLon() {
        return getPrefs().getFloat(LON_PREF,0);
    }

    public String getNickname() {
        String nickname = getStringPref(NICKNAME_PREF);
        if (nickname != null) {
            nickname = nickname.trim();
        }
        return TextUtils.isEmpty(nickname) ? null : nickname;
    }

    public boolean getWifi() {
        return getBoolPref(WIFI_ONLY);
    }

    public boolean getWifiScanAlways() {
        return getBoolPref(WIFI_SCAN_ALWAYS);
    }

    ///
    /// Privates
    ///


    private String getStringPref(String key) {
        return getPrefs().getString(key, null);
    }

    private boolean getBoolPref(String key) {
        return getPrefs().getBoolean(key, false);
    }

    private boolean getBoolPref(String key, boolean def) {
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
        return mContext.getSharedPreferences(PREFS_FILE, Context.MODE_MULTI_PROCESS | Context.MODE_PRIVATE);
    }
}
