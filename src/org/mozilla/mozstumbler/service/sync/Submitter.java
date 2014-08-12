/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.sync;

import android.util.Log;
import java.io.IOException;

import org.mozilla.mozstumbler.service.AbstractCommunicator;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.Prefs;

public class Submitter extends AbstractCommunicator {
    private static final String SUBMIT_URL = "https://location.services.mozilla.com/v1/submit";
    private static final String LOG_TAG = AppGlobals.LOG_PREFIX + Submitter.class.getSimpleName();
    private final String mNickname;

    public Submitter() {
        super();
        mNickname = Prefs.getInstance().getNickname();
    }

    @Override
    public String getUrlString() {
        return SUBMIT_URL;
    }

    @Override
    public String getNickname(){
        return mNickname;
    }

    @Override
    public NetworkSendResult cleanSend(byte[] data) {
        NetworkSendResult result = new NetworkSendResult();
        try {
            result.bytesSent = this.send(data, ZippedState.eAlreadyZipped);
            result.errorCode = 0;
        } catch (IOException ex) {
            String msg = "Error submitting: " + ex;
            if (ex instanceof HttpErrorException) {
                result.errorCode = ((HttpErrorException) ex).responseCode;
                msg += " Code:" + result.errorCode;
            }
            Log.e(LOG_TAG, msg);
            AppGlobals.guiLogError(msg);
        }
        return result;
    }

}
