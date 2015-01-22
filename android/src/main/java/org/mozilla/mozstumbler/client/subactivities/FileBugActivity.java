/* This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.subactivities;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.acra.ACRA;
import org.mozilla.mozstumbler.R;

public class FileBugActivity extends ActionBarActivity {

    private static final String SENDER_NAME = "Sender Name";
    private static final String SENDER_EMAIL = "Sender Email";

    private static final String BUG_TITLE_FIELD = "Bug Title";
    private static final String BUG_TYPE_FIELD = "Bug Type";
    private static final String BUG_DESCRIPTION_FIELD = "Bug Description";
    private static final String BUG_REPRODUCTION_FIELD = "Bug Reproduction";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_bug);
    }

    @Override
    protected void onStart() {
        super.onStart();

        TextView textView = (TextView) findViewById(R.id.bug_report_info);
        String info = getString(R.string.file_bug_report_info);
        String crashreporting = getString(R.string.enable_crash_reporting);
        textView.setText(String.format(info, crashreporting));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return false;
    }

    public void sendReport(View button) {

        EditText senderName = (EditText) findViewById(R.id.bug_report_sender_name);
        EditText senderEmail = (EditText) findViewById(R.id.bug_report_sender_email);
        EditText bugName = (EditText) findViewById(R.id.bug_report_name);
        Spinner bugType = (Spinner) findViewById(R.id.bug_report_type);
        EditText bugDescription = (EditText) findViewById(R.id.bug_report_description);
        EditText bugReproduction = (EditText) findViewById(R.id.bug_report_reproduction);

        if (verifyInput(bugName, bugDescription, bugReproduction)) {

            // Set custom data fields
            ACRA.getErrorReporter().putCustomData(SENDER_NAME, senderName.getText().toString());
            ACRA.getErrorReporter().putCustomData(SENDER_EMAIL, senderEmail.getText().toString());
            ACRA.getErrorReporter().putCustomData(BUG_TITLE_FIELD, bugName.getText().toString());
            ACRA.getErrorReporter().putCustomData(BUG_TYPE_FIELD, bugType.getSelectedItem().toString());
            ACRA.getErrorReporter().putCustomData(BUG_DESCRIPTION_FIELD, bugDescription.getText().toString());
            ACRA.getErrorReporter().putCustomData(BUG_REPRODUCTION_FIELD, bugReproduction.getText().toString());

            // Send the report (Stack trace will say "Requested by Developer"
            // That should set apart manual reports from actual crashes
            ACRA.getErrorReporter().handleException(null);

            // Notify the user that the report was sent
            Toast toast = Toast.makeText(this, getResources().getString(R.string.file_bug_toast_success), Toast.LENGTH_LONG);
            toast.show();
            this.finish();
        }
    }

    // Making sure input isn't just blank spaces for required fields
    private boolean verifyInput(EditText... args) {
        boolean allValid = true;
        for (EditText field : args) {
            String input = field.getText().toString();
            if (input.trim().length() == 0) {
                field.setError(getResources().getString(R.string.file_bug_missing_field));
                allValid = false;
            }
        }
        return allValid;
    }

}

