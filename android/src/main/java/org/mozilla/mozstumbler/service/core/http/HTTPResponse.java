package org.mozilla.mozstumbler.service.core.http;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.core.logging.Log;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;

public class HTTPResponse implements IResponse {

    private static final String LOG_TAG = AppGlobals.LOG_PREFIX + HTTPResponse.class.getSimpleName();

    private final int statusCode;
    private final byte[] bodyBytes;
    private final int bytesSent;
    private final Map<String, List<String>> headers;

    public HTTPResponse(int responseCode, Map<String, List<String>> headerFields, byte[] contentBody, int txByteLength) {
        statusCode = responseCode;
        bodyBytes = contentBody;
        bytesSent = txByteLength;
        headers = headerFields;
        Log.d(LOG_TAG, "HTTP Status: " + Integer.toString(statusCode) +
                ", Bytes Sent: " + Integer.toString(bytesSent) +
                ", Bytes received: " + Integer.toString(bodyBytes.length));
    }

    public boolean isErrorCode400BadRequest(){
        return 400 == statusCode;
    }

    public boolean isErrorCode4xx(){
        return statusCode / 100 == 4;
    }

    @Override
    public String getFirstHeader(String key) {
        return headers.get(key).get(0);
    }

    public boolean isSuccessCode2XX() {
        return statusCode / 100 == 2;
    }

    public int httpResponse() {
        return statusCode;
    }

    public String body() {
        try {
            return new String(bodyBytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    @Override
    public byte[] bodyBytes() {
        return bodyBytes;
    }

    @Override
    public int bytesSent() {
        return bytesSent;
    }

}
