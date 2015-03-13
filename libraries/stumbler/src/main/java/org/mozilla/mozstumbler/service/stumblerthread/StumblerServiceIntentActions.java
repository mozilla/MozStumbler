/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread;

public class StumblerServiceIntentActions {
    public static final String SVC_RESP_NS = "org.mozilla.mozstumbler.service.intentaction.response";
    public static final String SVC_RESP_UNIQUE_WIFI_COUNT = SVC_RESP_NS + ".uniq.wifi";
    public static final String SVC_RESP_UNIQUE_CELL_COUNT = SVC_RESP_NS + ".uniq.cell";
    public static final String SVC_RESP_OBSERVATION_PT = SVC_RESP_NS + ".obs_pts";
    public static final String SVC_RESP_VISIBLE_CELL = SVC_RESP_NS + ".cell_towers";
    public static final String SVC_RESP_VISIBLE_AP = SVC_RESP_NS + ".wifi_access_points";

    public static final String SVC_REQ_NS = "org.mozilla.mozstumbler.service.intentaction.request";
    public static final String SVC_REQ_UNIQUE_WIFI_COUNT = SVC_REQ_NS + ".uniq.wifi";
    public static final String SVC_REQ_UNIQUE_CELL_COUNT = SVC_REQ_NS + ".uniq.cell";
    public static final String SVC_REQ_OBSERVATION_PT = SVC_REQ_NS + ".obs_pts";
    public static final String SVC_REQ_VISIBLE_CELL = SVC_REQ_NS + ".cell_towers";
    public static final String SVC_REQ_VISIBLE_AP = SVC_REQ_NS + ".wifi_access_points";
}
