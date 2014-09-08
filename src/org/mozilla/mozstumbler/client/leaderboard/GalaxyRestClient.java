package org.mozilla.mozstumbler.client.leaderboard;

import android.content.Context;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import org.apache.http.Header;
import org.apache.http.HttpEntity;

/**
 * Created by JeremyChiang on 2014-08-28.
 */
public class GalaxyRestClient {

    private static final String BASE_URL = "https://api-galaxy.dev.mozaws.net/";
    private static AsyncHttpClient client = new AsyncHttpClient();

    public static final String GAME_SLUG = "mozstumbler";
    public static final String GAME_NAME = "MozStumbler";
    public static final String GAME_DESC = "A game based on stumbling WiFi access points.";
    public static final String GAME_URL = "https://github.com/mozilla/MozStumbler";
    public static final String GAME_DIRECTORY = "games";

    public static final String LEADERBOARD_SLUG = "points-collected";
    public static final String LEADERBOARD_NAME = "Points Collected Leaderboard";
    public static final String LEADERBOARD_DIRECTORY = "leaderboards";

    public static final String KEY_GAME_SLUG = "slug";
    public static final String KEY_GAME_NAME = "name";
    public static final String KEY_GAME_DESCRIPTION = "description";
    public static final String KEY_GAME_URL = "app_url";

    public static final String KEY_LEADERBOARD_SLUG = "slug";
    public static final String KEY_LEADERBOARD_NAME = "name";

    public static void get(String url, RequestParams params, AsyncHttpResponseHandler responseHandler) {
        client.get(getAbsoluteUrl(url), params, responseHandler);
    }

    public static void post(Context context, String url, Header[] headers, HttpEntity entity, String contentType, AsyncHttpResponseHandler responseHandler) {
        client.post(context, getAbsoluteUrl(url), headers, entity, contentType, responseHandler);
    }

    private static String getAbsoluteUrl(String relativeUrl) {
        return BASE_URL + relativeUrl;
    }
}
