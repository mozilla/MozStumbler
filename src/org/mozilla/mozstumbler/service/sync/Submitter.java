package org.mozilla.mozstumbler.service.sync;

import android.content.Context;
import android.util.Log;
import java.io.IOException;
import java.net.HttpURLConnection;
import org.mozilla.mozstumbler.service.AbstractCommunicator;
import org.mozilla.mozstumbler.service.Prefs;

public class Submitter extends AbstractCommunicator {
    private static final String SUBMIT_URL = "https://location.services.mozilla.com/v1/submit";
    private static final String LOGTAG = Submitter.class.getName();
    private static final int CORRECT_RESPONSE = HttpURLConnection.HTTP_NO_CONTENT;
    private final String mNickname;

    public Submitter(Context ctx) {
        super(ctx);
        final Prefs prefs = new Prefs(ctx);
        mNickname = prefs.getNickname();
    }

    @Override
    public String getUrlString() {
        return SUBMIT_URL;
    }

    @Override
    public int getCorrectResponse() {
        return CORRECT_RESPONSE;
    }

    @Override
    public String getNickname(){
        return mNickname;
    }

    @Override
    public boolean cleanSend(byte[] data) {
        boolean result = false;
        try {
            this.send(data);
            result = true;
        } catch (IOException ex) {
            Log.e(LOGTAG,"Error submitting: ", ex);
        }
        return result;
    }

}
