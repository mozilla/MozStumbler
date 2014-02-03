package org.mozilla.mozstumbler.preferences;

import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.preferences.Prefs;
import org.mozilla.mozstumbler.ScannerService;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.text.TextUtils;
import android.widget.EditText;

public class PreferencesScreen extends PreferenceActivity {

    private EditTextPreference mNicknamePreference;
    private CheckBoxPreference mGeofenceSwitch;
    private Preference mGeofenceHere;
    private Prefs mPrefs;

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getPreferenceManager().setSharedPreferencesName(Prefs.PREFS_FILE);
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD) {
            getPreferenceManager().setSharedPreferencesMode(MODE_MULTI_PROCESS);
        }
        addPreferencesFromResource(R.xml.preferences);
        CheckBoxPreference mWifiPreference;
        mNicknamePreference = (EditTextPreference) getPreferenceManager().findPreference("nickname");
        mWifiPreference = (CheckBoxPreference) getPreferenceManager().findPreference("wifi_only");
        mGeofenceSwitch = (CheckBoxPreference) getPreferenceManager().findPreference("geofence_switch");
        mGeofenceHere = getPreferenceManager().findPreference("geofence_here");

        mPrefs = new Prefs(this);

        setNicknamePreferenceTitle(mPrefs.getNickname());
        mWifiPreference.setChecked(mPrefs.getWifi());
        setGeofenceSwitchTitle();
        mGeofenceSwitch.setChecked(mPrefs.getGeofenceState());
        boolean geofence_here = mPrefs.getGeofenceHere();
        mGeofenceSwitch.setEnabled(!geofence_here);
        setGeofenceHereDesc(geofence_here);

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

        mGeofenceHere.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                mPrefs.setGeofenceHere(true);
                setGeofenceHereDesc(true);
                mGeofenceSwitch.setChecked(false);
                mGeofenceSwitch.setEnabled(false);
                mPrefs.setGeofenceState(false);
                return true;
            }
        });
    }
    private void setGeofenceHereDesc(boolean state) {
        if (state) {
            mGeofenceHere.setSummary(R.string.geofencing_explain);
        } else {
            mGeofenceHere.setSummary(R.string.geofencing_desc);
        }
    }

    private void setGeofenceSwitchTitle() {
        String geo_on = getResources().getString(R.string.geofencing_on);
        mGeofenceSwitch.setTitle(String.format(geo_on,mPrefs.getLat(),mPrefs.getLon()));
    }

    private void setNicknamePreferenceTitle(String nickname) {
        if (!TextUtils.isEmpty(nickname)) {
            mNicknamePreference.setTitle(getString(R.string.enter_nickname_title) + " " + nickname);
        } else {
            mNicknamePreference.setTitle(R.string.enter_nickname);
        }
    }
}
