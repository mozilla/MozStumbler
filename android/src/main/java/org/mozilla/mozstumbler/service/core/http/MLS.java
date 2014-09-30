/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.core.http;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.Prefs;

import java.util.Map;

public class MLS implements ILocationService {

    private static final String SEARCH_URL = "https://location.services.mozilla.com/v1/search";
    private static final String SUBMIT_URL = "https://location.services.mozilla.com/v1/submit";

    private static final String LOG_TAG = AppGlobals.LOG_PREFIX + MLS.class.getSimpleName();

    public static final String NICKNAME_HEADER = "X-Nickname";
    public static final String EMAIL_HEADER = "X-Email";
    final IHttpUtil httpDelegate;

    private String mozApiKey;

    public MLS(IHttpUtil httpUtil) {
        mozApiKey = Prefs.getInstance().getMozApiKey();
        httpDelegate = httpUtil;
    }

    public IResponse submit(byte[] data, Map<String, String> headers, boolean precompressed) {
        return httpDelegate.post(SUBMIT_URL + "?key=" + mozApiKey, data, headers, precompressed, this);
    }

    public IResponse search(byte[] data, Map<String, String> headers, boolean precompressed) {
        return httpDelegate.post(SEARCH_URL + "?key=" + mozApiKey, data, headers, precompressed, this);
    }



}
