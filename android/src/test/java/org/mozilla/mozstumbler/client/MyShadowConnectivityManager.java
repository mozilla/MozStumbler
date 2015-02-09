/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import org.mockito.Mockito;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowConnectivityManager;

import static org.mockito.Mockito.when;

@Implements(ConnectivityManager.class)
public class MyShadowConnectivityManager extends ShadowConnectivityManager {

    private Boolean connectedFlag = false;

    @Implementation
    public NetworkInfo getActiveNetworkInfo() {

        // NetworkInfo doesn't provide a proper constructor, so we're just going to clobber
        // that with mockito.
        final NetworkInfo networkInfo = Mockito.mock(NetworkInfo.class);
        when(networkInfo.isConnected()).thenReturn(connectedFlag);
        when(networkInfo.getType()).thenReturn(ConnectivityManager.TYPE_DUMMY);
        return networkInfo;
    }

    public void setConnectedFlag(boolean flag) {
        connectedFlag = flag;
    }


}
