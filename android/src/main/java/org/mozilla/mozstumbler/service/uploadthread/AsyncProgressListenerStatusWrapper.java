/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.mozstumbler.service.uploadthread;

/**
 * Created by victorng on 2014-09-29.
 */
public class AsyncProgressListenerStatusWrapper {
    public AsyncUploaderListener listener;
    public Boolean uploading_flag;

    public AsyncProgressListenerStatusWrapper(AsyncUploaderListener l, boolean status) {
        listener = l;
        uploading_flag = new Boolean(status);
    }
}
