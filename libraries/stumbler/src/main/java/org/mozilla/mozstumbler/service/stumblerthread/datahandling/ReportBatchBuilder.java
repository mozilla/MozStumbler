/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread.datahandling;

import org.json.JSONObject;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.base.SerializedJSONRows;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.base.JSONRowsObjectBuilder;
import org.mozilla.mozstumbler.service.utils.Zipper;

/*
 ReportBatchBuilder accepts MLS GeoSubmit JSON blobs and serializes them to
 string form.
 */
public class ReportBatchBuilder extends JSONRowsObjectBuilder {
    public int getCellCount() {
        int result = 0;
        for (JSONObject obj: mJSONEntries) {
            assert(obj instanceof MLSJSONObject);
            result += ((MLSJSONObject) obj).getCellCount();
        }
        return result;
    }

    public int getWifiCount() {
        int result = 0;
        for (JSONObject obj : mJSONEntries) {
            assert(obj instanceof MLSJSONObject);
            result += ((MLSJSONObject) obj).getWifiCount();
        }
        return result;
    }

    @Override
    public SerializedJSONRows finalizeToJSONRowsObject(String name) {
        return new ReportBatch(name, Zipper.zipData(generateJSON(false).getBytes()),
                entriesCount(), getWifiCount(), getCellCount());
    }
}
