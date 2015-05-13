/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.core.http;

import org.json.JSONObject;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;

import java.util.Map;

public class MLSLocationService implements ILocationService {
    public static final String NICKNAME_HEADER = "X-Nickname";
    public static final String EMAIL_HEADER = "X-Email";
    private static final String SEARCH_URL = "https://location.services.mozilla.com/v1/geolocate";
    private static final String SUBMIT_URL = "https://location.services.mozilla.com/v1/geosubmit";
    final IHttpUtil httpDelegate = (IHttpUtil) ServiceLocator.getInstance().getService(IHttpUtil.class);

    private String mozApiKey;

    public MLSLocationService() {}

    public IResponse submit(byte[] data, Map<String, String> headers, boolean precompressed) {
        if (mozApiKey == null) {
            Prefs p = Prefs.getInstanceWithoutContext();
            if (p != null) {
                mozApiKey = p.getMozApiKey();
            }
        }

        return httpDelegate.post(SUBMIT_URL + "?key=" + mozApiKey, data, headers, precompressed);
    }


    public IResponse search(JSONObject mlsGeoLocate, Map<String, String> headers, boolean precompressed) {
        if (mozApiKey == null) {
            Prefs p = Prefs.getInstanceWithoutContext();
            if (p != null) {
                mozApiKey = p.getMozApiKey();
            }
        }
        return httpDelegate.post(SEARCH_URL + "?key=" + mozApiKey,
                mlsGeoLocate.toString().getBytes(),
                headers,
                precompressed);
    }
}
