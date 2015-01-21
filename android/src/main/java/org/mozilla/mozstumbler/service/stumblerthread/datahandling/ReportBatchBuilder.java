package org.mozilla.mozstumbler.service.stumblerthread.datahandling;

import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

import java.util.ArrayList;

/**
* Created by victorng on 14-11-20.
*/
public class ReportBatchBuilder {
    // The max number of reports stored in the mCurrentReports. Each report is a GPS location plus wifi and cell scan.
    // Once this size is reached, data is persisted to disk, mCurrentReports is cleared.
    public static final int MAX_REPORTS_IN_MEMORY = 50;
    private static final String LOG_TAG = LoggerUtil.makeLogTag(ReportBatchBuilder.class);
    private final ArrayList<String> reports = new ArrayList<String>();
    public int wifiCount;
    public int cellCount;

    public int reportsCount() {
        return reports.size();
    }

    String finalizeReports() {
        final String kPrefix = "{\"items\":[";
        final String kSuffix = "]}";
        final StringBuilder sb = new StringBuilder(kPrefix);
        String sep = "";
        final String separator = ",";
        if (reports != null) {
            for(String s: reports) {
                sb.append(sep).append(s);
                sep = separator;
            }
        }

        final String result = sb.append(kSuffix).toString();
        return result;
    }

    public void clearReports() {
        reports.clear();
    }

    public void addReport(String report) {
        if (reports.size() == MAX_REPORTS_IN_MEMORY) {
            // This can happen in the event that serializing reports to disk fails
            // and the reports list is never cleared.
            return;
        }
        reports.add(report);
    }

    public boolean maxReportsReached() {
        // Always try to flush memory to storage if saving stumble logs is enabled.
        if (Prefs.getInstanceWithoutContext().isSaveStumbleLogs()) {
            return true;
        }
        return reportsCount() == MAX_REPORTS_IN_MEMORY;
    }
}
