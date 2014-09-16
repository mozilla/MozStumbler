/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

import org.mozilla.mozstumbler.service.AppGlobals;

/* Test low power in adb with am broadcast -a android.intent.action.BATTERY_LOW
 * Test cancel button in notification list by swiping down on the entry for the
 * stumbler, and [X] Stop Scanning will appear.
 */
public final class TurnOffReceiver extends BroadcastReceiver {
    private static final String LOG_TAG = AppGlobals.LOG_PREFIX + TurnOffReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(MainActivity.ACTION_UI_PAUSE_SCANNING));
    }
}
