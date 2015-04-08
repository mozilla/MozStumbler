/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

 package org.mozilla.mozstumbler.service.stumblerthread.datahandling;

import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.log.ILogger;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;


public class ReportBatchBuilder {

    private static ILogger Log = (ILogger) ServiceLocator.getInstance().getService(ILogger.class);
    private static String LOG_TAG = LoggerUtil.makeLogTag(ReportBatchBuilder.class);

    // The max number of reports stored in the mCurrentReports. Each report is a GPS location plus wifi and cell scan.
    // Once this size is reached, data is persisted to disk, mCurrentReports is cleared.
    public static final int MAX_REPORTS_IN_MEMORY = 50;
    public int wifiCount;
    public int cellCount;
    StringBuilder reportString;
    int reportCount;

    public int reportsCount() {
        return reportCount;
    }

    String finalizeReports() {
        final String kSuffix = "]}";
        return reportString.toString() + kSuffix;
    }

    public void clearReports() {
        reportCount = 0;
        reportString = null;
    }

    public void addReport(String report) {
        if (reportCount == MAX_REPORTS_IN_MEMORY) {
            // This can happen in the event that serializing reports to disk fails
            // and the reports list is never cleared.
            return;
        }
        reportCount++;

        if (reportString == null) {
            final String kPrefix = "{\"items\":[";
            reportString = new StringBuilder(kPrefix);
            reportString.append(report);
        } else {
            reportString.append("," + report);
        }
    }

    public boolean maxReportsReached() {
        // Always try to flush memory to storage if saving stumble logs is enabled.
        if (Prefs.getInstanceWithoutContext().isSaveStumbleLogs()) {
            return true;
        }
        return reportsCount() == MAX_REPORTS_IN_MEMORY;
    }
}
