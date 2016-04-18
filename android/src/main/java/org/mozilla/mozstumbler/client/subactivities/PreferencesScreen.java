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
import org.mozilla.accounts.fxa.FxAGlobals;
import org.mozilla.accounts.fxa.IFxACallbacks;
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
import org.mozilla.mozstumbler.client.IMainActivity;
import org.mozilla.mozstumbler.client.MainApp;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.utils.NetworkInfo;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.log.ILogger;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

import java.util.Random;

public class PreferencesScreen extends PreferenceActivity implements IFxACallbacks{

    private static final String LOG_TAG = LoggerUtil.makeLogTag(PreferencesScreen.class);
    ILogger Log = (ILogger) ServiceLocator.getInstance().getService(ILogger.class);

    private EditTextPreference mNicknamePreference;

    private CheckBoxPreference mWifiPreference;
    private CheckBoxPreference mKeepScreenOn;
    private CheckBoxPreference mEnableShowMLSLocations;
    private CheckBoxPreference mCrashReportsOn;
    private CheckBoxPreference mUnlimitedMapZoom;
    private ListPreference mMapTileDetail;
    private Preference mFxaLoginPreference;

    private final BroadcastReceiver callbackReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(FetchFxaConfiguration.FXA_CONFIG_LOAD)) {
                processFxaConfigLoad(intent, true);
            } else if (intent.getAction().equals(FetchFxaConfiguration.FXA_CONFIG_LOAD_FAILURE)) {
                processFxaConfigLoad(intent, false);
            } else {
               Log.w(LOG_TAG, "Unexpected intent: " + intent.toString());
            }
        }
    };


    private void processFxaConfigLoad(Intent intent, boolean success) {
        if (!success) {
            Log.e(LOG_TAG, "No fxa configuration is available");
            return;
        }

        String configBlob  = intent.getStringExtra("json");
        if (TextUtils.isEmpty(configBlob)) {
            Log.w(LOG_TAG, "No fxa config blob was found in the intent");
            return;
        }

        Log.i(LOG_TAG, "Obtained configuration JSON: " + configBlob);

        try {
            JSONObject configJSON = new JSONObject(configBlob);

            // These secrets are provisioned from the FxA dashboard
            getPrefs().setFxaClientId(configJSON.getString("client_id"));
            getPrefs().setFxaProfileServer(configJSON.getString("profile_uri"));
            getPrefs().setFxaOauth2Server(configJSON.getString("oauth_uri"));
            getPrefs().setFxaScopes(configJSON.getString("scopes"));
            getPrefs().setFxaAppCallback(configJSON.getString("redirect_uri"));

            getPrefs().setLbBaseURI(configJSON.getString("leaderboard_base_uri"));
            getPrefs().setLbSubmitURL(configJSON.getString("leaderboard_base_uri") + "/api/v1/contributions/");

            enableLeaderboardMenuItem(true);
            // Only after all the FXA crap is setup can we initiate bearer token verification
            verifyBearerToken();

        } catch (JSONException e) {
            enableLeaderboardMenuItem(false);

            Log.e(LOG_TAG, "Can't parse JSON config blob", e);
            return;
        }


    }

    /*
     We enable the leaderboard button in either of two scenarios:
      a) the leaderboard FxA configuration was valid
      b) the user is signed into FxA

      Both of these state transitions indicate that FxA is 'mostly' live
      with respect to the leaderboard, so it should be ok to either start the signon
      process to FxA via leaderboard, or display the user's leaderboard page.
     */
    private void enableLeaderboardMenuItem(boolean b) {
        ClientPrefs clientPrefs = ClientPrefs.getInstance(this);
        clientPrefs.setFxaEnabled(b);
        IMainActivity mainActivity = ((MainApp) getApplication()).getMainActivity();
        if (mainActivity != null) {
            mainActivity.updateUiOnMainThread(false);
        }
    }

    private void fetchFxaProfile(String bearerToken) {
        RetrieveProfileTask task = new RetrieveProfileTask(getApplicationContext(),
                getFxaProfileServer());
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

        mUnlimitedMapZoom = (CheckBoxPreference) getPreferenceManager().findPreference(ClientPrefs.IS_MAP_ZOOM_UNLIMITED);

        setPreferenceListener();
        setButtonListeners();

        String app_name = getResources().getString(R.string.app_name);

        FxAGlobals fxa = new FxAGlobals();
        fxa.startIntentListening((Context) this, (IFxACallbacks) this, app_name);

        // Register callbacks so that we can load the FxA configuration JSON blob
        FetchFxaConfiguration.registerFxaIntents(this.getApplicationContext(), callbackReceiver);

        FetchFxaConfiguration fxaConfigTask = new FetchFxaConfiguration(this.getApplicationContext(),
                BuildConfig.LB_CONFIG_URL);
        fxaConfigTask.execute();

        // Kludge for 1.8.4 to clear out FxA logins if a token is detected, but no email.
        String bearerToken = getPrefs().getBearerToken();
        String fxaEmail = getPrefs().getEmail();
        if ((!TextUtils.isEmpty(bearerToken)) && (TextUtils.isEmpty(fxaEmail))) {
            clearFxaLoginState();
        }
    }

    private void verifyBearerToken() {
        if (!hasNetworkForFxA()) {
            return;
        }
        String bearerToken = getPrefs().getBearerToken();
        if (!TextUtils.isEmpty(bearerToken)) {
            VerifyOAuthTask task = new VerifyOAuthTask(getApplicationContext(),
                    getFxaOauth2Server());
            task.execute(bearerToken);
        }
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

                String newNickName = (String) newValue;
                if (TextUtils.isEmpty(newNickName)) {

                    AlertDialog.Builder builder = new AlertDialog.Builder(PreferencesScreen.this)
                            .setTitle("Forcing default display name")
                            .setPositiveButton(android.R.string.ok, null);
                    builder.create().show();

                    newNickName = defaultDisplayName();
                }

                setFxANickname(newNickName);
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
                                    getFxaOauth2Server());
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


                // And finally the callback endpoint on our web application
                // Example server endpoint code is available under the `sample_endpoint` subdirectory.
                CookieSyncManager cookies = CookieSyncManager.createInstance(PreferencesScreen.this);
                CookieManager.getInstance().removeAllCookie();
                CookieManager.getInstance().removeSessionCookie();
                cookies.sync();

                ClientPrefs prefs = ClientPrefs.getInstance(PreferencesScreen.this);

                if (prefs.isFxaEnabled()) {
                    new OAuthDialog(PreferencesScreen.this,
                            getFxaOauth2Server(),
                            getFxaAppCallback(),
                            getFxaScopes(),
                            getFxaClientId()).show();
                }
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
        getPrefs().setLbBaseURI("");
        getPrefs().setLbSubmitURL("");
        setFxALoginTitle("", "");
        getPrefs().setLeaderboardUID("");
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
            mFxaLoginPreference.setTitle(getString(R.string.fxa_settings_title));
            mFxaLoginPreference.setSummary(getString(R.string.fxaDescriptionLoggedIn) + ":\n" + email);
            mNicknamePreference.setEnabled(true);
            enableLeaderboardMenuItem(true);
        } else {
            mFxaLoginPreference.setTitle(getString(R.string.fxa_settings_title));
            mFxaLoginPreference.setSummary(getString(R.string.fxaDescription));
            mNicknamePreference.setEnabled(false);
        }
    }

    private void setNicknamePreferenceTitle(String displayName) {
        if (!TextUtils.isEmpty(displayName)) {
            mNicknamePreference.setEnabled(true);
            String title = String.format(getString(R.string.enter_nickname_title), displayName);
            mNicknamePreference.setTitle(title);
        } else {
            mNicknamePreference.setTitle(R.string.enter_nickname);
        }
    }


    // FxA Callbacks

    @Override
    public void processWebSvcAuthResponse(JSONObject authJSON) {
        String leaderboard_uid = authJSON.optString("leaderboard_uid", "");

        JSONObject fxa_auth_data = authJSON.optJSONObject("fxa_auth_data");

        if (fxa_auth_data == null) {
            clearFxaLoginState();
            return;
        }

        String bearerToken = null;
        try {
            bearerToken = fxa_auth_data.getString("access_token");
        } catch (JSONException e) {
            Log.e(LOG_TAG, "Expected to find an access token in the FxA auth data block.");
            clearFxaLoginState();
            return;
        }

        getPrefs().setBearerToken(bearerToken);
        fetchFxaProfile(bearerToken);

        if (!TextUtils.isEmpty(leaderboard_uid)) {
            getPrefs().setLeaderboardUID(leaderboard_uid);
            Log.i(LOG_TAG, "Saved leaderboard UID: " + leaderboard_uid);
        } else {
            Log.i(LOG_TAG, "Didn't save a leaderboard UID");

        }
    }

    @Override
    public void failCallback(String s) {
        if (s.equals(Intents.PROFILE_READ)) {
            getPrefs().setBearerToken("");
            clearFxaLoginState();
        }
        if (s.equals(Intents.OAUTH_DESTROY)) {
            // I don't care.  Clear the login state even if fxa logout 'fails'
            clearFxaLoginState();
        }
        if (s.equals(Intents.DISPLAY_NAME_WRITE)) {
            fetchFxaProfile(getPrefs().getBearerToken());
        }
        if (s.equals(Intents.OAUTH_VERIFY)) {
            clearFxaLoginState();
        }
    }

    @Override
    public void processProfileRead(JSONObject jsonObject) {
        ProfileJson profileJson = new ProfileJson(jsonObject);
        String displayName = profileJson.getDisplayName();
        String email = profileJson.getEmail();


        if (!TextUtils.isEmpty(email)) {
            Prefs.getInstance(this).setEmail(email);
            setFxALoginTitle(getPrefs().getBearerToken(), getPrefs().getEmail());
        }

        if (!TextUtils.isEmpty(displayName)) {
            Prefs.getInstance(this).setNickname(displayName);
            setNicknamePreferenceTitle(displayName);
        } else {
            // Empty displayName is the default for a newly created FxA account
            // Force the display name to a default value
            setFxANickname(defaultDisplayName());
        }
    }

    private void setFxANickname(String displayName) {
        SetDisplayNameTask task = new SetDisplayNameTask(getApplicationContext(),
                getFxaProfileServer());
        task.execute(getPrefs().getBearerToken(), displayName);
    }

    private String defaultDisplayName() {
        Random rand = new Random(System.currentTimeMillis() % 1000);
        return  "user_" + rand.nextInt(Integer.MAX_VALUE-1) + 1;
    }

    @Override
    public void processDisplayNameWrite() {
        // Fetch the profile to make sure we have the proper display name
        fetchFxaProfile(getPrefs().getBearerToken());
    }

    @Override
    public void processOauthDestroy() {
        clearFxaLoginState();
    }

    @Override
    public void processOauthVerify() {

    }

    @Override
    public void processRefreshToken(JSONObject jObj) {

    }

    private String getFxaAppCallback() {
        return getPrefs().getFxaAppCallback();
    }

    private String getFxaOauth2Server() {
        return getPrefs().getFxaOauth2Server();
    }

    public String[] getFxaScopes() {
        return TextUtils.split(getPrefs().getFxaScopes(), " ");
    }

    public String getFxaClientId() {
        return getPrefs().getFxaClientId();
    }

    public String getFxaProfileServer() {
        return getPrefs().getFxaProfileServer();
    }
}
