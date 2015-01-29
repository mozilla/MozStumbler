package org.mozilla.mozstumbler.client.subactivities;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.ClientPrefs;
import org.mozilla.mozstumbler.client.MainApp;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.Prefs;

public class PowerSavingScreen extends ActionBarActivity {

    private Spinner mBatteryLevelSpinner;
    private CheckBox mMotionDetectionCheckbox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_power_saving_screen);

        mBatteryLevelSpinner = (Spinner) findViewById(R.id.spinnerBatteryPercent);
        mMotionDetectionCheckbox = (CheckBox) findViewById(R.id.checkbox_motion_detection);

        setupMotionDetectionCheckbox();

        setupLowBatterySpinner();

        String msg;
        if (AppGlobals.hasSignificantMotionSensor) {
            msg = getResources().getString(R.string.sensor_type_significant_motion);
        } else {
            msg = getResources().getString(R.string.sensor_type_legacy);
        }
        TextView infoMessage = (TextView) findViewById(R.id.message_sensor_type);
        infoMessage.setText(msg);
    }

    private void setupMotionDetectionCheckbox() {
        boolean isOn = ClientPrefs.getInstance(this).getPowerSavingMode() != Prefs.PowerSavingModeOptions.Off;

        mMotionDetectionCheckbox.setChecked(isOn);
        mMotionDetectionCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Prefs.PowerSavingModeOptions isOn = (isChecked) ? Prefs.PowerSavingModeOptions.On :
                        Prefs.PowerSavingModeOptions.Off;
                ClientPrefs.getInstance(PowerSavingScreen.this).setPowerSavingMode(isOn);
                final MainApp app = ((MainApp) getApplication());
                if (app.isScanningOrPaused()) {
                    app.stopScanning();
                    app.startScanning();
                }
            }
        });
    }

    private void setupLowBatterySpinner() {
        final SpinnerAdapter spinnerAdapter = mBatteryLevelSpinner.getAdapter();
        assert (spinnerAdapter instanceof ArrayAdapter);
        @SuppressWarnings("unchecked")
        final ArrayAdapter<String> adapter = (ArrayAdapter<String>) spinnerAdapter;
        final int percent = ClientPrefs.getInstance(this).getMinBatteryPercent();
        final int spinnerPosition = adapter.getPosition(percent + "%");
        mBatteryLevelSpinner.setSelection(spinnerPosition);

        mBatteryLevelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View arg1, int position, long id) {
                String item = parent.getItemAtPosition(position).toString().replace("%", "");
                int percent = Integer.valueOf(item);
                ClientPrefs.getInstance(PowerSavingScreen.this).setMinBatteryPercent(percent);
            }

            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });
    }
}