package org.mozilla.mozstumbler.service;

public class SharedConstants {
    /** All intent actions start with this string  */
    public static final String ACTION_NAMESPACE = "org.mozilla.mozstumbler.intent.action";

    /** Handle this for logging reporter info */
    public static final String ACTION_GUI_LOG_MESSAGE = SharedConstants.ACTION_NAMESPACE + ".LOG_MESSAGE";
    public static final String ACTION_GUI_LOG_MESSAGE_EXTRA = ACTION_GUI_LOG_MESSAGE + ".MESSAGE";

    /** A common intent action argument, but you shouldn't need  to access this directly,
     * the specific intent action has its own constant that is assigned to this
     * for example, ACTION_WIFIS_SCANNED_ARG_TIME */
    public static final String ACTION_ARG_TIME = "time";

    public static final String SYNC_EXTRAS_IGNORE_WIFI_STATUS =
            "org.mozilla.mozstumbler.sync.ignore_wifi_status";

    /** Location constructor requires a named origin, these are created in the app  */
    public static final String LOCATION_ORIGIN_INTERNAL = "internal";

    public enum ActiveOrPassiveStumbling { ACTIVE_STUMBLING, PASSIVE_STUMBLING };

    // In passive mode, only scan this many times for each gps
    public static final int PASSIVE_MODE_MAX_SCANS_PER_GPS = 3;

    /** These are set on startup */
    public static String appVersionName = "0.0.0";
    public static int appVersionCode = 0;
    public static String appName = "StumblerService";
    public static boolean isDebug;
    public static String mozillaApiKey;
}

