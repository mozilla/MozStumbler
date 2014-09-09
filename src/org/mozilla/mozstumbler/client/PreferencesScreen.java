/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.text.TextUtils;
import org.mozilla.mozstumbler.R;

public class PreferencesScreen extends PreferenceActivity {
    private static final int REQUEST_CODE_WIFI_SCAN_ALWAYS = 1;

    private EditTextPreference mNicknamePreference;
    private CheckBoxPreference mWifiScanAlwaysSwitch;
    private CheckBoxPreference mWifiPreference;
    private CheckBoxPreference mIsHardwareAccelerated;

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
        mWifiPreference = (CheckBoxPreference) getPreferenceManager().findPreference("wifi_only");
        mWifiScanAlwaysSwitch = (CheckBoxPreference) getPreferenceManager().findPreference("wifi_scan_always");
        mIsHardwareAccelerated = (CheckBoxPreference) getPreferenceManager().findPreference("hardware_acceleration");

        setNicknamePreferenceTitle(sPrefs.getNickname());
        mWifiPreference.setChecked(sPrefs.getUseWifiOnly());

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
                sPrefs.setNickname(newValue.toString());
                setNicknamePreferenceTitle(newValue.toString());
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

        mIsHardwareAccelerated.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                sPrefs.setIsHardwareAccelerated(newValue.equals(true));
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
                boolean result = false;
                if ((Boolean) checked) {
                    WifiManager wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
                    if (wm.isScanAlwaysAvailable()) {
                        result = true;
                    } else {
                        Intent i = new Intent(WifiManager.ACTION_REQUEST_SCAN_ALWAYS_AVAILABLE);
                        startActivityForResult(i, REQUEST_CODE_WIFI_SCAN_ALWAYS);
                        result = false;
                    }
                } else {
                    result = true;
                }
                sPrefs.setWifiScanAlways(result);
                return result;
            }
        });
    }
}
