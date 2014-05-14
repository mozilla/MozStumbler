package org.mozilla.mozstumbler;

public class SharedConstants {
    /** All intent actions start with this string  */
    public static final String ACTION_NAMESPACE = "org.mozilla.mozstumbler.intent.action";

    /** A common intent action argument, but you shouldn't need  to access this directly,
     * the specific intent action has its own constant that is assigned to this
     * for example, ACTION_WIFIS_SCANNED_ARG_TIME */
    public static final String ACTION_ARG_TIME = "time";

    public static final String SYNC_EXTRAS_IGNORE_WIFI_STATUS =
            "org.mozilla.mozstumbler.sync.ignore_wifi_status";

    /** Location constructor requires a named origin, these are created in the app  */
    public static final String LOCATION_ORIGIN_INTERNAL = "internal";
}
