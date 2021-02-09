/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.core.http;

import android.os.Build;

public class GlobalConstants {
    /*
     * The DEFALUT_CIPHER_SUITES and DEFAULT_PROTOCOLS is copied from
     * firefox sync on android.
     *
     * Original source can be found at: 
     * 
     * https://github.com/mozilla-services/android-sync/blob/a948def/src/main/java/org/mozilla/gecko/background/common/GlobalConstants.java
     */

    // Acceptable cipher suites.
    /**
     * We support only a very limited range of strong cipher suites and protocols:
     * no SSLv3 or TLSv1.0 (if we can), no DHE ciphers that might be vulnerable to Logjam
     * (https://weakdh.org/), no RC4.
     * <p/>
     * Backstory: Bug 717691 (we no longer support Android 2.2, so the name
     * workaround is unnecessary), Bug 1081953, Bug 1061273, Bug 1166839.
     * <p/>
     * See <http://developer.android.com/reference/javax/net/ssl/SSLSocket.html> for
     * supported Android versions for each set of protocols and cipher suites.
     */
    public static final String[] DEFAULT_CIPHER_SUITES;
    public static final String[] DEFAULT_PROTOCOLS;

    static {
        if (Build.VERSION.SDK_INT >= 29) {
            DEFAULT_CIPHER_SUITES = new String[]
                    {
                            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",     // 20+
                            "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",        // 11+
                            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",     // 20+
                            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",        // 11+
                    };
        } else if (Build.VERSION.SDK_INT >= 20) {
            DEFAULT_CIPHER_SUITES = new String[]
                    {
                            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",     // 20+
                            "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384",     // 20+
                            "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",        // 11+
                            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",     // 20+
                            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",     // 20+
                            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",        // 11+
                    };
        } else if (Build.VERSION.SDK_INT >= 11) {
            DEFAULT_CIPHER_SUITES = new String[]
                    {
                            "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",        // 11+
                            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",        // 11+
                    };
        } else {       // 9+
            // Fall back to the only half-decent cipher suite supported on Gingerbread.
            DEFAULT_CIPHER_SUITES = new String[]
                    {
                            "TLS_DHE_RSA_WITH_AES_256_CBC_SHA"           // 9+
                    };
        }

        if (Build.VERSION.SDK_INT >= 16) {
            DEFAULT_PROTOCOLS = new String[]
                    {
                            "TLSv1.2",
                            "TLSv1.1",
                    };
        } else {
            // Fall back to TLSv1 if there's nothing better.
            DEFAULT_PROTOCOLS = new String[]
                    {
                            "TLSv1",
                    };
        }
    }
}
