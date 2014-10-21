/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */



package org.mozilla.mozstumbler.service.core.http;


public interface IResponse {
    public int httpResponse();

    public String body();

    public int bytesSent();

    public boolean isSuccessCode2XX();
    public boolean isErrorCode4xx();

    public boolean isErrorCode400BadRequest();

}
