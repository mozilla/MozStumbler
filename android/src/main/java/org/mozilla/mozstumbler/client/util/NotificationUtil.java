package org.mozilla.mozstumbler.client.util;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.v4.app.NotificationCompat;

import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.DateTimeUtils;
import org.mozilla.mozstumbler.client.MainApp;
import org.mozilla.mozstumbler.client.navdrawer.MainDrawerActivity;

public class NotificationUtil {
    private final Context mContext;
    public static final int NOTIFICATION_ID = 1;
    private static final long UPDATE_FREQUENCY = 60 * 1000;
    private static String sStopTitle;
    private static int sObservations, sCells, sWifis;
    private static long sUploadTime, sDisplayTime, sLastUpdateTime;
    private static boolean sIsPaused;

    public NotificationUtil(Context context) {
        mContext = context;
    }

    private Notification build() {
        PendingIntent turnOffIntent = PendingIntent.getBroadcast(mContext, 0, new Intent(MainApp.INTENT_TURN_OFF), 0);

        Intent notificationIntent = new Intent(mContext, MainDrawerActivity.class);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_FROM_BACKGROUND);
        PendingIntent contentIntent = PendingIntent.getActivity(mContext, NOTIFICATION_ID,
                notificationIntent,
                PendingIntent.FLAG_CANCEL_CURRENT);

        String title;
        if (sIsPaused) {
            title = mContext.getString(R.string.notification_paused);
        } else {
            title = mContext.getString(R.string.notification_scanning);
        }
        String metrics = mContext.getString(R.string.metrics_notification_text);
        metrics = String.format(metrics, sObservations, sCells, sWifis);

        String uploadtime = mContext.getString(R.string.metrics_observations_last_upload_time_never);
        if (sUploadTime > 0) {
            uploadtime = DateTimeUtils.formatTimeForLocale(sUploadTime);
        }
        String uploadLine = mContext.getString(R.string.metrics_notification_extraline);
        uploadLine = String.format(uploadLine, uploadtime);

        sLastUpdateTime = System.currentTimeMillis();

        return new NotificationCompat.Builder(mContext)
                .setSmallIcon(R.drawable.ic_status_scanning)
                .setContentTitle(title)
                .setContentText(metrics)
                .setWhen(sDisplayTime)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(metrics + "\n" + uploadLine))
                .addAction(R.drawable.ic_action_cancel, sStopTitle, turnOffIntent)
                .build();
    }

    private void update() {
        Notification notification = build();
        NotificationManager nm = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, notification);
    }

    public Notification buildNotification(String stopTitle) {
        sStopTitle = stopTitle;
        sIsPaused = false;
        sDisplayTime = System.currentTimeMillis();
        return build();
    }

    public void setPaused(boolean isPaused) {
        sIsPaused = isPaused;
        sDisplayTime = System.currentTimeMillis();
        update();
    }

    public void updateMetrics(int observations, int cells, int wifis, long uploadtime, boolean isActive) {
        sObservations = observations;
        sCells = cells;
        sWifis = wifis;
        sUploadTime = uploadtime;

        long diffLastUpdate = System.currentTimeMillis() - sLastUpdateTime;
        boolean isUpdateAnimated = Build.VERSION.SDK_INT < 21;
        if (isActive && (!isUpdateAnimated || diffLastUpdate > UPDATE_FREQUENCY)) {
            update();
        }
    }
}
