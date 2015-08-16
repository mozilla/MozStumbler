/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ConcurrentLinkedQueue;

public class AppGlobals {

    /* All intent actions start with this string. Only locally broadcasted. */
    public static final String ACTION_NAMESPACE = "org.mozilla.mozstumbler.intent.action";

    /* Handle this for logging reporter info. */
    public static final String ACTION_GUI_LOG_MESSAGE = AppGlobals.ACTION_NAMESPACE + ".LOG_MESSAGE";
    public static final String ACTION_GUI_LOG_MESSAGE_EXTRA = ACTION_GUI_LOG_MESSAGE + ".MESSAGE";

    /* Defined here so that the Reporter class can access the time of an Intent in a generic fashion.
     * Classes should have their own constant that is assigned to this, for example,
     * WifiScanner has ACTION_WIFIS_SCANNED_ARG_TIME = ACTION_ARG_TIME.
     * This member definition in the broadcaster makes it clear what the extra Intent args are for that class. */
    public static final String ACTION_ARG_TIME = "time";

    /* Location constructor requires a named origin, these are created in the app. */
    public static final String LOCATION_ORIGIN_INTERNAL = "internal";
    public static final String NO_TRUNCATE_FLAG = "~";
    public static final String ACTION_TEST_SETTING_ENABLED = "stumbler-test-setting-enabled";
    public static final String ACTION_TEST_SETTING_DISABLED = "stumbler-test-setting-disabled";
    public static final String TELEMETRY_TIME_BETWEEN_UPLOADS_SEC = "STUMBLER_TIME_BETWEEN_UPLOADS_SEC";
    public static final String TELEMETRY_BYTES_UPLOADED_PER_SEC = "STUMBLER_VOLUME_BYTES_UPLOADED_PER_SEC";
    public static final String TELEMETRY_TIME_BETWEEN_STARTS_SEC = "STUMBLER_TIME_BETWEEN_START_SEC";
    public static final String TELEMETRY_BYTES_PER_UPLOAD = "STUMBLER_UPLOAD_BYTES";
    public static final String TELEMETRY_OBSERVATIONS_PER_UPLOAD = "STUMBLER_UPLOAD_OBSERVATION_COUNT";
    public static final String TELEMETRY_CELLS_PER_UPLOAD = "STUMBLER_UPLOAD_CELL_COUNT";
    public static final String TELEMETRY_WIFIS_PER_UPLOAD = "STUMBLER_UPLOAD_WIFI_AP_COUNT";
    public static final String TELEMETRY_OBSERVATIONS_PER_DAY = "STUMBLER_OBSERVATIONS_PER_DAY";
    public static final String TELEMETRY_TIME_BETWEEN_RECEIVED_LOCATIONS_SEC = "STUMBLER_TIME_BETWEEN_RECEIVED_LOCATIONS_SEC";
    private static final SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss: ");
    /* These are set on startup. The appVersionName and code are not used in the service-only case. */
    public static String appVersionName = "0.0.0";
    public static int appVersionCode = 0;
    public static String appName = "StumblerService";
    public static boolean isDebug;
    public static boolean isRobolectric;
    public static boolean hasSignificantMotionSensor;
    /* The log activity will clear this periodically, and display the messages.
     * Always null when the stumbler service is used stand-alone. */
    public static volatile ConcurrentLinkedQueue<String> guiLogMessageBuffer;

    public static void guiLogError(String msg) {
        guiLogInfo(msg, "red", true, false);
    }

    public static void guiLogInfo(String msg) {
        guiLogInfo(msg, "white", false, false);
    }

    public static void guiLogInfo(String msg, String color, boolean isBold, boolean doNotTruncateLongString) {
        if (guiLogMessageBuffer != null) {
            if (isBold) {
                msg = "<b>" + msg + "</b>";
            }
            String noTruncateFlag = doNotTruncateLongString ? NO_TRUNCATE_FLAG : "";
            String ts = formatter.format(new Date());
            guiLogMessageBuffer.add(noTruncateFlag + ts + "<font color='" + color + "'>" + msg + "</font>");
        }
    }

    public enum ActiveOrPassiveStumbling {ACTIVE_STUMBLING, PASSIVE_STUMBLING}
}

