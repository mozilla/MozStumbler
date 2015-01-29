/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.svclocator.services.log;

public class LoggerUtil {
    public static final String LOG_PREFIX = "Stumbler_";

    public static String makeLogTag(Class<?> cls) {
        String name = cls.getSimpleName();
        final int maxLen = 23 - LOG_PREFIX.length();
        if (name.length() > maxLen) {
            name = name.substring(name.length() - maxLen, name.length());
        }
        return LOG_PREFIX + name;
    }
}
