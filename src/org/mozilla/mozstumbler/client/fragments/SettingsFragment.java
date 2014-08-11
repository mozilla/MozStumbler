package org.mozilla.mozstumbler.client.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
import android.text.TextUtils;
import android.view.View;

import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.MainActivity;
import org.mozilla.mozstumbler.client.models.User;
import org.mozilla.mozstumbler.service.Prefs;

/**
 * Created by JeremyChiang on 2014-08-05.
 */
public class SettingsFragment extends PreferenceFragment {

    public interface SettingSelectedListener {
        public void technicalDataSelected();
        public void aboutSelected();
    }

    private SettingSelectedListener settingSelectedListener;

    private EditTextPreference nicknamePreference;
    private SwitchPreference stumblerPowerPreference;
    private SwitchPreference wifiUploadOnlyPreference;
    private Preference sendFeedbackPreference;
    private Preference aboutStumblerPreference;

    private Preference technicalDataPreference;
    private Preference resetTodayScorePreference;
    private Preference resetOverallScorePreference;

    private Prefs sPrefs;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sPrefs = Prefs.getInstance();

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.pref_settings);

        setupNicknamePreference();
        setupStumblerPowerPreference();
        setupWifiUploadOnlyPreference();
        setupSendFeedbackPreference();
        setupAboutStumblerPreference();

        setupTechnicalDataPreference();
        setupResetTodayScorePreference();
        setupResetOverallScorePreference();
    }

    private void setupNicknamePreference() {
        nicknamePreference = (EditTextPreference)getPreferenceManager().findPreference(getString(R.string.pref_nickname_key));
        setNicknamePreferenceTitle(sPrefs.getNickname());
        nicknamePreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o) {
                sPrefs.setNickname(o.toString());
                setNicknamePreferenceTitle(o.toString());
                return true;
            }
        });
    }

    private void setNicknamePreferenceTitle(String nickname) {
        if (!TextUtils.isEmpty(nickname)) {
            nicknamePreference.setTitle(getString(R.string.enter_nickname_title) + " " + nickname);
        } else {
            nicknamePreference.setTitle(R.string.enter_nickname);
        }
    }

    private void setupStumblerPowerPreference() {
        stumblerPowerPreference = (SwitchPreference)getPreferenceManager().findPreference(getString(R.string.pref_stumbler_power_key));
        stumblerPowerPreference.setChecked(((MainActivity)getActivity()).isStumblerServiceOn());
        stumblerPowerPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o) {
                ((MainActivity)getActivity()).toggleStumblerServices();
                stumblerPowerPreference.setChecked(((MainActivity)getActivity()).isStumblerServiceOn());
                return true;
            }
        });
    }

    private void setupWifiUploadOnlyPreference() {
        wifiUploadOnlyPreference = (SwitchPreference)getPreferenceManager().findPreference(getString(R.string.pref_wifi_upload_only_key));
        wifiUploadOnlyPreference.setChecked(sPrefs.getUseWifiOnly());
        wifiUploadOnlyPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o) {
                sPrefs.setUseWifiOnly(o.equals(true));
                return true;
            }
        });
    }

    private void setupSendFeedbackPreference() {
        sendFeedbackPreference = getPreferenceManager().findPreference(getString(R.string.pref_send_feedback_key));
        sendFeedbackPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent sendEmailIntent = new Intent(Intent.ACTION_SENDTO);
                sendEmailIntent.setType("text/plain");
                sendEmailIntent.putExtra(Intent.EXTRA_EMAIL, new String[] { getString(R.string.send_feedback_address) });
                sendEmailIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.send_feedback_subject));
                sendEmailIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.send_feedback_text));
                sendEmailIntent.setClassName("com.google.android.gm", "com.google.android.gm.ComposeActivityGmail");

                startActivity(Intent.createChooser(sendEmailIntent, getString(R.string.send_feedback_title)));

                return true;
            }
        });
    }

    private void setupAboutStumblerPreference() {
        aboutStumblerPreference = getPreferenceManager().findPreference(getString(R.string.pref_about_stumbler_key));
        aboutStumblerPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (settingSelectedListener != null) {
                    settingSelectedListener.aboutSelected();
                    return true;
                }

                return false;
            }
        });
    }

    private void setupTechnicalDataPreference() {
        technicalDataPreference = getPreferenceManager().findPreference(getString(R.string.pref_technical_data_key));
        technicalDataPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (settingSelectedListener != null) {
                    settingSelectedListener.technicalDataSelected();
                    return true;
                }

                return false;
            }
        });
    }

    private void setupResetTodayScorePreference() {
        resetTodayScorePreference = getPreferenceManager().findPreference(getString(R.string.pref_reset_today_score_key));
        resetTodayScorePreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                User user = ((MainActivity)getActivity()).getUser();
                user.resetScoreForToday();

                return true;
            }
        });
    }

    private void setupResetOverallScorePreference() {
        resetOverallScorePreference = getPreferenceManager().findPreference(getString(R.string.pref_reset_overall_score_key));
        resetOverallScorePreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                User user = ((MainActivity)getActivity()).getUser();
                user.resetOverallScores();

                return true;
            }
        });
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.setBackgroundColor(getResources().getColor(android.R.color.white));
    }

    public void setSettingSelectedListener(SettingSelectedListener settingSelectedListener) {
        this.settingSelectedListener = settingSelectedListener;
    }
}
