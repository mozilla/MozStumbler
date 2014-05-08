package org.mozilla.mozstumbler;

public class SharedConstants {
    /** All intent actions start with this string  */
    public static final String ACTION_NAMESPACE = "org.mozilla.mozstumbler.intent.action";

    /** A common intent action argument, but you shouldn't need  to access this directly,
     * the specific intent action has its own constant that is assigned to this
     * for example, ACTION_WIFIS_SCANNED_ARG_TIME */
    public static final String ACTION_ARG_TIME = "time";
}
