/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.utils;

import android.content.Context;
import android.net.ConnectivityManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.robolectric.Robolectric.shadowOf;


@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
public class NetworkInfoTest {

    Context ctx;

    @Before
    public void setup() {
        ctx = Robolectric.application;
    }

    @Test
    public void testIsConnectedNoConnManager() {
        // Test that the isConnected method doesn't bomb out without a connection manager

        // This first instance will have no connection manager
        NetworkInfo ni = new NetworkInfo(ctx);
        ni.mConnectivityManager = null;
        assertFalse(ni.isConnected());
    }

    @Test
    @Config(shadows = {MyShadowConnectivityManager.class})
    public void testIsConnectedWithGlobal() {
        NetworkInfo ni = new NetworkInfo(ctx);

        ConnectivityManager connectivityManager = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        MyShadowConnectivityManager shadowConnManager = (MyShadowConnectivityManager) shadowOf(connectivityManager);

        assertFalse(ni.isConnected());

        shadowConnManager.setConnectedFlag(true);
        assertTrue(ni.isConnected());
    }


}
