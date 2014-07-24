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
import android.database.Cursor;
import android.os.SystemClock;
import android.provider.BaseColumns;
import android.util.Log;

import org.mozilla.mozstumbler.service.SharedConstants;
import org.mozilla.mozstumbler.service.datahandling.ContentResolverInterface;
import org.mozilla.mozstumbler.service.datahandling.DatabaseContract;
import org.mozilla.mozstumbler.service.datahandling.ServerContentResolver;
import org.mozilla.mozstumbler.service.utils.DateTimeUtils;
import org.mozilla.mozstumbler.service.utils.NetworkUtils;

public class UploadAlarmReceiver extends BroadcastReceiver {
    static final String LOG_TAG = UploadAlarmReceiver.class.getName();
    static final long INTERVAL_MS = 5000;
    static final int MAX_STALENESS_IN_WEEKS = 2;

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
            if (SharedConstants.stumblerContentResolver == null) {
                SharedConstants.stumblerContentResolver = new ServerContentResolver(this, null);
            }
            upload();
        }

        void upload() {
            // Defensive approach: grab the first row, check the time, if it is too old, delete the whole table
            String[] projection = { DatabaseContract.Reports.TIME };
            Cursor cursor = SharedConstants.stumblerContentResolver.query(DatabaseContract.Reports.CONTENT_URI,
                    projection, null, null, BaseColumns._ID + " limit 1");
            try {
                if (cursor == null || cursor.getCount() < 1) {
                    // DB is empty
                    Log.d(LOG_TAG, "upload completed, stopping alarm.");
                    UploadAlarmReceiver.cancelAlarm(this);
                    return;
                }

                cursor.moveToFirst();
                long time = cursor.getLong(cursor.getColumnIndex(DatabaseContract.Reports.TIME));
                cursor.close();
                if (time > 0) {
                    long currentTime = System.currentTimeMillis();
                    long msPerWeek = 604800 * 1000;
                    if (currentTime - time > MAX_STALENESS_IN_WEEKS * msPerWeek) {
                        SharedConstants.stumblerContentResolver.delete(DatabaseContract.Reports.CONTENT_URI, null, null);
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
            } finally {
                if (cursor != null && !cursor.isClosed())
                    cursor.close();
            }
        }
    }

    static PendingIntent createIntent(Context c) {
        PendingIntent pi = PendingIntent.getBroadcast(c, 0, new Intent(c, UploadAlarmReceiver.class), 0);
        return pi;
    }

    public static void cancelAlarm(Context c) {
        AlarmManager alarmManager = (AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = createIntent(c);
        alarmManager.cancel(pi);
    }

    public static void scheduleAlarm(Context c) {
        cancelAlarm(c);

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

