/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.core.http;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(emulateSdk = 18)
public class MLSTest {

    @Before
    public void setUp() {


    }


    @Test
    /*

    {"items": [
    {
        "latitude": -22.7,
        "longitude": -43.4,
        "wifi": [
            {
                "macAddress": "01:23:45:67:89:ab",
            },
            {
                "macAddress": "23:45:67:89:ab:cd"
            }
        ]
    },
    {
        "latitude": -22.6,
        "longitude": -43.4,
        "radioType": "gsm",
        "cellTowers": [
            {
                "cellId": 12345,
                "locationAreaCode": 2,
                "mobileCountryCode": 208,
                "mobileNetworkCode": 1,
                "age": 3
            }
        ]
    }
]}
     */
    public void testGeoSubmit() {

    }
}
