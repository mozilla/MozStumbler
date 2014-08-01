/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Registered as a receiver in manifest. Starts the StumblerService in passive listening mode.
 * Using GPS_* event changes during development, switch to using the existing permissions for a
 * service on Fennec.
 <meta-data name="MOZILLA_API_KEY" value="aValue">
 <receiver android:name=".service.PassiveServiceReceiver">
 <intent-filter>
 <action android:name="android.location.GPS_ENABLED_CHANGE" />
 <action android:name="android.location.GPS_FIX_CHANGE" />
 </intent-filter>
 </receiver>
 */
public class PassiveServiceReceiver extends BroadcastReceiver {
    final static String LOG_TAG = PassiveServiceReceiver.class.getSimpleName();

    static boolean sIsStarted;

    @Override
    public void onReceive(Context context, Intent intent) {
        Prefs.createGlobalInstance(context);

        String action = intent.getAction();
        String mozApiKey = null;

        if (action.contains(".STUMBLER_PREF")) {
            AppGlobals.isDebug = intent.getBooleanExtra("is_debug", false);
            StumblerService.sFirefoxStumblingEnabled = intent.getBooleanExtra("enabled", false) ?
                    StumblerService.FirefoxStumbleState.ON :
                    StumblerService.FirefoxStumbleState.OFF;
            mozApiKey = intent.getStringExtra("moz_mozilla_api_key");
        }

        if (StumblerService.sFirefoxStumblingEnabled == StumblerService.FirefoxStumbleState.OFF) {
            context.stopService(new Intent(context, StumblerService.class));
            sIsStarted = false;
            return;
        }

        if (sIsStarted) {
            return;
        }
        sIsStarted = true;

        if (AppGlobals.isDebug) Log.d(LOG_TAG, "Starting Passively");

        Intent startServiceIntent = new Intent(context, StumblerService.class);
        startServiceIntent.putExtra(StumblerService.ACTION_START_PASSIVE, true);
        startServiceIntent.putExtra(StumblerService.ACTION_EXTRA_MOZ_API_KEY, mozApiKey);

        context.startService(startServiceIntent);
    }
}

