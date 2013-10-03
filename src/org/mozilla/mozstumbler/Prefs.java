package org.mozilla.mozstumbler;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build.VERSION;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import java.util.UUID;

final class Prefs {
    private static final String     LOGTAG        = Prefs.class.getName();
    private static final String     PREFS_FILE    = Prefs.class.getName();
    private static final String     NICKNAME_PREF = "nickname";
    private static final String     REPORTS_PREF  = "reports";
    private static final String     NOTICE_PREF   = "notice";

    private int mCurrentVersion;
    private Context mContext;

    Prefs(Context context) {
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

    String getNickname() {
        String nickname = getStringPref(NICKNAME_PREF);

        // Remove old empty nickname prefs.
        if (nickname != null && nickname.length() == 0) {
            deleteNickname();
            nickname = null;
        }

        return nickname;
    }

    void setNickname(String nickname) {
        if (nickname != null && nickname.length() > 0) {
            setStringPref(NICKNAME_PREF, nickname);
        } else {
            deleteNickname();
        }
    }

    void deleteNickname() {
        deleteStringPref(NICKNAME_PREF);
    }

    void setReports(String json) {
        setStringPref(REPORTS_PREF, json);
    }

    String getReports() {
        return getStringPref(REPORTS_PREF);
    }

    boolean hasSeenNotice() {
        int lastSeenVersion = getPrefs().getInt(NOTICE_PREF, 0);
        return lastSeenVersion == mCurrentVersion;
    }

    void setHasSeenNotice() {
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putInt(NOTICE_PREF, mCurrentVersion);
        apply(editor);
    }

    private String getStringPref(String key) {
        return getPrefs().getString(key, null);
    }

    private void setStringPref(String key, String value) {
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putString(key, value);
        apply(editor);
    }

    private void deleteStringPref(String key) {
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.remove(key);
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
