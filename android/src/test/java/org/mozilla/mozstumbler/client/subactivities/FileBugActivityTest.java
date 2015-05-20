/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.subactivities;


import android.os.Environment;
import android.widget.Button;
import android.widget.EditText;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowEnvironment;

import static junit.framework.Assert.assertEquals;

@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
public class FileBugActivityTest {

    private static final String LOG_TAG = LoggerUtil.makeLogTag(FileBugActivityTest.class);


    @Before
    public void setup() {
        // This is really dumb.  robolectric doesn't automatically reset the state
        // of preferences.
        Prefs.getInstanceWithoutContext().setSaveStumbleLogs(false);
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED);
    }



    @Test
    public void testEmailFieldIsRequired() {
        FileBugActivity activity = Robolectric.buildActivity(FileBugActivity.class).create().start().visible().get();

        Button pressMeButton = (Button) activity.findViewById(R.id.bug_report_button);
        Robolectric.clickOn(pressMeButton);

        EditText emailField = (EditText) activity.findViewById(R.id.bug_report_sender_email);
        CharSequence err = emailField.getError();
        String missing_field_text = Robolectric.application.getString(R.string.file_bug_missing_field);
        assertEquals(missing_field_text, err.toString());
    }
}
