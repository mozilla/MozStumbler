/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.svclocator.services.log;

import java.io.PrintWriter;
import java.io.StringWriter;

public class DebugLogger implements ILogger {

    public void w(String logTag, String s) {

        android.util.Log.w(logTag, s);
    }

    @Override
    public void e(String logTag, String s) {
        android.util.Log.e(logTag, s);
    }

    public String e(String logTag, String s, Throwable e) {
        if (e instanceof OutOfMemoryError) {
            // These are usually going to be OutOfMemoryErrors
            // We want the full stacktrace for full errors, but
            // not regular exception types.
            System.gc();
        }

        String msg;
        if (e == null) {
            msg = "";
        } else {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            msg = sw.toString();
        }
        android.util.Log.e(logTag, s + ":" + msg);
        return msg;
    }

    public void i(String logTag, String s) {

        android.util.Log.i(logTag, s);
    }

    public void d(String logTag, String s) {

        android.util.Log.d(logTag, s);
    }
}
