/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.svclocator.services.log;

import android.support.v4.util.CircularArray;

/*
 This is a proxy around the android logger so that we can see what the heck
 is happening when we run under test.
 */
public class DebugLogger implements ILogger {

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
