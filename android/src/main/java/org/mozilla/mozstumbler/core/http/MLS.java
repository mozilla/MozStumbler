/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.core.http;

import android.os.Build;
import android.util.Log;

import org.mozilla.mozstumbler.client.ClientPrefs;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.utils.Zipper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class MLS implements ILocationService {

    private static final String SEARCH_URL = "https://location.services.mozilla.com/v1/search";
    private static final String SUBMIT_URL = "https://location.services.mozilla.com/v1/submit";

    private static final String LOG_TAG = AppGlobals.LOG_PREFIX + MLS.class.getSimpleName();

    private static final String NICKNAME_HEADER = "X-Nickname";
    private static final String EMAIL_HEADER = "X-Email";
    private static final String USER_AGENT_HEADER = "User-Agent";

    private String userAgent;
    private String mozApiKey;

    public MLS() {
        initUserAgent(null);
        initApiKey(null);
    }

    public MLS(String ua, String apiKey) {
        initUserAgent(ua);
        initApiKey(apiKey);
    }

    private void initUserAgent(String ua) {
        if (ua == null) {
            userAgent = ClientPrefs.getInstance().getUserAgent();
        } else {
            userAgent = ua;
        }
    }

    private void initApiKey(String apiKey) {
        if (mozApiKey == null) {
            mozApiKey = Prefs.getInstance().getMozApiKey();
        } else {
            mozApiKey = apiKey;
        }

    }

    public IResponse submit(byte[] data, Map<String, String> headers, boolean precompressed) {
        return post(SUBMIT_URL + "?key=" + mozApiKey, data, headers, precompressed);
    }

    public IResponse search(byte[] data, Map<String, String> headers, boolean precompressed) {
        return post(SEARCH_URL + "?key=" + mozApiKey, data, headers, precompressed);
    }

    /*
     POST data

     This method will automatically compress any data using gzip

     Return a response object from the server
     On IOException, this will return null.
     */

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
            httpURLConnection = (HttpURLConnection) url.openConnection();
            httpURLConnection.setRequestMethod("POST");
        } catch (IOException e) {
            Log.e(LOG_TAG, "Couldn't open a connection: " + e);
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
                Log.w(LOG_TAG, "Couldn't compress data, falling back to raw data.");
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
                    getContentBody(httpURLConnection),
                    wire_data.length);
        } catch (IOException e) {
            Log.e(LOG_TAG, "", e);
        } finally {
            if (httpURLConnection != null) {
                httpURLConnection.disconnect();
            }
        }
        return null;
    }

    private String getContentBody(HttpURLConnection httpURLConnection) throws IOException {
        String contentBody;
        InputStream in = new BufferedInputStream(httpURLConnection.getInputStream());
        BufferedReader r = new BufferedReader(new InputStreamReader(in));
        String line;
        StringBuilder total = new StringBuilder(in.available());
        while ((line = r.readLine()) != null) {
            total.append(line);
        }
        r.close();
        in.close();
        contentBody = total.toString();
        return contentBody;
    }
}
