package org.mozilla.mozstumbler.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build.VERSION;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import java.util.UUID;

public final class Prefs {
    private static final String     LOGTAG        = Prefs.class.getName();
            static final String     PREFS_FILE    = Prefs.class.getName();
    private static final String     NICKNAME_PREF = "nickname";
    private static final String     POWER_SAVING_MODE_PREF = "power_saving_mode";
    private static final String     REPORTS_PREF  = "reports";

    private int mCurrentVersion;
    private Context mContext;

    public Prefs(Context context) {
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(),
                                                                        PackageManager.GET_ACTIVITIES);
            mCurrentVersion = pi.versionCode;
        } catch (PackageManager.NameNotFoundException exception) {
            Log.e(LOGTAG, "getPackageInfo failed", exception);
            mCurrentVersion = 0;
        }
        mContext = context;
    }

    public String getNickname() {
        String nickname = getStringPref(NICKNAME_PREF);
        if (nickname != null) {
            nickname = nickname.trim();
        }

        return TextUtils.isEmpty(nickname) ? null : nickname;
    }

    public boolean getPowerSavingMode() {
        return getPrefs().getBoolean(POWER_SAVING_MODE_PREF, true);
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

    private void setStringPref(String key, String value) {
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putString(key, value);
        apply(editor);
    }

    private static void apply(SharedPreferences.Editor editor) {
        if (VERSION.SDK_INT >= 9) {
            editor.apply();
        } else if (!editor.commit()) {
            Log.e(LOGTAG, "", new IllegalStateException("commit() failed?!"));
        }
    }

    private SharedPreferences getPrefs() {
        return mContext.getSharedPreferences(PREFS_FILE, Context.MODE_MULTI_PROCESS | Context.MODE_PRIVATE);
    }
}
