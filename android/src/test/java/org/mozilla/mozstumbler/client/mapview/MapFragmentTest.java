/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.mapview;

import android.content.Context;
import android.support.v4.app.FragmentActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.util.FragmentTestUtil.startFragment;

@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
public class MapFragmentTest {

    private static final String LOG_TAG = AppGlobals.makeLogTag(MapFragmentTest.class);

    @Test
    @Config(shadows = {CustomShadowConnectivityManager.class})
    public void testMapNetworkConnectionChanged() {
        MapFragment mapFragment = new MapFragment();

        startFragment(mapFragment);

    }

}