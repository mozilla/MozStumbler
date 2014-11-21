/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.mozstumbler.client.subactivities;

import android.app.AlertDialog;
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
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.ClientPrefs;
import org.mozilla.mozstumbler.client.MainApp;
import org.mozilla.mozstumbler.client.serialize.KMLFragment;
import org.mozilla.mozstumbler.service.AppGlobals;
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

            final String[] distanceArray = {"30 m", "50 m", "75 m", "100 m", "125 m", "150 m", "175 m", "200 m"};
            final ArrayAdapter<String> distanceAdapter =
                    new ArrayAdapter<String>(this.getActivity(), android.R.layout.simple_spinner_item, distanceArray);
            final Spinner distanceSpinner = (Spinner) mRootView.findViewById(R.id.spinnerMotionDetectionDistanceMeters);
            distanceSpinner.setAdapter(distanceAdapter);
            final int dist = ClientPrefs.getInstance().getMotionChangeDistanceMeters();
            distanceSpinner.setSelection(findIndexOf(dist, distanceArray));

            distanceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View arg1, int position, long id) {
                    changeOfMotionDetectionDistanceOrTime(parent, position, IsDistanceOrTime.DISTANCE);
                }

                @Override
                public void onNothingSelected(AdapterView<?> arg0) {}
            });

            final String[] timeArray = {"30 s", "60 s", "90 s", "120 s", "180 s", "210 s", "240 s", "270 s", "300 s"};
            final ArrayAdapter<String> timeAdapter =
                    new ArrayAdapter<String>(this.getActivity(), android.R.layout.simple_spinner_item, timeArray);
            final Spinner timeSpinner = (Spinner) mRootView.findViewById(R.id.spinnerMotionDetectionTimeSeconds);
            timeSpinner.setAdapter(timeAdapter);
            final int time = ClientPrefs.getInstance().getMotionChangeTimeWindowSeconds();
            timeSpinner.setSelection(findIndexOf(time, timeArray));

            timeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View arg1, int position, long id) {
                    changeOfMotionDetectionDistanceOrTime(parent, position, IsDistanceOrTime.TIME);
                }

                @Override
                public void onNothingSelected(AdapterView<?> arg0) {}
            });

            return mRootView;
        }

        private enum IsDistanceOrTime { DISTANCE, TIME }
        private void changeOfMotionDetectionDistanceOrTime(AdapterView<?> parent, int position, IsDistanceOrTime isDistanceOrTime) {
            String item = parent.getItemAtPosition(position).toString();
            int val = Integer.valueOf(item.substring(0, item.indexOf(" ")));
            ClientPrefs prefs = ClientPrefs.createGlobalInstance(getActivity().getApplicationContext());
            if (isDistanceOrTime == IsDistanceOrTime.DISTANCE) {
                prefs.setMotionChangeDistanceMeters(val);
            } else {
                prefs.setMotionChangeTimeWindowSeconds(val);
            }
            MainApp mainApp = ((MainApp)getActivity().getApplication());
            mainApp.stopScanning();
            mainApp.startScanning();
        }

        private int findIndexOf(int needle, String[] haystack) {
            int i = 0;
            for (String item : haystack) {
                int val = Integer.valueOf(item.substring(0, item.indexOf(" ")));
                if (val == needle) {
                    return i;
                }
                i++;
            }
            return 0;
        }
    }

}
