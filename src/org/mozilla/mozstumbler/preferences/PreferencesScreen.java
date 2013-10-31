package org.mozilla.mozstumbler.preferences;

import org.mozilla.mozstumbler.R;

import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;

public class PreferencesScreen extends PreferenceActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getFragmentManager().beginTransaction().replace(android.R.id.content, new MyPreferenceFragment()).commit();        
    }
    
    public static class MyPreferenceFragment extends PreferenceFragment
    {
        private EditTextPreference mNicknamePreference;
        private Prefs mPrefs;

        @Override
        public void onCreate(final Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);
            mNicknamePreference = (EditTextPreference) getPreferenceManager().findPreference("nickname");
            mPrefs = new Prefs(getActivity().getApplicationContext());

            String nickname = mPrefs.getNickname();
            setNicknamePreferenceTitle(nickname);
            setNicknameListener();
        }

        public void setNicknameListener() {
            mNicknamePreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if ((newValue instanceof String) == false) {
                        return false;
                    }
                    String input = (String) newValue;
                    String nickname = input.trim();
                    mPrefs.setNickname(nickname);
                    setNicknamePreferenceTitle(nickname);
                    return true;
                }
            });
        }

        public void setNicknamePreferenceTitle(String nickname) {
            if (nickname != null && nickname.length()>0) {
                mNicknamePreference.setTitle(getString(R.string.enter_nickname_title) + " " + nickname);
            } else {
                mNicknamePreference.setTitle(getString(R.string.enter_nickname));
            }
        }
    }    

}
