package org.mozilla.mozstumbler.preferences;

import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.preferences.Prefs;
import org.mozilla.mozstumbler.ScannerService;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.SwitchPreference;
import android.text.TextUtils;
import android.util.Log;

public class PreferencesScreen extends PreferenceActivity {

    private EditTextPreference mNicknamePreference;
    private SwitchPreference mPowerSavingPreference;
    private Prefs mPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getPreferenceManager().setSharedPreferencesName(Prefs.PREFS_FILE);
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD) {
            getPreferenceManager().setSharedPreferencesMode(MODE_MULTI_PROCESS);
        }
        addPreferencesFromResource(R.xml.preferences);
        mNicknamePreference = (EditTextPreference) getPreferenceManager().findPreference("nickname");
        mPowerSavingPreference = (SwitchPreference) getPreferenceManager().findPreference("power_saving_mode");
        mPrefs = new Prefs(this);

        setNicknamePreferenceTitle(mPrefs.getNickname());
        setPowerSavingModeState(mPrefs.getPowerSavingMode());

        setPreferenceListener();
    }

    private void setPreferenceListener() {
        mNicknamePreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                setNicknamePreferenceTitle(newValue.toString());
                return true;
            }
        });

        mPowerSavingPreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                setPowerSavingModeState((Boolean)newValue);
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

    private void setPowerSavingModeState(Boolean state) {
      Log.d("PreferencesScreen", "setPowerSavingModeState : " + state);
    }
}
