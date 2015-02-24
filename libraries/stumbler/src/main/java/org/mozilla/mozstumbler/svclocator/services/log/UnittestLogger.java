/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.svclocator.services.log;

import android.support.v4.util.CircularArray;
import android.util.Log;

import java.lang.reflect.Field;

/*
 This is a proxy around the android logger so that we can see what the heck
 is happening when we run under test.
 */
public class UnittestLogger implements ILogger {

    private static final String LOG_TAG = LoggerUtil.makeLogTag(UnittestLogger.class);

    static {
        try {
            @SuppressWarnings("all")
            final Field field = Class.forName("org.robolectric.shadows.ShadowLog").getDeclaredField("stream");
            // Allow modification on the field
            field.setAccessible(true);
            // Get
            final Object oldValue = field.get(Class.forName("java.io.PrintStream"));
            // Sets the field to the new value
            field.set(oldValue, System.out);
        } catch (Exception e) {
            Log.d(LOG_TAG, "Can't reroute android.util.Log to robolectric");
        }
    }

    public static CircularArray<String> messageBuffer = new CircularArray<String>(10);

    public void w(String logTag, String s) {
        String msg = "W: " + logTag + ", " + s;
        System.out.println(msg);
        messageBuffer.addLast(msg);
    }

    public String e(String logTag, String s, Throwable e) {
        if (e instanceof OutOfMemoryError) {
            // These are usually going to be OutOfMemoryErrors
            // We want the full stacktrace for full errors, but
            // not regular exception types.
            System.gc();
        }

        String msg = "E: " + logTag + ", " + s;
        System.out.println(msg);
        if (e != null) {
            e.printStackTrace();
        }
        messageBuffer.addLast(msg);
        return msg;
    }

    public void i(String logTag, String s) {
        String msg = "i: " + logTag + ", " + s;
        System.out.println(msg);
        messageBuffer.addLast(msg);
    }

    public void d(String logTag, String s) {
        String msg = "d: " + logTag + ", " + s;
        System.out.println(msg);
        messageBuffer.addLast(msg);
    }
}
