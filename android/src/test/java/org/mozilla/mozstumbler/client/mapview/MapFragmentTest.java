/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.mapview;

import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.robolectric.util.FragmentTestUtil.startFragment;

@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
public class MapFragmentTest {

    private static final String LOG_TAG = AppGlobals.makeLogTag(MapFragmentTest.class);

    @Test
    @Config(shadows = {CustomShadowConnectivityManager.class})
    public void testMapNetworkConnectionChanged() {
        MapFragment mapFragment = spy(MapFragment.class);

        // skip most of the map setup
        doNothing().when(mapFragment).doOnCreateView(any(Bundle.class));

        // disable this method
        doNothing().when(mapFragment).getUrlAndInit();

        // disable setHighBandwidthMap method from doing anything,
        // we just care that it gets called and we want to verify the argument being passed in.
        doNothing().when(mapFragment).setHighBandwidthMap(Mockito.anyBoolean());

        // Disable the onResume's main chunk of code - too much going on in there and
        // we just don't care about it
        doNothing().when(mapFragment).doOnResume();

        startFragment(mapFragment);

        mapFragment.mapNetworkConnectionChanged();

        // now verify that a network connection changed just shows a no map available message
        verify(mapFragment).showMapNotAvailableMessage(MapFragment.NoMapAvailableMessage.eNoMapDueToNoInternet);

        // Check that the setHighBandwidthMap method was called. This
        // never happened before
        // https://github.com/mozilla/MozStumbler/pull/1370 was
        // merged.
        verify(mapFragment).setHighBandwidthMap(false);
    }

}
