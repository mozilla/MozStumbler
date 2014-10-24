package org.mozilla.mozstumbler.service.core.logging;

import org.mozilla.mozstumbler.BuildConfig;
import org.mozilla.mozstumbler.service.AppGlobals;

/*
 This is a proxy around the android logger so that we can see what the heck
 is happening when we run under test.
 */
public class Log {

    public static void w(String logTag, String s) {
        if (BuildConfig.BUILD_TYPE.equals("unittest")) {
            System.out.println("W: " + logTag + ", " + s);
        } else {
            android.util.Log.w(logTag, s);
        }
        AppGlobals.guiLogInfo(logTag + ":" + s);
    }

    public static void e(String logTag, String s, Throwable e) {
        String msg;
        if (e == null) {
            msg = "";
        } else {
            msg = e.toString();
        }

        if (BuildConfig.BUILD_TYPE.equals("unittest")) {
            System.out.println("E: " + logTag + ", " + s);
            if (e != null) {
                e.printStackTrace();
            }
        } else {
            android.util.Log.e(logTag, s + ":" + msg);
        }

        AppGlobals.guiLogError(logTag + ":" + s  + ":" + msg);
    }

    public static void i(String logTag, String s) {
        if (BuildConfig.BUILD_TYPE.equals("unittest")) {
            System.out.println("i: " + logTag + ", " + s);
        } else {
            android.util.Log.i(logTag, s);
        }
        AppGlobals.guiLogInfo(logTag + ":" + s);
    }

    public static void d(String logTag, String s) {
        if (BuildConfig.BUILD_TYPE.equals("release")) {
            return;
        }

        if (BuildConfig.BUILD_TYPE.equals("unittest")) {
            System.out.println("d: " + logTag + ", " + s);
        } else {
            android.util.Log.d(logTag, s);
        }
        // Note that debug level messages do not go to the GUI log
    }

}
