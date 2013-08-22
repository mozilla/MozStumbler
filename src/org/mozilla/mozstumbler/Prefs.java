package org.mozilla.mozstumbler;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build.VERSION;
import android.util.Log;

import java.util.UUID;

final class Prefs {
    private static final String     LOGTAG        = Prefs.class.getName();
    private static final String     PREFS_FILE    = Prefs.class.getName();
    private static final String     NICKNAME_PREF = "nickname";
    private static final String     TOKEN_PREF    = "token";
    private static final String     REPORTS_PREF  = "reports";

    private final Context mContext;

    Prefs(Context context) {
        mContext = context;
    }

    UUID getToken() {
        UUID token = null;

        String pref = getStringPref(TOKEN_PREF);
        if (pref != null) {
            try {
                token = UUID.fromString(pref);
            } catch (IllegalArgumentException e) {
                Log.e(LOGTAG, "bad token pref: " + pref, e);
            }
        }

        if (token == null) {
            token = UUID.randomUUID();
            setStringPref(TOKEN_PREF, token.toString());
        }

        return token;
    }

    void deleteToken() {
        deleteStringPref(TOKEN_PREF);
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
