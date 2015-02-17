package org.mozilla.mozstumbler.service.core.logging;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.log.ILogger;

/*
 This is a proxy around the android logger so that we can see what the heck
 is happening when we run under test.
 */
public class ClientLog {

    private static ILogger svcLogger = (ILogger) ServiceLocator.getInstance().getService(ILogger.class);

    public static void w(String logTag, String s) {
        svcLogger.w(logTag, s);
        AppGlobals.guiLogInfo(logTag + ":" + s);
    }

    public static void e(String logTag, String s, Throwable e) {
        String msg = svcLogger.e(logTag, s, e);
        AppGlobals.guiLogError(logTag + ":" + s + ":" + msg);
    }

    public static void i(String logTag, String s) {
        svcLogger.i(logTag, s);
        AppGlobals.guiLogInfo(logTag + ":" + s);
    }

    public static void i(String logTag, String msg, String color, boolean isBold, boolean doNotTruncateLongString) {
        svcLogger.i(logTag, msg);
        AppGlobals.guiLogInfo(logTag + ":" + msg, color, isBold, doNotTruncateLongString);
    }

    public static void d(String logTag, String s) {
        svcLogger.d(logTag, s);
    }
}
