/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */


package org.mozilla.mozstumbler.service.core.http;

import java.util.List;
import java.util.Map;

public interface IResponse {

    public static final int HTTP_OK = 200;
    public static final int HTTP_BAD_REQUEST = 400;
    public static final int HTTP_ERROR = 500;


    public int httpStatusCode();

    public String body();

    public byte[] bodyBytes();

    public int bytesSent();

    public boolean isSuccessCode2XX();

    public boolean isErrorCode4xx();

    Map<String, List<String>> getHeaders();

    public String getFirstHeader(String key);

    public boolean isErrorCode400BadRequest();
}
