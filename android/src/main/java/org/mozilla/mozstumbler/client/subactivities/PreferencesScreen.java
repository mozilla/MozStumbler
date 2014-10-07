/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.subactivities;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.text.TextUtils;
import android.view.View;

import org.mozilla.mozstumbler.BuildConfig;
import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.ClientPrefs;
import org.mozilla.mozstumbler.client.MainApp;
import org.mozilla.mozstumbler.service.AppGlobals;

public class PreferencesScreen extends PreferenceActivity {
    private EditTextPreference mNicknamePreference;
    private EditTextPreference mEmailPreference;

    private CheckBoxPreference mWifiPreference;
    private CheckBoxPreference mKeepScreenOn;
    private static ClientPrefs sPrefs;

    /* Precondition to using this class, call this method to set Prefs */
    public static void setPrefs(ClientPrefs p) {
        sPrefs = p;
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        assert(sPrefs != null);

        addPreferencesFromResource(R.xml.stumbler_preferences);

        mNicknamePreference = (EditTextPreference) getPreferenceManager().findPreference("nickname");
        mEmailPreference = (EditTextPreference) getPreferenceManager().findPreference("email");

        mWifiPreference = (CheckBoxPreference) getPreferenceManager().findPreference("wifi_only");
        mKeepScreenOn = (CheckBoxPreference) getPreferenceManager().findPreference(ClientPrefs.KEEP_SCREEN_ON_PREF);

        setNicknamePreferenceTitle(sPrefs.getNickname());
        setEmailPreferenceTitle(sPrefs.getEmail());
        mWifiPreference.setChecked(sPrefs.getUseWifiOnly());

        setPreferenceListener();

        Preference button = findPreference("about_button");
        button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference arg) {
                startActivity(new Intent(PreferencesScreen.this, AboutActivity.class));
                return true;
            }
        });

        button = findPreference("log_button");
        button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference arg) {
                startActivity(new Intent(PreferencesScreen.this, LogActivity.class));
                return true;
            }
        });

        button = findPreference("leaderboard_button");
        if (Build.VERSION.SDK_INT > 10) {
            ((PreferenceGroup) findPreference("api_10_support")).removePreference(button);
        }
        button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference arg) {
                startActivity(new Intent(PreferencesScreen.this, LeaderboardActivity.class));
                return true;
            }
        });


        button = findPreference("developer_button");
        button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference arg) {
                ((MainApp) getApplication()).showDeveloperDialog(PreferencesScreen.this);
                return true;
            }
        });
    }

    private void setPreferenceListener() {
        mNicknamePreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                sPrefs.setNickname(newValue.toString());
                setNicknamePreferenceTitle(newValue.toString());
                return true;
            }
        });

        mEmailPreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                sPrefs.setEmail(newValue.toString());
                setEmailPreferenceTitle(newValue.toString());
                return true;
            }
        });

        mWifiPreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                sPrefs.setUseWifiOnly(newValue.equals(true));
                return true;
            }
        });

        mKeepScreenOn.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                sPrefs.setKeepScreenOn(newValue.equals(true));
                return true;
            }
        });
    }

    private void setNicknamePreferenceTitle(String nickname) {
        if (!TextUtils.isEmpty(nickname)) {
            mNicknamePreference.setTitle(getString(R.string.enter_nickname_title) + " " + nickname);
        } else {
            mNicknamePreference.setTitle(R.string.enter_nickname);
        }
    }

    private void setEmailPreferenceTitle(String email) {
        if (!TextUtils.isEmpty(email)) {
            mEmailPreference.setTitle(getString(R.string.enter_email_title) + " " + email);
        } else {
            mEmailPreference.setTitle(R.string.enter_email);
        }
    }
}
