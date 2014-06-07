package org.mozilla.mozstumbler.client.mapview;

import android.content.Context;
import android.util.Log;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.mozstumbler.service.AbstractCommunicator;

public class Searcher extends AbstractCommunicator {
    private static final String LOGTAG = Searcher.class.getName();
    private static final String SEARCH_URL = "https://location.services.mozilla.com/v1/search";
    private static final int CORRECT_RESPONSE = HttpURLConnection.HTTP_OK;
    private static final String RESPONSE_OK_TEXT = "ok";
    private static final String JSON_LATITUDE = "lat";
    private static final String JSON_LONGITUDE = "lon";
    private static final String JSON_ACCURACY = "accuracy";
    private JSONObject mResponse;

    public Searcher (Context ctx) {
        super(ctx);
    }

    @Override
    public String getUrlString() {
        return SEARCH_URL;
    }

    @Override
    public int getCorrectResponse() {
        return CORRECT_RESPONSE;
    }

    private void initResponse() throws IOException,JSONException {
        if (mResponse!=null)
            return;
        InputStream in = new BufferedInputStream(super.getInputStream());
        BufferedReader r = new BufferedReader(new InputStreamReader(in));
        String line;
        StringBuilder total = new StringBuilder(in.available());
        while ((line = r.readLine()) != null) {
            total.append(line);
        }
        r.close();
        in.close();
        mResponse = new JSONObject(total.toString());
    }

    @Override
    public boolean cleanSend(byte[] data) {
        boolean result = false;
        try {
            send(data);
            result = true;
        } catch (IOException e) {
            // do nothing
        }
        return result;
    }

    public String getStatus() {
        try {
            initResponse();
            return mResponse.getString("status");
        } catch (IOException e) {
            Log.e(LOGTAG, "Couldn't process the response: ", e);
            return null;
        } catch (JSONException e) {
            Log.e(LOGTAG, "JSON got confused: ", e);
            return null;
        }
    }

    public float getLat() {
        if (RESPONSE_OK_TEXT.equals(getStatus())) {
            try {
                return Float.parseFloat(mResponse.getString(JSON_LATITUDE));
            } catch (JSONException e) {
                Log.e(LOGTAG, "Latitude JSON response problem: ", e);
            }
        }
        return 0f;
    }

    public float getLon() {
        if (RESPONSE_OK_TEXT.equals(getStatus())) {
            try {
                return Float.parseFloat(mResponse.getString(JSON_LONGITUDE));
            } catch (JSONException e) {
                Log.e(LOGTAG, "Longitude JSON response problem: ", e);
            }
        }
        return 0f;
    }

    public float getAccuracy() {
        if (RESPONSE_OK_TEXT.equals(getStatus())) {
            try {
                return Float.parseFloat(mResponse.getString(JSON_ACCURACY));
            } catch (JSONException e) {
                Log.e(LOGTAG, "Accuracy JSON response problem: ", e);
            }
        }
        return 0f;
    }

    @Override
    public void close() {
        super.close();
        mResponse = null;
    }

}
