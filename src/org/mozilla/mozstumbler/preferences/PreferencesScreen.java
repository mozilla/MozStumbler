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

        mPrefs = new Prefs(this);

        setNicknamePreferenceTitle(mPrefs.getNickname());
        mWifiPreference.setChecked(mPrefs.getWifi());
        setGeoFencePreferenceTitle(mPrefs.getLatLon());

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
                String nLatLon = mPrefs.getLatLon();
                setGeoFencePreferenceTitle(nLatLon);
                return true;
            }
        });
    }

    private void setGeoFencePreferenceTitle(String LatLon) {

        if (TextUtils.equals(LatLon,"0,0")||TextUtils.equals(LatLon,"0.0,0.0")) {
            mLatLonPreference.setTitle(R.string.geofencing_off);
        } else {
            mLatLonPreference.setTitle(R.string.geofencing_on);
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
