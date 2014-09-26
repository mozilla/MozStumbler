/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */


package org.mozilla.mozstumbler.service.core.http;

import java.util.Map;

public interface ILocationService {
    // Submit data to MLS
    // Errors will return
    public IResponse submit(byte[] data, Map<String, String> headers, boolean precompressed);
    public IResponse search(byte[] data, Map<String, String> headers, boolean precompressed);



}
