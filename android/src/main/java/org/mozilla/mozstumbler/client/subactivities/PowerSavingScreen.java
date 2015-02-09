package org.mozilla.mozstumbler.client.subactivities;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.ClientPrefs;
import org.mozilla.mozstumbler.client.MainApp;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.Prefs;

public class PowerSavingScreen extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_power_saving_screen);

        setupMotionDetectionCheckbox();
        setupLowBatterySpinner();
        setupSensorRadio();
    }

    private void setupSensorRadio() {
        RadioGroup radioGroupSensorType = (RadioGroup) findViewById(R.id.radioGroupSensorType);
        if (ClientPrefs.getInstance(this).getIsMotionSensorTypeSignificant()) {
            radioGroupSensorType.check(R.id.radioSignificant);
        }

        radioGroupSensorType.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                boolean isAccelerometer = (checkedId == R.id.radioAccelerometer);
                ClientPrefs.getInstance(PowerSavingScreen.this).setIsMotionSensorTypeSignificant(!isAccelerometer);
            }
        });

        String msg;
        if (AppGlobals.hasSignificantMotionSensor) {
            msg = getResources().getString(R.string.sensor_type_significant_motion);
        } else {
            msg = getResources().getString(R.string.sensor_type_legacy);
        }
        TextView infoMessage = (TextView) findViewById(R.id.message_sensor_type);
        infoMessage.setText(msg);

    }

    private void motionDetectionOptionsEnable(boolean on) {
        findViewById(R.id.titleUseSensor).setEnabled(on);
        findViewById(R.id.radioAccelerometer).setEnabled(on);
        findViewById(R.id.radioSignificant).setEnabled(on && AppGlobals.hasSignificantMotionSensor);
    }

    private void setupMotionDetectionCheckbox() {
        boolean isOn = ClientPrefs.getInstance(this).getIsMotionSensorEnabled();
        CheckBox motionDetectionCheckbox = (CheckBox) findViewById(R.id.checkbox_motion_detection);
        motionDetectionCheckbox.setChecked(isOn);
        motionDetectionOptionsEnable(isOn);
        motionDetectionCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                ClientPrefs.getInstance(PowerSavingScreen.this).setIsMotionSensorEnabled(isChecked);
                final MainApp app = ((MainApp) getApplication());
                if (app.isScanningOrPaused()) {
                    app.stopScanning();
                    app.startScanning();
                }
                motionDetectionOptionsEnable(isChecked);
            }
        });
    }



    private void setupLowBatterySpinner() {
        Spinner batteryLevelSpinner = (Spinner) findViewById(R.id.spinnerBatteryPercent);
        final SpinnerAdapter spinnerAdapter = batteryLevelSpinner.getAdapter();
        assert (spinnerAdapter instanceof ArrayAdapter);
        @SuppressWarnings("unchecked")
        final ArrayAdapter<String> adapter = (ArrayAdapter<String>) spinnerAdapter;
        final int percent = ClientPrefs.getInstance(this).getMinBatteryPercent();
        final int spinnerPosition = adapter.getPosition(percent + "%");
        batteryLevelSpinner.setSelection(spinnerPosition);

        batteryLevelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
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