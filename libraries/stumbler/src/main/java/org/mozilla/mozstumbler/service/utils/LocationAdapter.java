/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.utils;

import android.location.Location;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

/*
    A simple adapter for deserializing JSON to Location objects from the
    geolocate API.

    https://developers.google.com/maps/documentation/business/geolocation/#responses

 */
public class LocationAdapter {

    private static final String LOG_TAG = LoggerUtil.makeLogTag(LocationAdapter.class);

    private static final String JSON_LATITUDE = "lat";
    private static final String JSON_LONGITUDE = "lng";
    private static final String JSON_ACCURACY = "accuracy";

    public static Location fromJSON(JSONObject jsonObj) {
        Location location = new Location(AppGlobals.LOCATION_ORIGIN_INTERNAL);
        location.setLatitude(getLat(jsonObj));
        location.setLongitude(getLng(jsonObj));
        location.setAccuracy(getAccuracy(jsonObj));
        return location;
    }

    public static Location fromJSON_LatLngOnly(JSONObject jsonObj) {
        Location location = new Location(AppGlobals.LOCATION_ORIGIN_INTERNAL);
        location.setLatitude(getLat(jsonObj));
        location.setLongitude(getLng(jsonObj));
        return location;
    }

    /*
    We should deserialize the JSONObject in one shot and provide
    an object that you can access lat/long/accuracy
    Use an adapter pattern
    */
    public static float getLat(JSONObject jsonObject) {
        return getFloat(jsonObject, JSON_LATITUDE, true);
    }

    public static float getLng(JSONObject jsonObject) {
        return getFloat(jsonObject, JSON_LONGITUDE, true);
    }


    public static float getAccuracy(JSONObject jsonObject) {
        return getFloat(jsonObject, JSON_ACCURACY, false);
    }


    public static float getFloat(JSONObject jsonObject, String field_name, boolean useLocation) {
        float result = 0f;
        try {
            if (useLocation) {
                result = Float.parseFloat(jsonObject.getJSONObject("location").getString(field_name));
            } else {
                result = Float.parseFloat(jsonObject.getString(field_name));
            }
        } catch (JSONException e) {
            Log.e(LOG_TAG, "Error decoding " + field_name + " from JSON: ", e);
        }
        return result;
    }
}
