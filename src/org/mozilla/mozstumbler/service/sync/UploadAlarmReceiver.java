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
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.datahandling.DataStorageManager;
import org.mozilla.mozstumbler.service.utils.NetworkUtils;

// Only if data is queued and device awake: periodically check network availability and upload
// TODO Fennec will only use this as a secondary mechanism. The primary Fennec method
// notifying this class when a good time is to try upload.
public class UploadAlarmReceiver extends BroadcastReceiver {
    static final String LOG_TAG = UploadAlarmReceiver.class.getSimpleName();

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
            if (DataStorageManager.getInstance() == null) {
                DataStorageManager.createGlobalInstance(this, null);
            }
            upload();
        }

        void upload() {
            // Defensive approach:  if it is too old, delete all data
            long oldestMs = DataStorageManager.getInstance().getOldestBatchTimeMs();
            int maxWeeks = DataStorageManager.getInstance().getMaxWeeksStored();
            if (oldestMs > 0) {
                long currentTime = System.currentTimeMillis();
                long msPerWeek = 604800 * 1000;
                if (currentTime - oldestMs > maxWeeks * msPerWeek) {
                    DataStorageManager.getInstance().deleteAll();
                    UploadAlarmReceiver.cancelAlarm(this);
                    return;
                }
            }

            if (NetworkUtils.getInstance().isWifiAvailable()) {
                Log.d(LOG_TAG, "Alarm upload(), call AsyncUploader");
                AsyncUploader.UploadSettings settings =
                        new AsyncUploader.UploadSettings(Prefs.getInstance().getWifiScanAlways(), Prefs.getInstance().getUseWifiOnly());
                AsyncUploader uploader = new AsyncUploader(settings, null);
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

    public static void scheduleAlarm(Context c, long secondsToWait, boolean isRepeating) {
        if (sIsAlreadyScheduled) {
            return;
        }

        long intervalMsec = secondsToWait * 1000;
        Log.d(LOG_TAG, "schedule alarm (ms):" + intervalMsec);

        sIsAlreadyScheduled = true;
        AlarmManager alarmManager = (AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = createIntent(c);

        long triggerAtMs = System.currentTimeMillis() + intervalMsec;
        if (isRepeating) {
            alarmManager.setInexactRepeating(AlarmManager.RTC, triggerAtMs, intervalMsec, pi);
        } else {
            alarmManager.set(AlarmManager.RTC, triggerAtMs, pi);
        }
    }

    @Override
    public void onReceive(final Context context, Intent intent) {
        Intent startServiceIntent = new Intent(context, UploadAlarmService.class);
        context.startService(startServiceIntent);
    }
}