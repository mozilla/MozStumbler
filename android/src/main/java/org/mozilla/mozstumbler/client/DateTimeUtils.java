/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client;

import android.annotation.SuppressLint;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;

import org.mozilla.mozstumbler.R;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class DateTimeUtils {
    private static final DateFormat sLocaleFormatDateTime = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
    private static final DateFormat sLocaleFormatDate = DateFormat.getDateInstance(DateFormat.SHORT);
    private static final DateFormat sISO8601Format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);


    static {
        sISO8601Format.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private DateTimeUtils() {
    }

    @SuppressLint("SimpleDateFormat")
    static String formatDate(Date date) {
        return sISO8601Format.format(date);
    }

    public static long removeDay(long time) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(time);
        c.set(Calendar.DAY_OF_MONTH, 1);
        return c.getTimeInMillis();
    }

    public static String formatTime(long time) {
        return formatDate(new Date(time));
    }

    public static String formatTimeForLocale(long time) {
        return sLocaleFormatDateTime.format(time);
    }

    public static String recoverFormatForSystem(Context mContext){
        return Settings.System.getString(mContext.getContentResolver(),Settings.System.DATE_FORMAT);
    }

    public static String formatTimeForSystem(long time, Context mContext){
        String systemFormatDateTime = recoverFormatForSystem(mContext);
        if(TextUtils.isEmpty(systemFormatDateTime)){
            return formatTimeForLocale(time);
        }else{
            StringBuffer systemFormatDateTimeBuffer = new StringBuffer(systemFormatDateTime);
            systemFormatDateTimeBuffer.append(" HH:mm a");
            return new SimpleDateFormat(systemFormatDateTimeBuffer.toString()).format(time);
        }
    }

    public static String formatDateForSystem(long time, Context mContext){
        String systemFormatDateTime = recoverFormatForSystem(mContext);
        if(TextUtils.isEmpty(systemFormatDateTime)){
            return sLocaleFormatDate.format(time);
        }else{
            return new SimpleDateFormat(systemFormatDateTime).format(time);
        }
    }

    public static String prettyPrintTimeDiff(long time, Context mContext) {
        final long seconds = (System.currentTimeMillis() - time) / 1000;
        if (seconds < 0) {
            return DateTimeUtils.formatTimeForSystem(time,mContext);
        } else if (seconds < 60) {
            return mContext.getResources().getQuantityString(R.plurals.time_diff_seconds, (int) seconds, seconds);
        }

        final long minutes = (long) Math.floor(seconds / 60.0);
        if (minutes <= 60) {
            return mContext.getResources().getQuantityString(R.plurals.time_diff_minutes, (int) minutes, minutes);
        }

        final long hours = (long) Math.floor(minutes / 60.0);
        if (hours <= 24) {
            return mContext.getResources().getQuantityString(R.plurals.time_diff_hours, (int) hours, hours);
        }

        final long days = (long) Math.floor(hours / 24.0);
        if (days <= 7) {
            return mContext.getResources().getQuantityString(R.plurals.time_diff_days, (int) days, days);
        }
        return formatDateForSystem(time,mContext);
    }
}
