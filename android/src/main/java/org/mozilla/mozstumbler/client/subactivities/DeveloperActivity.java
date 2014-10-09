/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.mozstumbler.client.subactivities;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.widget.ToggleButton;

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
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new KMLFragment())
                    .commit();
        }

    }

    public void onToggleCrashReportClicked(View v) {
        boolean on = ((ToggleButton)v).isChecked();
        ClientPrefs.getInstance().setCrashReportingEnabled(on);

        if (on) {
            Log.d(LOG_TAG, "Enabled crash reporting");
            ACRA.setLog(MockAcraLog.getOriginalLog());
        } else {
            Log.d(LOG_TAG, "Disabled crash reporting");
            ACRA.setLog(new MockAcraLog());
        }
    }

}
