/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.test.fixtures;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class FixtureLoader {

    /*
     This class will load test fixtures from the resource directory with a root directory
     in android/src/main/res/org/mozilla/test/fixtures
     */

    public static byte[] loadFixture(String path) throws IOException {
        InputStream is = FixtureLoader.class.getResourceAsStream(path);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int nRead;
        byte[] data = new byte[16384];

        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        buffer.flush();

        return buffer.toByteArray();
    }
}
