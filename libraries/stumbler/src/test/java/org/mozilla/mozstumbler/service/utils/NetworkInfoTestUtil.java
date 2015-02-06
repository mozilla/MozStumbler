/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.utils;

/*
 Just a test utility to manually set the global instance
 */
public class NetworkInfoTestUtil {

    public static void setNetworkInfo(NetworkInfo info) {
        NetworkInfo.instance = info;
    }


}
