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
    private static String sTitle, sSubtitle, sStopTitle;
    private static int sObservations, sCells, sWifis;
    private static long sUploadTime;

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

        String subText = mContext.getString(R.string.metrics_notification_subtext);
        String bigText = sSubtitle + "\n" + mContext.getString(R.string.metrics_notification_bigtext);

        String uploadtime = mContext.getString(R.string.metrics_observations_last_upload_time_never);
        if (sUploadTime > 0) {
            uploadtime = DateTimeUtils.formatTimeForLocale(sUploadTime);
        }

        return new NotificationCompat.Builder(mContext)
                .setSmallIcon(R.drawable.ic_status_scanning)
                .setContentTitle(sTitle)
                .setContentText(sSubtitle)
                .setSubText(String.format(subText, sObservations, sCells, sWifis))
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(String.format(bigText, sObservations, sCells, sWifis, uploadtime)))
                .addAction(R.drawable.ic_action_cancel, sStopTitle, turnOffIntent)
                .build();
    }

    private void update() {
        Notification notification = build();
        NotificationManager nm = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, notification);
    }

    public Notification buildNotification(String title, String subtitle, String stopTitle) {
        sTitle = title;
        sSubtitle = subtitle;
        sStopTitle = stopTitle;
        return build();
    }

    public void updateSubtitle(String s) {
        sSubtitle = s;
        update();
    }

    public void updateMetrics(int observations, int cells, int wifis, long uploadtime, boolean scanning) {
        sObservations = observations;
        sCells = cells;
        sWifis = wifis;
        sUploadTime = uploadtime;

        if (scanning) {
            if (Build.VERSION.SDK_INT >= 16) {
                update();
            }
        }
    }
}
