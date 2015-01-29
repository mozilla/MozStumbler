/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.mapview;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import org.mockito.Mockito;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowConnectivityManager;

import static org.mockito.Mockito.when;

/*
  This custom shadow is only used to check if the network connectivity has changed.

  It's used by MapFragmentTest.
 */
@Implements(ConnectivityManager.class)
public class CustomShadowConnectivityManager extends ShadowConnectivityManager {

    @Implementation
    public NetworkInfo getActiveNetworkInfo() {

        // NetworkInfo doesn't provide a proper constructor, so we're just going to clobber
        // that with mockito.
        final NetworkInfo networkInfo = Mockito.mock(NetworkInfo.class);
        when(networkInfo.isConnected()).thenReturn(false);
        when(networkInfo.getType()).thenReturn(ConnectivityManager.TYPE_DUMMY);

        return networkInfo;
    }
}
