package org.mozilla.mozstumbler.client.leaderboard;

import android.content.Context;
import android.util.Log;

import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.entity.StringEntity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;

/**
 * Created by JeremyChiang on 2014-08-28.
 */
public class GalaxyManager {

    private static final String TAG = GalaxyManager.class.getName();
    private GalaxyManagerListener galaxyManagerListener;

    public interface GalaxyManagerListener {
        public void gameFound(JSONObject game);
        public void leaderboardFound(JSONObject leaderboard);
    }

    public GalaxyManager(GalaxyManagerListener galaxyManagerListener) {
        this.galaxyManagerListener = galaxyManagerListener;
    }

    public void getGame(final String gameSlug) {
        RequestParams requestParams = null;

        GalaxyRestClient.get(
                GalaxyRestClient.GAME_DIRECTORY + "/" + gameSlug,
                requestParams,
                new JsonHttpResponseHandler() {
                    @Override
                    public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                        // If the response is JSONObject instead of a JSONArray.
                        Log.d(TAG, "Game, " + gameSlug + ", retrieved with status code: " + statusCode);
                        galaxyManagerListener.gameFound(response);
                    }

                    @Override
                    public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                        super.onFailure(statusCode, headers, responseString, throwable);
                        Log.d(TAG, "Error retrieving game, " + gameSlug + ", reason: " + responseString);
                        galaxyManagerListener.gameFound(null);
                    }

                    @Override
                    public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                        super.onFailure(statusCode, headers, throwable, errorResponse);
                        Log.d(TAG, "Error retrieving game, " + gameSlug + ", reason: " + errorResponse.toString());
                        galaxyManagerListener.gameFound(null);
                    }

                    @Override
                    public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONArray errorResponse) {
                        super.onFailure(statusCode, headers, throwable, errorResponse);
                        Log.d(TAG, "Error retrieving game, " + gameSlug + ", reason: " + errorResponse.toString());
                        galaxyManagerListener.gameFound(null);
                    }
                });
    }

    public void createGame(Context context, final String nameOfSlug, String nameOfGame, String description, String appUrl) {
        JSONObject game = new JSONObject();
        Header[] headers = null;

        try {
            game.put(GalaxyRestClient.KEY_GAME_SLUG, nameOfSlug);
            game.put(GalaxyRestClient.KEY_GAME_NAME, nameOfGame);
            game.put(GalaxyRestClient.KEY_GAME_DESCRIPTION, description);
            game.put(GalaxyRestClient.KEY_GAME_URL, appUrl);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        HttpEntity gameEntity = null;

        try {
            gameEntity = new StringEntity(game.toString());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        GalaxyRestClient.post(
                context,
                GalaxyRestClient.GAME_DIRECTORY + "/",
                headers,
                gameEntity,
                "application/json",
                new JsonHttpResponseHandler() {
                    @Override
                    public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                        // If the response is JSONObject instead of a JSONArray.
                        Log.d(TAG, "Game, " + nameOfSlug + ", created with status code: " + statusCode);
                    }

                    @Override
                    public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                        super.onFailure(statusCode, headers, responseString, throwable);
                        Log.d(TAG, "Error creating game, " + nameOfSlug + ", reason: " + responseString);
                    }

                    @Override
                    public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                        super.onFailure(statusCode, headers, throwable, errorResponse);
                        Log.d(TAG, "Error creating game, " + nameOfSlug + ", reason: " + errorResponse.toString());
                    }

                    @Override
                    public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONArray errorResponse) {
                        super.onFailure(statusCode, headers, throwable, errorResponse);
                        Log.d(TAG, "Error creating game, " + nameOfSlug + ", reason: " + errorResponse.toString());
                    }
                });
    }

    public void getLeaderboard(final String gameSlug, final String leaderboardSlug) {
        RequestParams requestParams = null;

        GalaxyRestClient.get(
                GalaxyRestClient.GAME_DIRECTORY + "/" + gameSlug + "/" +
                GalaxyRestClient.LEADERBOARD_DIRECTORY + "/" + leaderboardSlug,
                requestParams,
                new JsonHttpResponseHandler() {
                    @Override
                    public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                        // If the response is JSONObject instead of a JSONArray.
                        Log.d(TAG, "Leaderboard, " + leaderboardSlug + ", retrieved with status code: " + statusCode);
                        galaxyManagerListener.leaderboardFound(response);
                    }

                    @Override
                    public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                        super.onFailure(statusCode, headers, responseString, throwable);
                        Log.d(TAG, "Error retrieving leaderboard, " + leaderboardSlug + ", for game, " + gameSlug + ", reason: " + responseString);
                        galaxyManagerListener.leaderboardFound(null);
                    }

                    @Override
                    public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                        super.onFailure(statusCode, headers, throwable, errorResponse);
                        Log.d(TAG, "Error retrieving leaderboard, " + leaderboardSlug + ", for game, " + gameSlug + ", reason: " + errorResponse.toString());
                        galaxyManagerListener.leaderboardFound(null);
                    }

                    @Override
                    public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONArray errorResponse) {
                        super.onFailure(statusCode, headers, throwable, errorResponse);
                        Log.d(TAG, "Error retrieving leaderboard, " + leaderboardSlug + ", for game, " + gameSlug + ", reason: " + errorResponse.toString());
                        galaxyManagerListener.leaderboardFound(null);
                    }
                });
    }

    public void createLeaderboard(Context context, final String gameSlug, final String leaderboardSlug, final String leaderboardName) {
        JSONObject leaderboard = new JSONObject();
        Header[] headers = null;

        try {
            leaderboard.put(GalaxyRestClient.KEY_LEADERBOARD_SLUG, leaderboardSlug);
            leaderboard.put(GalaxyRestClient.KEY_LEADERBOARD_NAME, leaderboardName);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        HttpEntity leaderboardEntity = null;

        try {
            leaderboardEntity = new StringEntity(leaderboard.toString());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        GalaxyRestClient.post(
                context,
                GalaxyRestClient.GAME_DIRECTORY + "/" + gameSlug + "/" + GalaxyRestClient.LEADERBOARD_DIRECTORY,
                headers,
                leaderboardEntity,
                "application/json",
                new JsonHttpResponseHandler() {
                    @Override
                    public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                        // If the response is JSONObject instead of a JSONArray.
                        Log.d(TAG, "Leaderboard, " + leaderboardSlug + ", for game, " + gameSlug + ", created with status code: " + statusCode);
                    }

                    @Override
                    public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                        super.onFailure(statusCode, headers, responseString, throwable);
                        Log.d(TAG, "Error creating leaderboard, " + leaderboardSlug + ", for game," + gameSlug + ", reason: " + responseString);
                    }

                    @Override
                    public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                        super.onFailure(statusCode, headers, throwable, errorResponse);
                        Log.d(TAG, "Error creating leaderboard, " + leaderboardSlug + ", for game," + gameSlug + ", reason: " + errorResponse.toString());
                    }

                    @Override
                    public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONArray errorResponse) {
                        super.onFailure(statusCode, headers, throwable, errorResponse);
                        Log.d(TAG, "Error creating leaderboard, " + leaderboardSlug + ", for game," + gameSlug + ", reason: " + errorResponse.toString());
                    }
                });
    }
}
