/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.subactivities;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.accounts.fxa.FxAGlobals;
import org.mozilla.accounts.fxa.Intents;
import org.mozilla.accounts.fxa.dialog.OAuthDialog;
import org.mozilla.accounts.fxa.tasks.DestroyOAuthTask;
import org.mozilla.accounts.fxa.tasks.ProfileJson;
import org.mozilla.accounts.fxa.tasks.RetrieveProfileTask;
import org.mozilla.accounts.fxa.tasks.SetDisplayNameTask;
import org.mozilla.accounts.fxa.tasks.VerifyOAuthTask;
import org.mozilla.mozstumbler.BuildConfig;
import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.ClientPrefs;
import org.mozilla.mozstumbler.client.MainApp;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.utils.NetworkInfo;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.log.ILogger;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

public class PreferencesScreen extends PreferenceActivity {

    private static final String LOG_TAG = LoggerUtil.makeLogTag(PreferencesScreen.class);
    ILogger Log = (ILogger) ServiceLocator.getInstance().getService(ILogger.class);

    private EditTextPreference mNicknamePreference;

    private CheckBoxPreference mWifiPreference;
    private CheckBoxPreference mKeepScreenOn;
    private CheckBoxPreference mEnableShowMLSLocations;
    private CheckBoxPreference mCrashReportsOn;
    private ListPreference mMapTileDetail;
    private Preference mFxaLoginPreference;

