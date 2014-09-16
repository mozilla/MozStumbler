package org.mozilla.mozstumbler.client;

import android.util.Log;

import org.apache.commons.io.IOUtils;
import org.mozilla.mozstumbler.service.AppGlobals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by victorng on 2014-09-15.
 */
public class HttpUtil implements IHttpUtil {
    private static final String LOG_TAG = AppGlobals.LOG_PREFIX + HttpUtil.class.getSimpleName();

    @Override
    public String getUrlAsString(String uri)  {
        try {
            URL url = new URL(uri);
            return IOUtils.toString(url, "utf8");
        } catch (IOException e) {
            Log.e(LOG_TAG, "", e);
        }
        return null;
    }

    @Override
    public Reader getUrlAsReader(String urlString)   {
       try
       {
           URL url = new URL(urlString);
           return getUrlAsReader(url);
       } catch (MalformedURLException urlEx) {
            Log.e(LOG_TAG, "", urlEx);
            return null;
       }

    }

    @Override
    public Reader getUrlAsReader(URL url) {
        try {
            return new BufferedReader(new InputStreamReader(url.openStream()));
        } catch (IOException ioEx) {
            Log.e(LOG_TAG, "", ioEx);
            return null;
        }
    }
}
