/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.mozstumbler.client.subactivities;

import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;

import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.serialize.KMLFragment;

public class DeveloperActivity extends ActionBarActivity {

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
}
