/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build.VERSION;
import android.text.TextUtils;
import android.util.Log;

public class Prefs {
    private static final String LOG_TAG = AppGlobals.makeLogTag(Prefs.class.getSimpleName());
    private static final String NICKNAME_PREF = "nickname";
    private static final String EMAIL_PREF = "email";
    private static final String USER_AGENT_PREF = "user-agent";
    private static final String VALUES_VERSION_PREF = "values_version";
    private static final String WIFI_ONLY = "wifi_only";
    private static final String LAT_PREF = "lat_pref";
    private static final String LON_PREF = "lon_pref";
    private static final String FIREFOX_SCAN_ENABLED = "firefox_scan_on";
    private static final String MOZ_API_KEY = "moz_api_key";
    private static final String LAST_ATTEMPTED_UPLOAD_TIME = "last_attempted_upload_time";
    protected static final String PREFS_FILE = Prefs.class.getSimpleName();

    private final SharedPreferences mSharedPrefs;
    protected static Prefs sInstance;

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
    }

    /* Prefs must be created on application startup or service startup. */
    public static synchronized Prefs createGlobalInstance(Context c) {
        if (sInstance == null) {
            sInstance = new Prefs(c);
        }
        return sInstance;
    }

    /* Only access after CreatePrefsInstance(Context) has been called at startup. */
    public static synchronized Prefs getInstance() {
        assert(sInstance != null);
        return sInstance;
    }

    ///
    /// Setters
    ///
    public synchronized void setUserAgent(String userAgent) {
        setStringPref(USER_AGENT_PREF, userAgent);
    }

    public synchronized void setUseWifiOnly(boolean state) {
        setBoolPref(WIFI_ONLY, state);
    }

    public synchronized void setMozApiKey(String s) {
        setStringPref(MOZ_API_KEY, s);
    }

    ///
    /// Getters
    ///
    public synchronized String getUserAgent() {
        String s = getStringPref(USER_AGENT_PREF);
        return (s == null)? AppGlobals.appName + "/" + AppGlobals.appVersionName : s;
    }

    public synchronized boolean getFirefoxScanEnabled() {
        return getBoolPrefWithDefault(FIREFOX_SCAN_ENABLED, false);
    }

    public synchronized String getMozApiKey() {
        String s = getStringPref(MOZ_API_KEY);
        return (s == null)? "no-mozilla-api-key" : s;
    }

    // This is the time an upload was last attempted, not necessarily successful.
    // Used to ensure upload attempts aren't happening too frequently.
    public synchronized long getLastAttemptedUploadTime() {
        return getPrefs().getLong(LAST_ATTEMPTED_UPLOAD_TIME, 0);
    }

    public synchronized String getNickname() {
        String nickname = getStringPref(NICKNAME_PREF);
        if (nickname != null) {
            nickname = nickname.trim();
        }
        return TextUtils.isEmpty(nickname) ? null : nickname;
    }


    public synchronized String getEmail() {
        String email = getStringPref(EMAIL_PREF);
        if (email != null) {
            email = email.trim();
        }
        return TextUtils.isEmpty(email) ? null : email;
    }

    public synchronized void setFirefoxScanEnabled(boolean on) {
        setBoolPref(FIREFOX_SCAN_ENABLED, on);
    }

    public synchronized void setLastAttemptedUploadTime(long time) {
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putLong(LAST_ATTEMPTED_UPLOAD_TIME, time);
        apply(editor);
    }

    public synchronized void setEmail(String email) {
        if (email != null) {
            email = email.trim();
            setStringPref(EMAIL_PREF, email);
        }
    }

    public synchronized void setNickname(String nick) {
        if (nick != null) {
            nick = nick.trim();
            setStringPref(NICKNAME_PREF, nick);
        }
    }

    public synchronized boolean getUseWifiOnly() {
        return getBoolPrefWithDefault(WIFI_ONLY, true);
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
        editor.putBoolean(key,state);
        apply(editor);
    }

    protected void setStringPref(String key, String value) {
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putString(key, value);
        apply(editor);
    }

    @TargetApi(9)
    protected static void apply(SharedPreferences.Editor editor) {
        if (VERSION.SDK_INT >= 9) {
            editor.apply();
        } else if (!editor.commit()) {
            Log.e(LOG_TAG, "", new IllegalStateException("commit() failed?!"));
        }
    }

    @SuppressLint("InlinedApi")
    protected SharedPreferences getPrefs() {
        return mSharedPrefs;
    }
}
