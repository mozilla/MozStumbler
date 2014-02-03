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
    private static final String     REPORTS_PREF  = "reports";
    private static final String     VALUES_VERSION_PREF = "values_version";
    private static final String     WIFI_ONLY = "wifi_only";
    private static final String     LAT_PREF = "lat_pref";
    private static final String     LON_PREF = "lon_pref";

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

    public String getLatLon() {
        float la=getPrefs().getFloat(LAT_PREF,0);
        float lo=getPrefs().getFloat(LON_PREF,0);
        String LatLon = Float.toString(la).concat(",").concat(Float.toString(lo));
        return LatLon;
    }
    public float getLat() {
        float Lat = getPrefs().getFloat(LAT_PREF,0);
        return Lat;
    }

    public float getLon() {
        float Lon = getPrefs().getFloat(LON_PREF,0);
        return Lon;
    }
    public void setLatLon(String LatLon) {
        float la = 0;
        float lo = 0;
        try {
            la = Float.parseFloat(LatLon.substring(0,LatLon.indexOf(",")));
            lo = Float.parseFloat(LatLon.substring(LatLon.lastIndexOf(",")+1));
        } catch (Exception err) {
            Log.w(LOGTAG,"Messed up again: ".concat(err.toString()));
            la = 0;
            lo = 0;
        }
        setLatLonPref(la,lo);
    }

    private void setLatLonPref(float la, float lo) {
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putFloat(LAT_PREF,la);
        editor.putFloat(LON_PREF,lo);
        apply(editor);
        Log.d(LOGTAG,Float.toString(la).concat(",").concat(Float.toString(lo)));
    }

    public void setReports(String json) {
        setStringPref(REPORTS_PREF, json);
    }

    public String getReports() {
        return getStringPref(REPORTS_PREF);
    }

    private String getStringPref(String key) {
        return getPrefs().getString(key, null);
    }

    private boolean getBoolPref(String key) {
        return getPrefs().getBoolean(key, false);
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
