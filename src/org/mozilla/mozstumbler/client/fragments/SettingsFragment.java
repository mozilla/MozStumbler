package org.mozilla.mozstumbler.client.fragments;

import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.view.View;

import org.mozilla.mozstumbler.R;

/**
 * Created by JeremyChiang on 2014-08-05.
 */
public class SettingsFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.pref_settings);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.setBackgroundColor(getResources().getColor(android.R.color.white));
    }
}
