package org.mozilla.mozstumbler.service.core.http;

import org.mozilla.mozstumbler.service.core.logging.Log;
import org.mozilla.mozstumbler.service.AppGlobals;

public class HTTPResponse implements IResponse {

    private static final String LOG_TAG = AppGlobals.LOG_PREFIX + HTTPResponse.class.getSimpleName();

    private final int statusCode;
    private final String body;
    private final int bytesSent;

    public HTTPResponse(int responseCode, String contentBody, int txByteLength) {
        statusCode = responseCode;
        body = contentBody;
        bytesSent = txByteLength;
        Log.i(LOG_TAG, "HTTP Status: " + Integer.toString(statusCode) +
                ", Bytes Sent: " + Integer.toString(bytesSent) +
                ", Bytes received: " + Integer.toString(body.length()));
    }

    public boolean isErrorCode4xx(){
        return statusCode / 100 == 4;
    }

    public boolean isSuccessCode2XX() {
        return statusCode / 100 == 2;
    }

    public int httpResponse() {
        return statusCode;
    }

    public String body() {
        return body;
    }

    @Override
    public int bytesSent() {
        return bytesSent;
    }

}
