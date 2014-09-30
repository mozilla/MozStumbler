/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mozilla.mozstumbler.service.uploadthread;

/**
 * Created by victorng on 2014-09-29.
*/
public interface AsyncUploaderListener {
    // This is called by Android on the UI thread
    public void onUploadProgress(boolean isUploading);
}
