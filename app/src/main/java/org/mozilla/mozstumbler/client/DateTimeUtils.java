/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client;

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
}
