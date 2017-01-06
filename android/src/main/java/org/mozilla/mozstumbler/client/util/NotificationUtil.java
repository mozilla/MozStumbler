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
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.ISystemClock;
import org.mozilla.mozstumbler.svclocator.services.log.ILogger;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

public class NotificationUtil {

    private static final ILogger Log = (ILogger) ServiceLocator
            .getInstance()
            .getService(ILogger.class);
    private static final String LOG_TAG = LoggerUtil.makeLogTag(NotificationUtil.class);

    public static final int NOTIFICATION_ID = 1;
    private static final long UPDATE_FREQUENCY = 60 * 1000;
    private static String sStopTitle;
    private static int sObservations, sCells, sWifis;
    private static long sUploadTime, sDisplayTime, sLastUpdateTime;
    private static boolean sIsPaused;
    private final Context mContext;
    private ISystemClock clock = (ISystemClock) ServiceLocator.getInstance().getService(ISystemClock.class);

    public NotificationUtil(Context context) {
        mContext = context;
    }

    private Notification build() {
        PendingIntent notificationStopIntent = PendingIntent.getBroadcast(mContext,
                0,
                new Intent(MainApp.NOTIFICATION_STOP),
                0);

        Intent notificationIntent = new Intent(mContext, MainDrawerActivity.class);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_FROM_BACKGROUND);
        PendingIntent contentIntent = PendingIntent.getActivity(mContext, NOTIFICATION_ID,
                notificationIntent,
                PendingIntent.FLAG_CANCEL_CURRENT);

        String appname = mContext.getString(R.string.app_name);
        int titleId = sIsPaused ? R.string.notification_paused : R.string.notification_scanning;
        String title = String.format(mContext.getString(titleId), appname);

        String metrics = mContext.getString(R.string.metrics_notification_text);
        metrics = String.format(metrics, sObservations, sCells, sWifis);

        String uploadtime = mContext.getString(R.string.metrics_observations_last_upload_time_never);
        if (sUploadTime > 0) {
            uploadtime = DateTimeUtils.formatTimeForSystem(sUploadTime,mContext);
        }
        String uploadLine = mContext.getString(R.string.metrics_notification_extraline);
        uploadLine = String.format(uploadLine, uploadtime);

        ISystemClock clock = (ISystemClock) ServiceLocator.getInstance()
                .getService(ISystemClock.class);

        sLastUpdateTime = clock.currentTimeMillis();

        return new NotificationCompat.Builder(mContext)
                .setSmallIcon(R.drawable.ic_status_scanning)
                .setContentTitle(title)
                .setContentText(metrics)
                .setWhen(sDisplayTime)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(metrics + "\n" + uploadLine))
                .addAction(R.drawable.ic_action_cancel, sStopTitle, notificationStopIntent)
                .build();
    }

    void update() {
        Notification notification = build();
        NotificationManager nm = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            // This really *shouldn't* happen, but we get this on some devices. See :
            // https://github.com/mozilla/MozStumbler/issues/1564
            Log.d(LOG_TAG, "Couldn't acquire notification service");
            return;
        }
        nm.notify(NOTIFICATION_ID, notification);

    }

    public Notification buildNotification(String stopTitle) {
        sStopTitle = stopTitle;
        sIsPaused = false;
        sDisplayTime = clock.currentTimeMillis();
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

    // Will update the last uploaded label without rate-limiting the update. Therefore ensure
    // this is being called only when the upload label actually needs updating.
    public void updateLastUploadedLabel(long value) {
        sUploadTime = value;
        update();
    }
}
