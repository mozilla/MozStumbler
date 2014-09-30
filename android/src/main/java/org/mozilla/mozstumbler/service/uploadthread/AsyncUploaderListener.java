package org.mozilla.mozstumbler.service.uploadthread;

/**
 * Created by victorng on 2014-09-29.
*/
public interface AsyncUploaderListener {
    // This is called by Android on the UI thread
    public void onUploadProgress(boolean async_uploading);
}
