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
    private EditTextPreference mLatLonPreference;
    private CheckBoxPreference mGeofenceSwitch;
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
        mLatLonPreference = (EditTextPreference) getPreferenceManager().findPreference("geofence");
        mGeofenceSwitch = (CheckBoxPreference) getPreferenceManager().findPreference("geofence_switch");

        mPrefs = new Prefs(this);

        setNicknamePreferenceTitle(mPrefs.getNickname());
        mWifiPreference.setChecked(mPrefs.getWifi());
        setGeofencePreferenceTitle(mPrefs.getLatLon());

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

        mLatLonPreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                mPrefs.setLatLon(newValue.toString());
                setGeofencePreferenceTitle(mPrefs.getLatLon());
                return true;
            }
        });

        mGeofenceSwitch.setOnPreferenceChangeListener( new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o) {
                if (o.equals(Boolean.TRUE)) {
                    return(mPrefs.getLat()!=0&&mPrefs.getLon()!=0);
                }
                    else {
                return true;
                }
            }
        });
    }

    private void setGeofencePreferenceTitle(String LatLon) {

        if (TextUtils.equals(LatLon,"0,0")||TextUtils.equals(LatLon,"0.0,0.0")) {
            mGeofenceSwitch.setTitle(R.string.geofencing_off);
            mGeofenceSwitch.setChecked(false);
        } else {
            String geo_on = getResources().getString(R.string.geofencing_on);
            mGeofenceSwitch.setTitle(String.format(geo_on,mPrefs.getLatLon()));
            mGeofenceSwitch.setChecked(true);
        }
    }

    private void setNicknamePreferenceTitle(String nickname) {
        if (!TextUtils.isEmpty(nickname)) {
            mNicknamePreference.setTitle(getString(R.string.enter_nickname_title) + " " + nickname);
        } else {
            mNicknamePreference.setTitle(R.string.enter_nickname);
        }
    }
}
