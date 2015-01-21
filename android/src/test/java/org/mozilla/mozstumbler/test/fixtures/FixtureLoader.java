/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.test.fixtures;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class FixtureLoader {
    /*
     Load a test resource as an array of bytes.

     Returns null on error loading the test fixture.
     */
    public static byte[] loadResource(String path)  {
        int nRead;
        byte[] data = new byte[16384];
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        try {
            InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }

            buffer.flush();

            return buffer.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}
