package org.mozilla.mozstumbler.service.uploadthread;

/**
 * Created by victorng on 2014-09-29.
 */
public class AsyncUploadParam {

    boolean useWifiOnly;
    AsyncUploaderListener asyncListener;
    String nickname;
    String emailAddress;

    public AsyncUploadParam(boolean wifiOnly,
                            AsyncUploaderListener listener,
                            String nick,
                            String email) {

        useWifiOnly = wifiOnly;
        asyncListener = listener;
        nickname = nick;
        emailAddress = email;
    }
}
