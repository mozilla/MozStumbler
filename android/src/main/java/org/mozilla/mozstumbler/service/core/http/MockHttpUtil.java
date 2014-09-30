/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.core.http;

import android.os.Build;

import org.mozilla.mozstumbler.service.core.logging.Log;
import org.mozilla.mozstumbler.service.utils.Zipper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class MockHttpUtil implements IHttpUtil {
    @Override
    public String getUrlAsString(URL url) throws IOException {
        return "abc";
    }

    @Override
    public String getUrlAsString(String url) throws IOException {
        return "abc";
    }

    @Override
    public InputStream getUrlAsStream(String url) throws IOException {
        return new ByteArrayInputStream(new String("abc").getBytes());
    }

    @Override
    public File getUrlAsFile(URL url, File file) throws IOException {
        Writer writer = new FileWriter(file);
        writer.write(getUrlAsString(url));
        return file;
    }

    @Override
    public IResponse post(String urlString, byte[] data, Map<String, String> headers, boolean precompressed, MLS mls) {
        return null;
    }

}
