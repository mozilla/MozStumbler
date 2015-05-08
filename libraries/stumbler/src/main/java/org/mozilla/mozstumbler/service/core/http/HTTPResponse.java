package org.mozilla.mozstumbler.service.core.http;

import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HTTPResponse implements IResponse {

    private static final String LOG_TAG = LoggerUtil.makeLogTag(HTTPResponse.class);

    private final int statusCode;
    private final byte[] bodyBytes;
    private final int bytesSent;
    private final Map<String, List<String>> headers;

    public HTTPResponse(int responseCode, int txByteLength)
    {
        this(responseCode, new HashMap<String, List<String>>(), new byte[]{}, txByteLength);
    }

    public HTTPResponse(int responseCode, Map<String, List<String>> headerFields, byte[] contentBody, int txByteLength) {
        statusCode = responseCode;
        bodyBytes = contentBody;
        bytesSent = txByteLength;
        if (headerFields == null) {
            headerFields = new HashMap<String, List<String>>();
        }
        headers = headerFields;
    }

    public boolean isErrorCode400BadRequest() {
        return 400 == statusCode;
    }

    public boolean isErrorCode4xx() {
        return statusCode / 100 == 4;
    }

    @Override
    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    @Override
    public String getFirstHeader(String key) {
        List<String> matches = headers.get(key);
        if (matches != null) {
            return matches.get(0);
        }
        return null;
    }

    public boolean isSuccessCode2XX() {
        return statusCode / 100 == 2;
    }

    public int httpStatusCode() {
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
