package org.mozilla.mozstumbler.service.utils;

import android.annotation.SuppressLint;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class DateTimeUtils {
    private static final DateFormat sLocaleFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
    private static final DateFormat sISO8601Format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    public static final long MILLISECONDS_PER_DAY = 86400000;  // milliseconds/day

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
        c.set(Calendar.DAY_OF_MONTH,1);
        return c.getTimeInMillis();
    }

    public static String formatTime(long time) {
        return formatDate(new Date(time));
    }

    public static String formatTimeForLocale(long time) {
        return sLocaleFormat.format(time);
    }

    static String formatCurrentTime() {
        return formatTimeForLocale(System.currentTimeMillis());
    }
}
