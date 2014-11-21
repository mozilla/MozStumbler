/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.mozstumbler.client.subactivities;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;

import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.ClientPrefs;
import org.mozilla.mozstumbler.client.serialize.KMLFragment;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.Prefs;

import static org.mozilla.mozstumbler.R.string;

public class DeveloperActivity extends ActionBarActivity {

    private final String LOG_TAG = AppGlobals.makeLogTag(DeveloperActivity.class.getSimpleName());

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

        TextView tv = (TextView) findViewById(R.id.textViewDeveloperTitle);
        tv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                new AlertDialog.Builder(DeveloperActivity.this)
                        .setTitle("ACRA test")
                        .setMessage("Force a crash?")
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                Object empty = null;
                                empty.hashCode();
                            }
                        })
                        .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {}
                        })
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show();
                return true;
            }
        });
    }

    // For misc developer options
    public static class DeveloperOptions extends Fragment {
        private final String LOG_TAG = AppGlobals.makeLogTag(DeveloperOptions.class.getSimpleName());

        private View mRootView;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            mRootView = inflater.inflate(R.layout.fragment_developer_options, container, false);

            // Setup for any logical group of config options should self contained in their
            // own methods.  This is mostly to help with merges in the event that multiple
            // source branches update the developer options.
            setupSimulationPreference();
            setupLowBatterySpinner();

            return mRootView;
        }

        private void setupLowBatterySpinner() {
            final Spinner batterySpinner = (Spinner) mRootView.findViewById(R.id.spinnerBatteryPercent);
            final SpinnerAdapter spinnerAdapter = batterySpinner.getAdapter();
            assert(spinnerAdapter instanceof ArrayAdapter);
            @SuppressWarnings("unchecked")
            final ArrayAdapter<String> adapter = (ArrayAdapter<String>)spinnerAdapter;
            final int percent = ClientPrefs.getInstance().getMinBatteryPercent();
            final int spinnerPosition = adapter.getPosition(percent + "%");
            batterySpinner.setSelection(spinnerPosition);

            batterySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View arg1, int position, long id) {
                    String item = parent.getItemAtPosition(position).toString().replace("%", "");
                    int percent = Integer.valueOf(item);
                    ClientPrefs prefs = ClientPrefs.createGlobalInstance(getActivity().getApplicationContext());
                    prefs.setMinBatteryPercent(percent);
                }

                @Override
                public void onNothingSelected(AdapterView<?> arg0) {}
            });
        }

        private void setupSimulationPreference() {
            boolean simulationEnabled = Prefs.getInstance().isSimulateStumble();
            final CheckBox simCheckBox = (CheckBox) mRootView.findViewById(R.id.toggleSimulation);
            final Button simResetBtn = (Button) mRootView.findViewById(R.id.buttonClearSimulationDefault);

            if (!AppGlobals.isDebug) {
                simCheckBox.setEnabled(false);
                simResetBtn.setEnabled(false);
                return;
            }

            simCheckBox.setChecked(simulationEnabled);
            simCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    onToggleSimulation(isChecked);
                }
            });

            simResetBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(android.view.View view) {
                    ClientPrefs cPrefs = ClientPrefs.getInstance();
                    cPrefs.clearSimulationStart();
                    Context btnCtx = simResetBtn.getContext();
                    Toast.makeText(btnCtx,
                            btnCtx.getText(R.string.reset_simulation_start),
                            Toast.LENGTH_SHORT).show();
                }
            });

        }

        private void onToggleSimulation(boolean isChecked) {
            Prefs.getInstance().setSimulateStumble(isChecked);
        }

    }
}