    private final BroadcastReceiver fxaCallbackReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intents.ORG_MOZILLA_ACCOUNTS_FXA_BEARER_TOKEN)) {
                processBearerToken(intent);
            } else if (intent.getAction().equals(Intents.PROFILE_READ)) {
                processProfile(context, intent);
            } else if (intent.getAction().equals(Intents.PROFILE_READ_FAILURE)) {
                // bad profile reads automatically logout the user
                getPrefs().setBearerToken("");
                clearFxaLoginState();
            } else if (intent.getAction().equals(Intents.OAUTH_DESTROY)) {
                clearFxaLoginState();
            } else if (intent.getAction().equals(Intents.OAUTH_DESTROY_FAIL)) {
                // I don't care.  Clear the login state even if fxa logout 'fails'
                clearFxaLoginState();
            } else if (intent.getAction().equals(Intents.DISPLAY_NAME_WRITE)) {
                // Refetch the profile to make sure we have the proper display name
                fetchFxaProfile(getPrefs().getBearerToken());
            } else if (intent.getAction().equals(Intents.DISPLAY_NAME_WRITE_FAILURE)) {
                fetchFxaProfile(getPrefs().getBearerToken());
            } else if (intent.getAction().equals(Intents.OAUTH_VERIFY)) {
                // Do nothing
            } else if (intent.getAction().equals(Intents.OAUTH_VERIFY_FAIL)) {
                clearFxaLoginState();
            }
        }

        private void processBearerToken(Intent intent) {
            String jsonBlob = intent.getStringExtra("json");
            if (TextUtils.isEmpty(jsonBlob)) {
                Log.w(LOG_TAG, "error extracting json data");
                return;
            }
            JSONObject authJSON = null;
            try {
                authJSON = new JSONObject(jsonBlob);
                String bearerToken = authJSON.getString("access_token");
                getPrefs().setBearerToken(bearerToken);
                fetchFxaProfile(bearerToken);
            } catch (JSONException e) {
                Log.e(LOG_TAG, "Error fetching bearer token.", e);
                return;
            }
        }

        private void processProfile(Context context, Intent intent) {
            String jsonBlob = intent.getStringExtra("json");
            if (TextUtils.isEmpty(jsonBlob)) {
                Log.w(LOG_TAG, "error extracting json data");
                return;
            }
            ProfileJson profileJson = null;
            try {
                profileJson = new ProfileJson(new JSONObject(jsonBlob));
                String displayName = profileJson.getDisplayName();
                String email = profileJson.getEmail();

                if (!TextUtils.isEmpty(email)) {
                    Prefs.getInstance(context).setEmail(email);
                    setFxALoginTitle(getPrefs().getBearerToken(), getPrefs().getEmail());
                }

                if (!TextUtils.isEmpty(displayName)) {
                    Prefs.getInstance(context).setNickname(displayName);
                    setNicknamePreferenceTitle(displayName);
                }


            } catch (JSONException e) {
                Log.e(LOG_TAG, "Error fetching FxA profile", e);
            }
        }
    };

    private void fetchFxaProfile(String bearerToken) {
        RetrieveProfileTask task = new RetrieveProfileTask(getApplicationContext(),
                BuildConfig.FXA_PROFILE_SERVER);
        task.execute(bearerToken);
    }


    private ClientPrefs getPrefs() {
        return ClientPrefs.getInstance(this);
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.layout.stumbler_preferences);

        mNicknamePreference = (EditTextPreference) getPreferenceManager().findPreference(Prefs.NICKNAME_PREF);

        mFxaLoginPreference = getPreferenceManager().findPreference(Prefs.FXA_LOGIN_PREF);
        setFxALoginTitle(getPrefs().getBearerToken(), getPrefs().getEmail());
        setNicknamePreferenceTitle(getPrefs().getNickname());

        mWifiPreference = (CheckBoxPreference) getPreferenceManager().findPreference(Prefs.WIFI_ONLY);
        mKeepScreenOn = (CheckBoxPreference) getPreferenceManager().findPreference(ClientPrefs.KEEP_SCREEN_ON_PREF);
        mEnableShowMLSLocations = (CheckBoxPreference) getPreferenceManager().findPreference(ClientPrefs.ENABLE_OPTION_TO_SHOW_MLS_ON_MAP);
        mCrashReportsOn = (CheckBoxPreference) getPreferenceManager().findPreference(ClientPrefs.CRASH_REPORTING);
        mMapTileDetail = (ListPreference) getPreferenceManager().findPreference(ClientPrefs.MAP_TILE_RESOLUTION_TYPE);
        int valueIndex = ClientPrefs.getInstance(this).getMapTileResolutionType().ordinal();
        mMapTileDetail.setValueIndex(valueIndex);
        updateMapDetailTitle(valueIndex);

        setPreferenceListener();
        setButtonListeners();
        registerFxaListeners();
        verifyBearerToken();
    }

    private void verifyBearerToken() {
        if (!hasNetworkForFxA()) {
            return;
        }
        String bearerToken = getPrefs().getBearerToken();
        if (!TextUtils.isEmpty(bearerToken)) {
            VerifyOAuthTask task = new VerifyOAuthTask(getApplicationContext(),
                    BuildConfig.FXA_OAUTH2_SERVER);
            task.execute(bearerToken);
        }
    }

    private void registerFxaListeners() {
        IntentFilter intentFilter = new IntentFilter();
        Intents.registerFxaIntents(intentFilter);

        LocalBroadcastManager
                .getInstance(getApplicationContext())
                .registerReceiver(fxaCallbackReceiver, intentFilter);
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

        mNicknamePreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (!hasNetworkForFxA()) {
                    return false;
                }

                SetDisplayNameTask task = new SetDisplayNameTask(getApplicationContext(),
                        BuildConfig.FXA_PROFILE_SERVER);
                task.execute(getPrefs().getBearerToken(), newValue.toString());
                return true;
            }
        });


        mFxaLoginPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                NetworkInfo netInfo = new NetworkInfo(PreferencesScreen.this);
                if (!netInfo.isConnected()) {
                    Toast.makeText(getApplicationContext(),
                            getApplicationContext().getString(R.string.fxa_needs_network),
                            Toast.LENGTH_SHORT).show();
                    return false;
                }


                String bearerToken = getPrefs().getBearerToken();
                if (!TextUtils.isEmpty(bearerToken)) {
                    AlertDialog.Builder myAlertDialog = new AlertDialog.Builder(PreferencesScreen.this);
                    myAlertDialog.setTitle(getString(R.string.fxaPromptLogout));
                    myAlertDialog.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface arg0, int arg1) {
                            String bearerToken = getPrefs().getBearerToken();
                            DestroyOAuthTask task = new DestroyOAuthTask(getApplicationContext(),
                                    BuildConfig.FXA_OAUTH2_SERVER);
                            task.execute(bearerToken);
                        }
                    });
                    myAlertDialog.setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener() {

                        public void onClick(DialogInterface arg0, int arg1) {
                            // do nothing on cancel
                        }});
                    myAlertDialog.show();
                    return true;
                }

                String app_name = getResources().getString(R.string.app_name);
                FxAGlobals.initFxaLogin(PreferencesScreen.this, app_name);

                // These secrets are provisioned from the FxA dashboard
                String FXA_APP_KEY = BuildConfig.FXA_APP_KEY;

                // And finally the callback endpoint on our web application
                // Example server endpoint code is available under the `sample_endpoint` subdirectory.
                String FXA_APP_CALLBACK = BuildConfig.FXA_APP_CALLBACK;

                CookieSyncManager cookies = CookieSyncManager.createInstance(PreferencesScreen.this);
                CookieManager.getInstance().removeAllCookie();
                CookieManager.getInstance().removeSessionCookie();
                cookies.sync();

                // Only untrusted scopes can go here for now.
                // If you add an scope that is not on that list, the login screen will hang instead
                // of going to the final redirect.  No user visible error occurs. This is terrible.
                // https://github.com/mozilla/fxa-content-server/issues/2508
                String[] scopes = new String[] {"profile:email",
                        "profile:display_name",
                        "profile:display_name:write"};

                new OAuthDialog(PreferencesScreen.this,
                        BuildConfig.FXA_SIGNIN_URL,
                        FXA_APP_CALLBACK,
                        scopes,
                        FXA_APP_KEY).show();
                return true;
            }
        });


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
                getPrefs().setOptionEnabledToShowMLSOnMap(newValue.equals(true));
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

