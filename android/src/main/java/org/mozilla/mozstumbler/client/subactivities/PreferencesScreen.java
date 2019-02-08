/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.subactivities;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.text.TextUtils;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.mozstumbler.BuildConfig;
import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.ClientPrefs;
import org.mozilla.mozstumbler.client.IMainActivity;
import org.mozilla.mozstumbler.client.MainApp;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.utils.NetworkInfo;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.log.ILogger;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

import java.util.Random;

public class PreferencesScreen extends PreferenceActivity {

    private static final String LOG_TAG = LoggerUtil.makeLogTag(PreferencesScreen.class);
    ILogger Log = (ILogger) ServiceLocator.getInstance().getService(ILogger.class);

    private CheckBoxPreference mWifiPreference;
    private CheckBoxPreference mKeepScreenOn;
    private CheckBoxPreference mEnableShowMLSLocations;
    private CheckBoxPreference mCrashReportsOn;
    private CheckBoxPreference mUnlimitedMapZoom;
    private ListPreference mMapTileDetail;

    private final BroadcastReceiver callbackReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
           Log.w(LOG_TAG, "Unexpected intent: " + intent.toString());
        }
    };


    private ClientPrefs getPrefs() {
        return ClientPrefs.getInstance(this);
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.layout.stumbler_preferences);

        mWifiPreference = (CheckBoxPreference) getPreferenceManager().findPreference(Prefs.WIFI_ONLY);
        mKeepScreenOn = (CheckBoxPreference) getPreferenceManager().findPreference(ClientPrefs.KEEP_SCREEN_ON_PREF);
        mEnableShowMLSLocations = (CheckBoxPreference) getPreferenceManager().findPreference(ClientPrefs.ENABLE_OPTION_TO_SHOW_MLS_ON_MAP);
        mCrashReportsOn = (CheckBoxPreference) getPreferenceManager().findPreference(ClientPrefs.CRASH_REPORTING);
        mMapTileDetail = (ListPreference) getPreferenceManager().findPreference(ClientPrefs.MAP_TILE_RESOLUTION_TYPE);
        int valueIndex = ClientPrefs.getInstance(this).getMapTileResolutionType().ordinal();
        mMapTileDetail.setValueIndex(valueIndex);
        updateMapDetailTitle(valueIndex);

        mUnlimitedMapZoom = (CheckBoxPreference) getPreferenceManager().findPreference(ClientPrefs.IS_MAP_ZOOM_UNLIMITED);

        setPreferenceListener();
        setButtonListeners();
    }


    private void setButtonListeners() {
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

        button = findPreference("developer_button");
        button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference arg) {
                ((MainApp) getApplication()).showDeveloperDialog(PreferencesScreen.this);
                return true;
            }
        });

        button = findPreference("file_bug");
        button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference arg) {
                startActivity(new Intent(PreferencesScreen.this, FileBugActivity.class));
                return true;
            }
        });
    }

    private void updateMapDetailTitle(int index) {
        String label = getString(R.string.map_tile_resolution_options_label_dynamic);
        String option = mMapTileDetail.getEntries()[index].toString();
        mMapTileDetail.setTitle(String.format(label, option));
    }

    private void setPreferenceListener() {

        mWifiPreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                getPrefs().setUseWifiOnly(newValue.equals(true));
                return true;
            }
        });

        mKeepScreenOn.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                getPrefs().setKeepScreenOn(newValue.equals(true));
                ((MainApp) getApplication()).keepScreenOnPrefChanged(newValue.equals(true));
                return true;
            }
        });
        mEnableShowMLSLocations.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                getPrefs().enableMLSQueryResults(newValue.equals(true));
                if (newValue.equals(true)) {
                    Context c = PreferencesScreen.this;
                    String message = String.format(getString(R.string.enable_option_show_mls_on_map_detailed_info),
                            getString(R.string.upload_wifi_only_title));
                    AlertDialog.Builder builder = new AlertDialog.Builder(c)
                            .setTitle(preference.getTitle())
                            .setMessage(message).setPositiveButton(android.R.string.ok, null);
                    builder.create().show();
                }
                return true;
            }
        });
        mCrashReportsOn.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                getPrefs().setCrashReportingEnabled(newValue.equals(true));
                return true;
            }
        });
        mMapTileDetail.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                int i = mMapTileDetail.findIndexOfValue(newValue.toString());
                getPrefs().setMapTileResolutionType(i);
                updateMapDetailTitle(i);
                return true;
            }
        });
        mUnlimitedMapZoom.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                getPrefs().setIsMapZoomUnlimited(newValue.equals(true));
                return true;
            }
        });
    }
}
