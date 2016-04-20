/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.core.http;

import android.os.Build;

import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.core.logging.ClientLog;
import org.mozilla.mozstumbler.service.utils.Zipper;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.log.ILogger;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

public class HttpUtil implements IHttpUtil {
    public final static int IO_BUFFER_SIZE = 8 * 1024;

    private static final ILogger Log = (ILogger) ServiceLocator.getInstance().getService(ILogger.class);
    private static final String LOG_TAG = LoggerUtil.makeLogTag(HttpUtil.class);
    private static final String USER_AGENT_HEADER = "User-Agent";
    private final String userAgent;

    @SuppressWarnings("unused")
    public HttpUtil() {
        // This is instantiated through reflection for the ServiceLocator
        this((Prefs.getInstanceWithoutContext() != null) ?
                Prefs.getInstanceWithoutContext().getUserAgent() : "user-agent-not-set-properly");
    }

    public HttpUtil(String ua) {
        userAgent = ua;
    }

    private URLConnection mozOpenConnection(URL l_url) throws IOException {
        try {
            if (l_url.getProtocol().equals("http")) {
                return l_url.openConnection();
            }
            // Try to use the highest possible TLS protocol that this platform supports.
            SSLContext sslcontext = SSLContext.getInstance(GlobalConstants.DEFAULT_PROTOCOLS[0]);
            sslcontext.init(null,
                    null,
                    null);

            NoSSLv3SocketFactory factory = new NoSSLv3SocketFactory(sslcontext.getSocketFactory());
            HttpsURLConnection.setDefaultSSLSocketFactory(factory);
            return l_url.openConnection();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error initializing context", e);
            return null ;
        }
    }

    public String getUrlAsString(URL url) throws IOException {
        InputStream stream = null;
        try {
            URLConnection connection = mozOpenConnection(url);
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
        URLConnection connection = mozOpenConnection(new URL(url));
        return connection.getInputStream();
    }


    public File getUrlAsFile(URL url, File file) throws IOException {
        final int bufferLength = 8192;
        final byte[] buffer = new byte[bufferLength];
        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            URLConnection connection = mozOpenConnection(url);
            inputStream = connection.getInputStream();
            outputStream = new FileOutputStream(file);
            for (; ; ) {
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

    public IResponse get(String urlString, Map<String, String> headers) {
        String HTTP_METHOD = "GET";
        return getHttpResponse(urlString, headers, HTTP_METHOD);
    }

    private IResponse getHttpResponse(String urlString, Map<String, String> headers, String HTTP_METHOD) {
        URL url = null;
        HttpURLConnection httpURLConnection = null;

        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            ClientLog.e(LOG_TAG, "Bad URL", e);
            return new HTTPResponse(404, 0);
        }

        if (headers == null) {
            headers = new HashMap<String, String>();
        }

        try {
            httpURLConnection = (HttpURLConnection) mozOpenConnection(url);
            if (HTTP_METHOD.toUpperCase().equals("HEAD")) {
                httpURLConnection.setInstanceFollowRedirects(false);
            }
            httpURLConnection.setConnectTimeout(5000); // set timeout to 5 seconds
            httpURLConnection.setRequestMethod(HTTP_METHOD);
            httpURLConnection.setRequestProperty(USER_AGENT_HEADER, userAgent);
        } catch (IOException e) {
            ClientLog.e(LOG_TAG, "Couldn't open a connection: ", e);
            return new HTTPResponse(598, 0);
        }

        // Workaround for a bug in Android mHttpURLConnection. When the library
        // reuses a stale connection, the connection may fail with an EOFException
        // http://stackoverflow.com/questions/15411213/android-httpsurlconnection-eofexception/17791819#17791819
        if (Build.VERSION.SDK_INT > 13 && Build.VERSION.SDK_INT < 19) {
            httpURLConnection.setRequestProperty("Connection", "Close");
        }

        // Set headers
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            httpURLConnection.setRequestProperty(entry.getKey(), entry.getValue());
        }

        try {
            return new HTTPResponse(httpURLConnection.getResponseCode(),
                    httpURLConnection.getHeaderFields(),
                    getContentBody(httpURLConnection),
                    0);
        } catch (IOException e) {
            ClientLog.e(LOG_TAG, "Networking error", e);
        } finally {
            httpURLConnection.disconnect();
        }
        return new HTTPResponse(598, 0);
    }

    @Override
    public IResponse head(String urlString, Map<String, String> headers) {
        String HTTP_METHOD = "HEAD";
        return getHttpResponse(urlString, headers, HTTP_METHOD);
    }

    @Override
    public IResponse post(String urlString, byte[] data, Map<String, String> headers, boolean precompressed) {

        URL url = null;
        HttpURLConnection httpURLConnection = null;

        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid URL", e);
        }

        if (data == null) {
            throw new IllegalArgumentException("Data must be not null");
        }

        if (headers == null) {
            headers = new HashMap<String, String>();
        }


        try {
            httpURLConnection = (HttpURLConnection) mozOpenConnection(url);
            httpURLConnection.setConnectTimeout(5000); // set timeout to 5 seconds
            // HttpURLConnection and Java are braindead.
            // http://stackoverflow.com/questions/8587913/what-exactly-does-urlconnection-setdooutput-affect
            httpURLConnection.setDoOutput(true);
            httpURLConnection.setRequestMethod("POST");
        } catch (Exception e) {
            ClientLog.e(LOG_TAG, "Couldn't open a connection: ", e);
            return null;
        }

        httpURLConnection.setDoOutput(true);
        httpURLConnection.setRequestProperty(USER_AGENT_HEADER, userAgent);
        httpURLConnection.setRequestProperty("Content-Type", "application/json");

        // Workaround for a bug in Android mHttpURLConnection. When the library
        // reuses a stale connection, the connection may fail with an EOFException
        // http://stackoverflow.com/questions/15411213/android-httpsurlconnection-eofexception/17791819#17791819
        if (Build.VERSION.SDK_INT > 13 && Build.VERSION.SDK_INT < 19) {
            httpURLConnection.setRequestProperty("Connection", "Close");
        }

        // Set headers
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            httpURLConnection.setRequestProperty(entry.getKey(), entry.getValue());
        }

