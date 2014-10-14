/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.mozstumbler.client.subactivities;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Spinner;
import org.acra.ACRA;
import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.ClientPrefs;
import org.mozilla.mozstumbler.client.serialize.KMLFragment;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.core.logging.Log;
import org.mozilla.mozstumbler.service.core.logging.MockAcraLog;

public class DeveloperActivity extends ActionBarActivity {

    private final String LOG_TAG = AppGlobals.LOG_PREFIX + DeveloperActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_developer);
        if (savedInstanceState == null) {
            FragmentManager fm = getSupportFragmentManager();
            FragmentTransaction ft =fm.beginTransaction();
            ft.add(R.id.frame1, new KMLFragment());
            ft.add(R.id.frame2, new DeveloperOptions());
            ft.commit();
        }

    }

    // For misc developer options
    private static class DeveloperOptions extends Fragment {
        private final String LOG_TAG = AppGlobals.LOG_PREFIX + DeveloperOptions.class.getSimpleName();

        private View mRootView;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            mRootView = inflater.inflate(R.layout.fragment_developer_options, container, false);

            boolean crashEnabled = ClientPrefs.getInstance().isCrashReportingEnabled();
            CheckBox button = (CheckBox) mRootView.findViewById(R.id.toggleCrashReports);
            button.setChecked(crashEnabled);
            button.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    onToggleCrashReportClicked(isChecked);
                }
            });

            final Spinner spinner = (Spinner) mRootView.findViewById(R.id.spinnerMapResolutionOptions);
            spinner.setSelection(ClientPrefs.getInstance().getMapTileResolutionType().ordinal());
            spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long id) {
                    String item = spinner.getSelectedItem().toString();
                    Log.d(LOG_TAG, item + ", pos:" + position);
                    ClientPrefs prefs = (ClientPrefs) ClientPrefs.createGlobalInstance(getActivity().getApplicationContext());
                    prefs.setMapTileResolutionType(position);
                }

                @Override
                public void onNothingSelected(AdapterView<?> arg0) {
                }

            });
            return mRootView;
        }

        private void onToggleCrashReportClicked(boolean isOn) {
            ClientPrefs.getInstance().setCrashReportingEnabled(isOn);

            if (isOn) {
                Log.d(LOG_TAG, "Enabled crash reporting");
                ACRA.setLog(MockAcraLog.getOriginalLog());
            } else {
                Log.d(LOG_TAG, "Disabled crash reporting");
                ACRA.setLog(new MockAcraLog());
            }
        }
    }
}
