package org.mozilla.mozstumbler.service.core.logging;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.mozilla.mozstumbler.BuildConfig;
import org.mozilla.mozstumbler.service.AppGlobals;

/*
 This is a proxy around the android logger so that we can see what the heck
 is happening when we run under test.
 */
public class Log {
    public static void e(String logTag, String s) {
        if (BuildConfig.BUILD_TYPE.equals("unittest")) {
            System.out.print("E: "+logTag + ", " + s);
        } else {
            android.util.Log.e(logTag, s);
        }
        AppGlobals.guiLogError(logTag + ":" + s);
    }

    public static void w(String logTag, String s) {
        if (BuildConfig.BUILD_TYPE.equals("unittest")) {
            System.out.print("W: " + logTag + ", " + s);
        } else {
            android.util.Log.w(logTag, s);
        }
        AppGlobals.guiLogInfo(logTag + ":" + s);
    }

    public static void e(String logTag, String s, IOException e) {
        if (BuildConfig.BUILD_TYPE.equals("unittest")) {
            System.out.print("E: " + logTag + ", " + s);
            e.printStackTrace();
        } else {
            android.util.Log.e(logTag, s, e);
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        if (e != null) {
            e.printStackTrace(pw);
        }

        AppGlobals.guiLogError(logTag + ":" + s  + sw.toString());
    }

    public static void i(String logTag, String s) {
        if (BuildConfig.BUILD_TYPE.equals("unittest")) {
            System.out.print("i: " + logTag + ", " + s);
        } else {
            android.util.Log.i(logTag, s);
        }
        AppGlobals.guiLogInfo(logTag + ":" + s);
    }

    public static void d(String logTag, String s) {
        if (BuildConfig.BUILD_TYPE.equals("unittest")) {
            System.out.print("d: " + logTag + ", " + s);
        } else {
            android.util.Log.d(logTag, s);
        }
        // Note that debug level messages do not go to the GUI log
    }
}
