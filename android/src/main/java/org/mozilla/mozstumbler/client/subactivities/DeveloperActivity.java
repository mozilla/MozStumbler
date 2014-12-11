/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.mozstumbler.client.subactivities;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;

import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.ClientPrefs;
import org.mozilla.mozstumbler.client.MainApp;
import org.mozilla.mozstumbler.client.serialize.KMLFragment;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.core.logging.Log;
import org.mozilla.mozstumbler.service.stumblerthread.motiondetection.LocationChangeSensor;
import org.mozilla.mozstumbler.service.stumblerthread.motiondetection.MotionSensor;
import org.mozilla.mozstumbler.service.utils.BatteryCheckReceiver;

import java.io.File;

import static org.mozilla.mozstumbler.service.stumblerthread.datahandling.ClientDataStorageManager.sdcardArchivePath;

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
                final AlertDialog.Builder b = new AlertDialog.Builder(DeveloperActivity.this);
                final String[] menuList = { "ACRA Crash Test",
                        "Fake no motion", "Fake motion", "Battery Low", "Battery OK"};
                b.setTitle("Secret testing.. shhh.");
                b.setItems(menuList,new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int item) {
                    switch (item) {
                        case 0:
                            Object a = null;
                            a.hashCode();
                            break;
                        case 1:
                            LocationChangeSensor.debugSendLocationUnchanging();
                            break;
                        case 2:
                            MotionSensor.debugMotionDetected();
                            break;
                        case 3:
                            int pct = ClientPrefs.getInstance().getMinBatteryPercent();
                            BatteryCheckReceiver.debugSendBattery(pct - 1);
                            break;
                        case 4:
                            BatteryCheckReceiver.debugSendBattery(99);
                            break;

                    }
                    }
                });
                b.create().show();
                return true;
            }
        });
    }

    // For misc developer options
    public static class DeveloperOptions extends Fragment {
        private final String LOG_TAG = AppGlobals.makeLogTag(DeveloperOptions.class.getSimpleName());

        private View mRootView;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) 
        {
            mRootView = inflater.inflate(R.layout.fragment_developer_options, container, false);

            setupSaveJSONLogs();
            setupBatterySaver();
            return mRootView;
        }

        private void setupSaveJSONLogs() {
            boolean saveStumbleLogs = Prefs.getInstance().isSaveStumbleLogs();
            CheckBox button = (CheckBox) mRootView.findViewById(R.id.toggleSaveStumbleLogs);
            button.setChecked(saveStumbleLogs);
            button.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                    onToggleSaveStumbleLogs(isChecked);
                }
            });
        }

        private void setupBatterySaver() {
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
                public void onNothingSelected(AdapterView<?> arg0) {
                }
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
                public void onNothingSelected(AdapterView<?> arg0) {
                }
            });

            final String[] timeArray = {"5 s", "30 s", "60 s", "90 s", "120 s", "180 s", "210 s", "240 s", "270 s", "300 s"};
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
                public void onNothingSelected(AdapterView<?> arg0) {
                }
            });

        }
        private void onToggleSaveStumbleLogs(boolean isChecked) {
            if (isChecked) {
                Context viewCtx = mRootView.getContext();
                if (!archiveDirCreatedAndMounted(this.getActivity())) {

                    Toast.makeText(viewCtx,
                            viewCtx.getString(R.string.create_log_archive_failure),
                            Toast.LENGTH_SHORT).show();
                    isChecked = false;
                    CheckBox button = (CheckBox) mRootView.findViewById(R.id.toggleSaveStumbleLogs);
                    button.setChecked(isChecked);
                } else {
                    Toast.makeText(viewCtx,
                            viewCtx.getString(R.string.create_log_archive_success) +
                                    sdcardArchivePath(),
                            Toast.LENGTH_LONG).show();
                }
            }
            Prefs.getInstance().setSaveStumbleLogs(isChecked);
        }

        public boolean archiveDirCreatedAndMounted(Context ctx) {
            File saveDir = new File(sdcardArchivePath());
            String storageState = Environment.getExternalStorageState();

            // You have to check the mount state of the external storage.
            // Using the mkdirs() result isn't good enough.
            if(!storageState.equals(Environment.MEDIA_MOUNTED)) {
                return false;
            }

            saveDir.mkdirs();
            if (!saveDir.exists()) {
                return false;
            }
            Log.d(LOG_TAG, "Created: [" + saveDir.getAbsolutePath() + "]");
            return true;
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
            if (mainApp.isScanningOrPaused()) {
                mainApp.stopScanning();
                mainApp.startScanning();
            }
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
