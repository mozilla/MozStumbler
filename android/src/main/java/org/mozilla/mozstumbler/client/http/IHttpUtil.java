/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.http;

import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * Created by victorng on 2014-09-19.
 */
public interface IHttpUtil {
    public String getUrlAsString(URL url) throws IOException ;
    public File getUrlAsFile(URL url, File file) throws IOException;
}
