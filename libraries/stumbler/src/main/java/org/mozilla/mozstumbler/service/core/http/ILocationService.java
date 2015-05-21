/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.core.http;

import org.json.JSONObject;

import java.util.Map;

public interface ILocationService extends ISubmitService {
    IResponse search(JSONObject mlsGeoLocate, Map<String, String> headers, boolean precompressed);
}
