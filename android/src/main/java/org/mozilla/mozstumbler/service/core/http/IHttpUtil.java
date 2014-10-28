/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.core.http;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;

public interface IHttpUtil {
    public String getUrlAsString(URL url) throws IOException;

    public String getUrlAsString(String url) throws IOException;

    public InputStream getUrlAsStream(String url) throws IOException;

    public File getUrlAsFile(URL url, File file) throws IOException;

    /*
     POST data

    This method will automatically compress any data using gzip

    Return a response object from the server
    On IOException, this will return null.
    */
    IResponse post(String urlString, byte[] data, Map<String, String> headers, boolean precompressed);

    IResponse get(String urlString, Map<String, String> headers);

    IResponse head(String latestUrl,  Map<String, String> headers);
}
