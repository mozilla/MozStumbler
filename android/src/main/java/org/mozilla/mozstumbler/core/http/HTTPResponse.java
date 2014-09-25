package org.mozilla.mozstumbler.core.http;

public class HTTPResponse implements IResponse {

    private final int statusCode;
    private final String body;
    private final int bytesSent;

    public HTTPResponse(int responseCode, String contentBody, int txByteLength) {
        statusCode = responseCode;
        body = contentBody;
        bytesSent = txByteLength;
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
