/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.mapview;

import android.location.Location;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.core.http.ILocationService;
import org.mozilla.mozstumbler.service.core.http.IResponse;
import org.mozilla.mozstumbler.service.core.offline.IOfflineLocationService;
import org.mozilla.mozstumbler.service.utils.LocationAdapter;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

import java.util.concurrent.atomic.AtomicInteger;

/*
 An asynchronous task to fetch MLS locations.
 */
public class AsyncGeolocate extends AsyncTask<String, Void, Location> {
    private static final String LOG_TAG = LoggerUtil.makeLogTag(AsyncGeolocate.class);
    private static AtomicInteger sRequestCounter = new AtomicInteger(0);
    private final int MAX_REQUESTS = 10;

    private MLSLocationGetterCallback mCallback;

    JSONObject mlsGeolocateObj;

    private boolean mIsBadRequest;

    public AsyncGeolocate(MLSLocationGetterCallback callback, JSONObject mlsQueryObj) {
        mCallback = callback;
        mlsGeolocateObj = mlsQueryObj;
    }

    @Override
    public Location doInBackground(String... params) {
        JSONObject response = null;
        ILocationService mls = null;

        int requests = sRequestCounter.incrementAndGet();
        if (requests > MAX_REQUESTS) {
            return null;
        }

        if (Prefs.getInstanceWithoutContext().useOfflineGeo()) {
            Log.i(LOG_TAG, "Using offline location fixing!");
            mls = (ILocationService)
                    ServiceLocator.getInstance()
                            .getService(IOfflineLocationService.class);
        } else {

            Log.i(LOG_TAG, "Using MLS online location fixing!");
            mls = (ILocationService)
                    ServiceLocator.getInstance()
                            .getService(ILocationService.class);

        }

        // TODO: the ILocationService shouldn't be returning raw HTTP responses.
        // We should either get a Location or a null.
        IResponse resp = mls.search(mlsGeolocateObj, null, false);

        if (resp == null) {
            Log.i(LOG_TAG, "Error processing search request");
            return null;
        }

        if (resp.isErrorCode400BadRequest()) {
            //TODO detect malformed request, and clear out mlsrequest on observation point
            mIsBadRequest = true;
            return null;
        }

        try {
            response = new JSONObject(resp.body());
        } catch (JSONException e) {
            Log.e(LOG_TAG, "Error deserializing JSON. " + e.toString(), e);
            return null;
        }
        return LocationAdapter.fromJSON(response);
    }

    @Override
    protected void onPostExecute(Location location) {
        sRequestCounter.decrementAndGet();
        if (location == null) {
            mCallback.errorMLSResponse(mIsBadRequest);
        } else {
            mCallback.setMLSResponseLocation(location);
        }
    }

    public interface MLSLocationGetterCallback {
        void setMLSResponseLocation(Location loc);

        void errorMLSResponse(boolean stopRequesting);
    }
}
