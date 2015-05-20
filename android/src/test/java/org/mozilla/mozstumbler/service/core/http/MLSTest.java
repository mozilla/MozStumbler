/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.core.http;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.HashMap;

import static junit.framework.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
public class MLSTest {

    @Test
    public void testSubmitIgnoresFilePaths() {
        MLS mls = new MLS();
        byte[] pathData = "/data/data/org.fdroid.fdroid/files/fdroid/repo/index.html".getBytes();
        IResponse response = mls.submit(pathData, new HashMap<String, String>(), false);
        assertNull(response);
    }
}
