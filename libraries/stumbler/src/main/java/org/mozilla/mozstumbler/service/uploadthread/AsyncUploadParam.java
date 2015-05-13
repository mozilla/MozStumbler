/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.uploadthread;

public class AsyncUploadParam {

    final boolean useWifiOnly;
    public final String nickname;
    public final String emailAddress;
    final boolean isWifiAvailable;

    public AsyncUploadParam(boolean wifiOnly,
                            boolean isWifiAvailable,
                            String nick,
                            String email) {

        if (email == null) {
            email = "";
        }

        if (nick == null) {
            nick = "";
        }

        this.isWifiAvailable = isWifiAvailable;
        useWifiOnly = wifiOnly;
        nickname = nick;
        emailAddress = email;
    }
}
