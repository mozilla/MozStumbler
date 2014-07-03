/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service;

import android.os.Build;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.zip.GZIPOutputStream;

public abstract class AbstractCommunicator {

    private static final String LOGTAG = AbstractCommunicator.class.getName();
    private static final String NICKNAME_HEADER = "X-Nickname";
    private static final String USER_AGENT_HEADER = "User-Agent";
    private HttpURLConnection httpURLConnection;
    private final String mUserAgent;

    public abstract String getUrlString();
    public abstract int getCorrectResponse();
    public abstract boolean cleanSend(byte[] data);

    public String getNickname() {
        return null;
    }

    public AbstractCommunicator() {
        String appName = SharedConstants.appName;
        // "MozStumbler/X.Y.Z"
        mUserAgent = appName + '/' + SharedConstants.appVersionName;
    }

    private void setHeaders() {
        try {
            URL url = new URL(getUrlString() + "?key=" + SharedConstants.mozillaApiKey);
            httpURLConnection = (HttpURLConnection) url.openConnection();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        } catch (IOException e) {
            Log.e(LOGTAG, "Couldn't open a connection: " + e);
        }
        httpURLConnection.setDoOutput(true);
        httpURLConnection.setRequestProperty(USER_AGENT_HEADER, mUserAgent);
        httpURLConnection.setRequestProperty("Content-Type", "application/json");

        // Workaround for a bug in Android HttpURLConnection. When the library
        // reuses a stale connection, the connection may fail with an EOFException
        if (Build.VERSION.SDK_INT > 13 && Build.VERSION.SDK_INT < 19) {
            httpURLConnection.setRequestProperty("Connection", "Close");
        }
        String nickname = getNickname();
        if (nickname != null) {
            httpURLConnection.setRequestProperty(NICKNAME_HEADER, nickname);
        }
    }

    private byte[] zipData(byte[] data) throws IOException {
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        GZIPOutputStream gstream = null;
        byte[] output;
        try {
            gstream = new GZIPOutputStream(os);
            gstream.write(data);
            gstream.finish();
            output = os.toByteArray();
        } finally {
            os.close();
            if (gstream != null) {
                gstream.close();
            }
        }
        httpURLConnection.setRequestProperty("Content-Encoding","gzip");
        return output;
    }

    private void sendData(byte[] data) throws IOException{
        httpURLConnection.setFixedLengthStreamingMode(data.length);
        OutputStream out = new BufferedOutputStream(httpURLConnection.getOutputStream());
        out.write(data);
        out.flush();
        int code = httpURLConnection.getResponseCode();
        if (code != getCorrectResponse()) {
            throw new HttpErrorException(code);
        }
    }

    public void send(byte[] data) throws IOException {
        setHeaders();
        try {
            sendData(zipData(data));
        } catch (IOException e) {
            Log.e(LOGTAG, "Couldn't compress and send data, falling back to plain-text: ", e);
            close();
            setHeaders();
            sendData(data);
        }
    }

    public InputStream getInputStream() {
        try {
            return httpURLConnection.getInputStream();
        } catch (IOException e) {
            return httpURLConnection.getErrorStream();
        }
    }

    public void close() {
        if (httpURLConnection == null) {
            return;
        }
        httpURLConnection.disconnect();
        httpURLConnection = null;
    }

    public static class HttpErrorException extends IOException {
        private static final long serialVersionUID = -5404095858043243126L;
        public final int responseCode;

        public HttpErrorException(int responseCode) {
            super();
            this.responseCode = responseCode;
        }

        public boolean isTemporary() {
            return responseCode >= 500 && responseCode <= 599;
        }

    }
}
