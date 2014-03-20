package org.mozilla.mozstumbler.preferences;

import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.preferences.Prefs;
import org.mozilla.mozstumbler.ScannerService;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import android.text.TextUtils;
import android.widget.EditText;

public class PreferencesScreen extends PreferenceActivity {
    private static final int REQUEST_CODE_WIFI_SCAN_ALWAYS = 1;

    private EditTextPreference mNicknamePreference;
    private CheckBoxPreference mGeofenceSwitch;
    private Preference mGeofenceHere;
    private CheckBoxPreference mWifiScanAlwaysSwitch;
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
        mWifiScanAlwaysSwitch = (CheckBoxPreference)getPreferenceManager().findPreference(Prefs.WIFI_SCAN_ALWAYS);

        mPrefs = new Prefs(this);

        setNicknamePreferenceTitle(mPrefs.getNickname());
        mWifiPreference.setChecked(mPrefs.getWifi());
        setGeofenceSwitchTitle();
        boolean geofence_here = mPrefs.getGeofenceHere();
        if(geofence_here) { mPrefs.setGeofenceState(true); }
        mGeofenceSwitch.setChecked(mPrefs.getGeofenceState());
        setGeofenceHereDesc(geofence_here);

        setPreferenceListener();
        initWifiScanAlways();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_WIFI_SCAN_ALWAYS) {
            boolean scanAlwaysAllowed = resultCode == Activity.RESULT_OK;
            mWifiScanAlwaysSwitch.setChecked(scanAlwaysAllowed);
        }
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
                mGeofenceSwitch.setChecked(true);
                mPrefs.setGeofenceState(false);
                return true;
            }
        });

        mGeofenceSwitch.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (mPrefs.getGeofenceHere() && newValue.equals(false))
                {
                    mPrefs.setGeofenceHere(false);
                    setGeofenceHereDesc(false);
                }
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

    private void initWifiScanAlways() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            mWifiScanAlwaysSwitch.setEnabled(false);
        } else {
            initWifiScanAlwaysMr2();
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void initWifiScanAlwaysMr2() {
        WifiManager wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if (mWifiScanAlwaysSwitch.isChecked() && !wm.isScanAlwaysAvailable()) {
            mWifiScanAlwaysSwitch.setChecked(false);
        }
        mWifiScanAlwaysSwitch.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object checked) {
                if ((Boolean) checked) {
                    WifiManager wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
                    if (wm.isScanAlwaysAvailable()) {
                        return true;
                    } else {
                        Intent i = new Intent(WifiManager.ACTION_REQUEST_SCAN_ALWAYS_AVAILABLE);
                        startActivityForResult(i, REQUEST_CODE_WIFI_SCAN_ALWAYS);
                        return false;
                    }
                } else {
                    return true;
                }
            }
        });
    }
}
