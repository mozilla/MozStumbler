/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.mozstumbler.service.stumblerthread.datahandling.base;

import org.json.JSONObject;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.utils.Zipper;
import java.util.concurrent.ConcurrentLinkedQueue;

public class JSONRowsObjectBuilder {
    // Once this size is reached, data should be persisted to disk
    public static final int MAX_ROWS_IN_MEMORY = 50;

    protected ConcurrentLinkedQueue<JSONObject> mJSONEntries = new ConcurrentLinkedQueue<JSONObject>();

    public int entriesCount() {
        return mJSONEntries.size();
    }

    /*
     This dequeues all collected entries and generates a stringified JSON blob
     Setting preserveentries to true will ensure that the entries are not lost after being
     converted to a string.  You almost certainly want to set that flag to false though as you'll
     eat memory.
     */
    public SerializedJSONRows finalizeToJSONRowsObject() {
        return new SerializedJSONRows(Zipper.zipData(generateJSON(false).getBytes()), SerializedJSONRows.StorageState.IN_MEMORY_ONLY);
    }

    protected String generateJSON(boolean preserve) {
        ConcurrentLinkedQueue<JSONObject> jsonCollector = new ConcurrentLinkedQueue<JSONObject>();
        JSONObject entry = null;
        final String kPrefix = "{\"items\":[";
        StringBuilder entriesString = new StringBuilder(kPrefix);

        // Note that this is a blank separator for first iteration
        String jsonSep = "";

        entry = mJSONEntries.poll();
        while (entry != null) {
            if (preserve) {
                jsonCollector.add(entry);
            }
            entriesString.append(jsonSep + entry.toString());
            entry = mJSONEntries.poll();
            jsonSep = ",";
        }

        // Restore the ejected entries if necessary
        if (preserve) {
            entry = jsonCollector.poll();
            while (entry != null) {
                mJSONEntries.add(entry);
                entry = jsonCollector.poll();
            }
        }

        return entriesString.toString() + "]}";
    }

    public void addRow(JSONObject geoSubmitObj) {
        if (entriesCount() == MAX_ROWS_IN_MEMORY) {
            // This can happen in the event that serializing entries to disk fails
            // and the entries list is never cleared.
            return;
        }
        mJSONEntries.add(geoSubmitObj);
    }

    public boolean maxRowsReached() {
        // Always try to flush memory to storage if saving stumble logs is enabled.
        if (Prefs.getInstanceWithoutContext().isSaveStumbleLogs()) {
            return true;
        }
        return entriesCount() == MAX_ROWS_IN_MEMORY;
    }

    /* Returns the serialized JSON or an empty byte array if entry count is 0 */
    public byte[] peekBytes() {
        if (entriesCount() == 0) {
            return new byte[0];
        }
        return generateJSON(true).getBytes();
    }
}
