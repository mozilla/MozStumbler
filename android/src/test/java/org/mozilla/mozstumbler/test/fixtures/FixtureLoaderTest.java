/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.mozilla.mozstumbler.test.fixtures;

import org.junit.Test;

import java.io.IOException;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;

public class FixtureLoaderTest {

    @Test
    public void testLoadPNG() throws IOException {
        // This is just a basic test to see that the FixtureLoader is behaving correctly.
        assertNotNull(FixtureLoader.loadResource("org/mozilla/mozstumbler/test/fixtures/2976.png.merged"));
        assertNull(FixtureLoader.loadResource("org/mozilla/mozstumbler/test/fixtures/not_a_real_tile"));

    }

}
