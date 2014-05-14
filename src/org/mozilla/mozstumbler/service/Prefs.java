package org.mozilla.mozstumbler.service;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Build;
import android.os.Build.VERSION;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import org.mozilla.mozstumbler.BuildConfig;
import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.SharedConstants;


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

    private final Context mContext;

    public Prefs(Context context) {
        mContext = context;
        setDefaultValues();
    }

    @SuppressLint("InlinedApi")
    public void setDefaultValues() {
        if (getPrefs().getInt(VALUES_VERSION_PREF, -1) != BuildConfig.VERSION_CODE) {
            Log.i(LOGTAG, "Version of the application has changed. Updating default values.");
            // Remove old keys
            getPrefs().edit()
                    .remove("reports")
                    .remove("power_saving_mode")
                    .commit();
            PreferenceManager.setDefaultValues(mContext, PREFS_FILE,
                    Context.MODE_MULTI_PROCESS, R.xml.preferences, true);
            getPrefs().edit().putInt(VALUES_VERSION_PREF, BuildConfig.VERSION_CODE).commit();
            getPrefs().edit().commit();
        }
    }

    ///
    /// Setters
    ///

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
        return getBoolPref(GEOFENCE_SWITCH);
    }

    public boolean getGeofenceHere() {
        return getBoolPref(GEOFENCE_HERE);
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

    public boolean getWifi() {
        return getBoolPref(WIFI_ONLY, true);
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