//        bug #1476
//        mLimitMapZoom.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
//            @Override
//            public boolean onPreferenceChange(Preference preference, Object newValue) {
//                getPrefs().setIsMapZoomLimited(newValue.equals(true));
//                return true;
//            }
//        });
    }

    private boolean hasNetworkForFxA() {
        boolean useWifiOnly = ClientPrefs.getInstance(PreferencesScreen.this).getUseWifiOnly();
        NetworkInfo netInfo = new NetworkInfo(PreferencesScreen.this);

        // Short circuit if we're restricted to wifi-only and have no wifi,
        // or if we just have no network connection.
        if ((useWifiOnly && !netInfo.isWifiAvailable()) || !netInfo.isConnected()) {
            return false;
        }
        return true;
    }

    private void clearFxaLoginState() {
        getPrefs().setBearerToken("");
        getPrefs().setEmail("");
        getPrefs().setNickname("");
        setFxALoginTitle("", "");
        setNicknamePreferenceTitle("");
        Toast.makeText(getApplicationContext(),
                getApplicationContext().getString(R.string.fxa_is_logged_out),
                Toast.LENGTH_LONG).show();
    }

    private void setFxALoginTitle(String bearerToken, String email) {
        if (TextUtils.isEmpty(email)) {
            email = "";
        }
        if (!TextUtils.isEmpty(bearerToken)) {
            mFxaLoginPreference.setTitle(getString(R.string.fxa_accounts));
            mFxaLoginPreference.setSummary(getString(R.string.fxaDescriptionLoggedIn) + ":\n" + email);
            mNicknamePreference.setEnabled(true);
        } else {
            mFxaLoginPreference.setTitle(getString(R.string.fxaLoginTitle));
            mFxaLoginPreference.setSummary(getString(R.string.fxaDescription));
            mNicknamePreference.setEnabled(false);
        }
    }

    private void setNicknamePreferenceTitle(String displayName) {
        if (!TextUtils.isEmpty(displayName)) {
            String title = String.format(getString(R.string.enter_nickname_title), displayName);
            mNicknamePreference.setTitle(title);
        } else {
            mNicknamePreference.setTitle(R.string.enter_nickname);
        }
    }


}
