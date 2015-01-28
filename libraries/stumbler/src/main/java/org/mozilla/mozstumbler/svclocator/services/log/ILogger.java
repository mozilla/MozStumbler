/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.svclocator.services.log;

public interface ILogger {
    public abstract void w(String logTag, String s);

    public abstract String e(String logTag, String s, Throwable e);

    public abstract void i(String logTag, String s);

    public abstract void d(String logTag, String s);
}
