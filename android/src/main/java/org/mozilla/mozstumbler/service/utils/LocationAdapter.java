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
    A simple adapter for deserializing JSON to Location objects
 */
public class LocationAdapter {

    private static final String LOG_TAG = LoggerUtil.makeLogTag(LocationAdapter.class);

    private static final String JSON_LATITUDE = "lat";
    private static final String JSON_LONGITUDE = "lon";
    private static final String JSON_ACCURACY = "accuracy";

    public static Location fromJSONText(String text) {
        try {
            return fromJSON(new JSONObject(text));
        } catch (JSONException e) {
            Log.e(LOG_TAG, "Error deserializing JSON", e);
        }
        return null;
    }



    public static Location fromJSON(JSONObject jsonObj) {
        Location location = new Location(AppGlobals.LOCATION_ORIGIN_INTERNAL);
        location.setLatitude(getLat(jsonObj));
        location.setLongitude(getLon(jsonObj));
        location.setAccuracy(getAccuracy(jsonObj));
        return location;
    }

    /*
    We should deserialize the JSONObject in one shot and provide
    an object that you can access lat/long/accuracy
    Use an adapter pattern
    */
    private static float getLat(JSONObject jsonObject) {
        return getFloat(jsonObject, JSON_LATITUDE);
    }

    private static float getLon(JSONObject jsonObject) {
        return getFloat(jsonObject, JSON_LONGITUDE);
    }


    private static float getAccuracy(JSONObject jsonObject) {
        return getFloat(jsonObject, JSON_ACCURACY);
    }


    private static float getFloat(JSONObject jsonObject, String field_name) {
        float result = 0f;
        try {
            result = Float.parseFloat(jsonObject.getString(field_name));
        } catch (JSONException e) {
            Log.e(LOG_TAG, "Error decoding " + field_name + " from JSON: ", e);
        }
        return result;
    }

}
