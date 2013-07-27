package org.mozilla.mozstumbler;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Build.VERSION;
import android.util.Log;

import java.util.UUID;

final class Prefs {
    private static final String     LOGTAG     = Prefs.class.getName();
    private static final String     PREFS_FILE = Prefs.class.getName();
    private static final String     NICKNAME_PREF = "nickname";
    private static final String     TOKEN_PREF = "token";

    private final SharedPreferences mPrefs;

    Prefs(Context context) {
        mPrefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
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

        Log.d(LOGTAG, "token: " + token);
        return token;
    }

    void deleteToken() {
        deleteStringPref(TOKEN_PREF);
    }

    String getNickname() {
        String nickname = getStringPref(NICKNAME_PREF);
        if (nickname == null) {
            // Generate a semi-anonymous nickname.
            nickname = Build.MODEL + " (Android " + VERSION.RELEASE + ")";
            setNickname(nickname);
        }

        Log.d(LOGTAG, "nickname: " + nickname);
        return nickname;
    }

    void setNickname(String nickname) {
        if (nickname != null) {
            setStringPref(NICKNAME_PREF, nickname);
        } else {
            deleteNickname();
        }
    }

    void deleteNickname() {
        deleteStringPref(NICKNAME_PREF);
    }

    private String getStringPref(String key) {
        return mPrefs.getString(key, null);
    }

    private void setStringPref(String key, String value) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putString(key, value);
        editor.commit();
    }

    private void deleteStringPref(String key) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.remove(key);
        editor.commit();
    }
}
