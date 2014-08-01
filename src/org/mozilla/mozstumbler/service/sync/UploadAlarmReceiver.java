/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.sync;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.datahandling.DataStorageManager;
import org.mozilla.mozstumbler.service.utils.NetworkUtils;

public class UploadAlarmReceiver extends BroadcastReceiver {
    static final String LOG_TAG = UploadAlarmReceiver.class.getName();

    // Only if data is queued and device awake: periodically check network availability and upload
    // TODO Fennec will only use this as a secondary mechanism. The primary Fennec method
    // notifying this class when a good time is to try upload.
    static final long INTERVAL_MS = 1000 * 60 * 5;

    public static boolean sIsAlreadyScheduled;

    public UploadAlarmReceiver() {}

    public static class UploadAlarmService extends IntentService {

        public UploadAlarmService(String name) {
            super(name);
        }

        public UploadAlarmService() {
            super(LOG_TAG);
        }

        @Override
        protected void onHandleIntent(Intent intent) {
            if (AppGlobals.dataStorageManager == null) {
                AppGlobals.dataStorageManager = new DataStorageManager(this, null);
            }
            upload();
        }

        void upload() {
            // Defensive approach:  if it is too old, delete all data
            long oldestMs = AppGlobals.dataStorageManager.getOldestBatchTimeMs();
            int maxWeeks = AppGlobals.dataStorageManager.getMaxWeeksStored();
            if (oldestMs > 0) {
                long currentTime = System.currentTimeMillis();
                long msPerWeek = 604800 * 1000;
                if (currentTime - oldestMs > maxWeeks * msPerWeek) {
                    AppGlobals.dataStorageManager.deleteAll();
                    UploadAlarmReceiver.cancelAlarm(this);
                    return;
                }
            }

            if (NetworkUtils.getInstance().isWifiAvailable()) {
                Log.d(LOG_TAG, "Alarm upload(), call AsyncUploader");
                AsyncUploader uploader = new AsyncUploader(null);
                uploader.execute();
                // we could listen for completion and cancel, instead, cancel on next alarm when db empty
            }
        }
    }

    static PendingIntent createIntent(Context c) {
        PendingIntent pi = PendingIntent.getBroadcast(c, 0, new Intent(c, UploadAlarmReceiver.class), 0);
        return pi;
    }

    public static void cancelAlarm(Context c) {
        Log.d(LOG_TAG, "cancelAlarm");
        // this is to stop scheduleAlarm from constantly rescheduling, not to guard cancellation.
        sIsAlreadyScheduled = false;
        AlarmManager alarmManager = (AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = createIntent(c);
        alarmManager.cancel(pi);
    }

    public static void scheduleAlarm(Context c) {
        if (sIsAlreadyScheduled)
            return;

        Log.d(LOG_TAG, "schedule alarm (ms):" + INTERVAL_MS);

        sIsAlreadyScheduled = true;
        AlarmManager alarmManager = (AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = createIntent(c);

        long triggerAtMs = System.currentTimeMillis() + INTERVAL_MS;
        alarmManager.setInexactRepeating(AlarmManager.RTC, triggerAtMs, INTERVAL_MS, pi);
    }

    @Override
    public void onReceive(final Context context, Intent intent) {
        Intent startServiceIntent = new Intent(context, UploadAlarmService.class);
        context.startService(startServiceIntent);
    }
}

