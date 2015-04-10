/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread.datahandling;

import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.utils.Zipper;

import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentLinkedQueue;

/*
 ReportBatchBuilder accepts MLS GeoSubmit JSON blobs and serializes them to
 string form.  This is
 */
public class ReportBatchBuilder {
    // The max number of reports stored in the mCachedReportBatches. Each report is a GPS location plus wifi and cell scan.
    // Once this size is reached, data is persisted to disk, mCachedReportBatches is cleared.
    public static final int MAX_REPORTS_IN_MEMORY = 50;

    private ConcurrentLinkedQueue<MLSJSONObject> reportList = new ConcurrentLinkedQueue<MLSJSONObject>();

    public int reportsCount() {
        return reportList.size();
    }

    /*
     This dequeues all collected reports and generates a stringified JSON blob

     Setting preserveReports to true will ensure that the reports are not lost after being
     converted to a string.  You almost certainly want to set that flag to false though as you'll
     eat memory.
     */
    byte[] finalizeAndClearReports() {
        return Zipper.zipData(generateJSON(false).getBytes());
    }

    private String generateJSON(boolean preserveReports) {

        ConcurrentLinkedQueue<MLSJSONObject> jsonCollector = new ConcurrentLinkedQueue<MLSJSONObject>();
        MLSJSONObject report = null;
        final String kPrefix = "{\"items\":[";
        StringBuilder reportString = new StringBuilder(kPrefix);
        report = reportList.poll();
        if (report != null) {
            if (preserveReports) {
                jsonCollector.add(report);
            }
            reportString.append(report.toString());
        }

        report = reportList.poll();
        while (report != null) {
            if (preserveReports) {
                jsonCollector.add(report);
            }
            reportString.append("," + report.toString());
            report = reportList.poll();
        }

        // Restore the ejected reports if necessary
        if (preserveReports) {
            report = jsonCollector.poll();
            while (report != null) {
                reportList.add(report);
                report = jsonCollector.poll();
            }
        }

        return reportString.toString() + "]}";
    }


    public void addReport(MLSJSONObject geoSubmitObj) {

        if (reportsCount() == MAX_REPORTS_IN_MEMORY) {
            // This can happen in the event that serializing reports to disk fails
            // and the reports list is never cleared.
            return;
        }
        reportList.add(geoSubmitObj);
    }

    public boolean maxReportsReached() {
        // Always try to flush memory to storage if saving stumble logs is enabled.
        if (Prefs.getInstanceWithoutContext().isSaveStumbleLogs()) {
            return true;
        }
        return reportsCount() == MAX_REPORTS_IN_MEMORY;
    }

    public int getCellCount() {
        int result = 0;
        for (MLSJSONObject obj: reportList) {
            result += obj.getCellCount();
        }
        return result;
    }

    public int getWifiCount() {
        int result = 0;
        for (MLSJSONObject obj : reportList) {
            result += obj.getWifiCount();
        }
        return result;
    }


    /* Returns the serialized JSON or an empty byte array if report count is 0 */
    public byte[] peekBytes() {
        if (reportsCount() == 0) {
            return new byte[0];
        }
        return generateJSON(true).getBytes();
    }
}
