/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.uploadthread;

public class AsyncUploadParam {

    final boolean useWifiOnly;
    final String nickname;
    final String emailAddress;

    public AsyncUploadParam(boolean wifiOnly,
                            String nick,
                            String email) {

        if (email == null) {
            email = "";
        }

        if (nick == null) {
            nick = "";
        }

        useWifiOnly = wifiOnly;
        nickname = nick;
        emailAddress = email;
    }
}
