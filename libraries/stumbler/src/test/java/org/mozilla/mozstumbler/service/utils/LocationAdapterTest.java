/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.utils;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static junit.framework.Assert.assertEquals;

@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
public class LocationAdapterTest {

    static final float LAT = 51.0f;
    static final float LNG = -0.1f;
    static final float ACCURACY = 1200.4f;
    static final float DELTA = 0.01f;

    public JSONObject getExpectedLocation() throws JSONException {
        /*
            Expected JSON as per:
               https://developers.google.com/maps/documentation/business/geolocation/#responses

            {
              "location": {
                "lat": 51.0,
                "lng": -0.1
              },
              "accuracy": 1200.4
            }

         */

        JSONObject expected = new JSONObject();
        JSONObject blob = new JSONObject();
        expected.put("location", blob);
        blob.put("lat", LAT);
        blob.put("lng", LNG);
        expected.put("accuracy", ACCURACY);
        return expected;
    }

    @Test
    public void testLocationAdaption() throws JSONException {
        JSONObject expected = getExpectedLocation();
        assertEquals(LocationAdapter.getLat(expected), LAT, DELTA);
        assertEquals(LocationAdapter.getLng(expected), LNG, DELTA);
        assertEquals(LocationAdapter.getAccuracy(expected), ACCURACY, DELTA);
    }
}
