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
