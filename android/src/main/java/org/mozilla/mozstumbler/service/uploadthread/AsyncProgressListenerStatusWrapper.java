/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.mozstumbler.service.uploadthread;

public class AsyncProgressListenerStatusWrapper {
    public final AsyncUploader.AsyncUploaderListener listener;
    public final boolean uploading_flag;

    public AsyncProgressListenerStatusWrapper(AsyncUploader.AsyncUploaderListener l, boolean status) {
        listener = l;
        uploading_flag = status;
    }
}