        byte[] wire_data = data;
        if (!precompressed) {
            wire_data = Zipper.zipData(data);
            if (wire_data != null) {
                httpURLConnection.setRequestProperty("Content-Encoding", "gzip");
            } else {
                ClientLog.w(LOG_TAG, "Couldn't compress data, falling back to raw data.");
                wire_data = data;
            }
        } else {
            httpURLConnection.setRequestProperty("Content-Encoding", "gzip");
        }

        httpURLConnection.setFixedLengthStreamingMode(wire_data.length);
        try {
            OutputStream out = new BufferedOutputStream(httpURLConnection.getOutputStream());
            out.write(wire_data);
            out.flush();
            return new HTTPResponse(httpURLConnection.getResponseCode(),
                    httpURLConnection.getHeaderFields(),
                    getContentBody(httpURLConnection),
                    wire_data.length);
        } catch (IOException e) {
            ClientLog.e(LOG_TAG, "post error", e);
        } finally {
            httpURLConnection.disconnect();
        }
        return null;
    }

    private byte[] getContentBody(HttpURLConnection httpURLConnection) throws IOException {
        InputStream in = null;
        try {
            in = new BufferedInputStream(httpURLConnection.getInputStream());
        } catch (Exception ex) {
            in = httpURLConnection.getErrorStream();
        }
        if (in == null) {
            return new byte[]{};
        }

        ByteArrayOutputStream dataStream = null;
        OutputStream out = null;

        try {
            dataStream = new ByteArrayOutputStream();
            out = new BufferedOutputStream(dataStream, IO_BUFFER_SIZE);
            copyStream(in, out);
            out.flush();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ioEx) {
                    ClientLog.e(LOG_TAG, "Error closing tile output stream.", ioEx);
                }
            }
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ioEx) {
                    ClientLog.e(LOG_TAG, "Error closing tile output stream.", ioEx);
                }
            }
        }

        return dataStream.toByteArray();
    }

    /**
     * Copy the content of the input stream into the output stream, using a temporary byte array
     * buffer whose size is defined by {@link #IO_BUFFER_SIZE}.
     *
     * @param in  The input stream to copy from.
     * @param out The output stream to copy to.
     * @return the total length copied
     * @throws IOException If any error occurs during the copy.
     */
    private static long copyStream(final InputStream in, final OutputStream out) throws IOException {
        long length = 0;
        final byte[] b = new byte[IO_BUFFER_SIZE];
        int read;
        while ((read = in.read(b)) != -1) {
            out.write(b, 0, read);
            length += read;
        }
        return length;
    }
}
