/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.utils;

import android.util.Log;

import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class ShaUtil {
    private static final String LOG_TAG = LoggerUtil.makeLogTag(ShaUtil.class);

    public static String sha1_hex_digest(String email) {
        String result = "";

        MessageDigest md;
        byte[] b;

        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            Log.e(LOG_TAG, "Couldn't obtain SHA1 digest: " + e);
            return null;
        }

        b = md.digest(email.getBytes());

        for (byte bi : b) {
            result += Integer.toString((bi & 0xff) + 0x100, 16).substring(1);
        }
        return result;
    }
}
