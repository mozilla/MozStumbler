/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.mapview;

import android.location.Location;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.mozstumbler.core.adapters.LocationAdapter;
import org.mozilla.mozstumbler.core.http.ILocationService;
import org.mozilla.mozstumbler.core.http.IResponse;
import org.mozilla.mozstumbler.core.http.MLS;
import org.mozilla.mozstumbler.service.AppGlobals;

import java.util.concurrent.atomic.AtomicInteger;

/*
This class provides MLS locations by calling HTTP methods against the MLS.
 */
public class MLSLocationGetter extends AsyncTask<String, Void, JSONObject> {
    private static final String LOG_TAG = AppGlobals.LOG_PREFIX + MLSLocationGetter.class.getSimpleName();
    private static final String RESPONSE_OK_TEXT = "ok";
    private ILocationService mls;
    private MLSLocationGetterCallback mCallback;
    private byte[] mQueryMLSBytes;
    private final int MAX_REQUESTS = 10;
    private static AtomicInteger sRequestCounter = new AtomicInteger(0);

    public interface MLSLocationGetterCallback {
        void setMLSResponseLocation(Location loc);
    }

    public MLSLocationGetter(MLSLocationGetterCallback callback, JSONObject mlsQueryObj) {
        mCallback = callback;
        mQueryMLSBytes  = mlsQueryObj.toString().getBytes();
        mls = new MLS();
    }

    @Override
    public JSONObject doInBackground(String... params) {

        int requests = sRequestCounter.incrementAndGet();
        if (requests > MAX_REQUESTS) {
            return null;
        }

        IResponse resp = mls.search(mQueryMLSBytes, null);
        if (resp == null) {
            Log.e(LOG_TAG, "Error processing search request", new RuntimeException("Error processing search"));
            return null;
        }
        int bytesSent = resp.bytesSent();

        JSONObject response = null;
        try {
            response = new JSONObject(resp.body());
        } catch (JSONException e) {
            Log.e(LOG_TAG, "Error deserializing JSON", e);
            return null;
        }

        String status = "";
        try {
            status = response.getString("status");
        } catch (JSONException ex) {
            Log.e(LOG_TAG, "Error deserializing status from JSON", ex);
            return null;
        }

        if (!status.equals(RESPONSE_OK_TEXT)) {
            return null;
        }

        return response;
    }

    @Override
    protected void onPostExecute(JSONObject result) {
        sRequestCounter.decrementAndGet();
        if (result == null) {
            return;
        }
        Location location = LocationAdapter.fromJSON(result);
        mCallback.setMLSResponseLocation(location);
    }
}
