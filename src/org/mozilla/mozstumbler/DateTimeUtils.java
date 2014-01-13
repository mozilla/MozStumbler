package org.mozilla.mozstumbler;

import android.annotation.SuppressLint;

import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

final class DateTimeUtils {
    private static final DateFormat mISO8601Format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US);

    static final long MILLISECONDS_PER_DAY = 86400000;  // milliseconds/day

    static {
        mISO8601Format.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private DateTimeUtils() {
    }

    @SuppressLint("SimpleDateFormat")
    static String formatDate(Date date) {
        return mISO8601Format.format(date);
    }

    static String formatTime(long time) {
        return formatDate(new Date(time));
    }

    static String formatTimeForLocale(long time) {
        return DateFormat.getDateTimeInstance().format(time);
    }

    static String formatCurrentTime() {
        return formatTime(System.currentTimeMillis());
    }
}
