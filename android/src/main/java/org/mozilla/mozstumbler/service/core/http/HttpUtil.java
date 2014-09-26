/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.core.http;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;


public class HttpUtil implements IHttpUtil {

    public HttpUtil(){};

    private URLConnection openConnectionWithProxy(URL url) throws IOException {
        Proxy proxy = Proxy.NO_PROXY;

        ProxySelector proxySelector = ProxySelector.getDefault();
        if (proxySelector != null) {
            URI uri;
            try {
                uri = url.toURI();
            } catch (URISyntaxException e) {
                IOException ioe = new IOException(url.toString());
                ioe.initCause(e);
                throw ioe;
            }

            List<Proxy> proxies = proxySelector.select(uri);
            if (proxies != null && !proxies.isEmpty()) {
                proxy = proxies.get(0);
            }
        }

        return url.openConnection(proxy);
    }

    public String getUrlAsString(URL url) throws IOException {
        InputStream stream = null;
        try {
            URLConnection connection = openConnectionWithProxy(url);
            stream = connection.getInputStream();
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new InputStreamReader(stream));
                return reader.readLine();
            } finally {
                if (reader != null) {
                    reader.close();
                }
            }
        } finally {
            if (stream != null) {
                stream.close();
            }
        }
    }

    @Override
    public String getUrlAsString(String url) throws IOException {
        return getUrlAsString(new URL(url));
    }

    @Override
    public InputStream getUrlAsStream(String url) throws IOException {
        return new URL(url).openStream();
    }


    public File getUrlAsFile(URL url, File file) throws IOException {
        final int bufferLength = 8192;
        final byte[] buffer = new byte[bufferLength];
        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            URLConnection connection = openConnectionWithProxy(url);
            inputStream = connection.getInputStream();
            outputStream = new FileOutputStream(file);
            for (;;) {
                int readLength = inputStream.read(buffer, 0, bufferLength);
                if (readLength == -1) {
                    return file;
                }
                outputStream.write(buffer, 0, readLength);
            }
        } finally {
            if (outputStream != null) {
                outputStream.close();
            }
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }
}
