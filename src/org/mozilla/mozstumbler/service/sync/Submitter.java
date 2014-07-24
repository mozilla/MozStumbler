/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.sync;

import android.util.Log;
import java.io.IOException;
import java.net.HttpURLConnection;
import org.mozilla.mozstumbler.service.AbstractCommunicator;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.SharedConstants;

public class Submitter extends AbstractCommunicator {
    private static final String SUBMIT_URL = "https://location.services.mozilla.com/v1/submit";
    private static final String LOGTAG = Submitter.class.getName();
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
    public int cleanSend(byte[] data) {
       int result = -1;

        try {
            this.send(data);
            result = 0;
        } catch (IOException ex) {
            String msg = "Error submitting: " + ex;
            Log.e(LOGTAG, msg);
            if (SharedConstants.guiLogMessageBuffer != null)
                SharedConstants.guiLogMessageBuffer.add("<font color='red'><b>" + msg + "</b></font>");

            if (ex instanceof HttpErrorException) {
                result = ((HttpErrorException) ex).responseCode;
            }
        }
        return result;
    }

}
