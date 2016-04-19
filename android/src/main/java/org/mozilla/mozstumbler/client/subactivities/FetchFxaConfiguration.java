package org.mozilla.mozstumbler.client.subactivities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.accounts.fxa.FxAGlobals;
import org.mozilla.accounts.fxa.LoggerUtil;
import org.mozilla.accounts.fxa.net.HTTPResponse;
import org.mozilla.accounts.fxa.net.HttpUtil;
import org.mozilla.mozstumbler.R;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by victorng on 2015-12-31.
 *
 *
 * This class jsut calls GET on a URL, passes in a refresh token, an existing access token
 * and the server processes
 *
 */
public class FetchFxaConfiguration extends AsyncTask<Void, Void, JSONObject> {

    // Most applications should use a refreshed access token on application startup.
    // This will minimize the lifetime of any access token.

    public static final String FXA_CONFIG_LOAD = "org.mozilla.accounts.fxa.config.load";
    public static final String FXA_CONFIG_LOAD_FAILURE = "org.mozilla.accounts.fxa.config.load.failure";


    private static final String LOG_TAG = LoggerUtil.makeLogTag(FetchFxaConfiguration.class);
    private final String configuration_endpoint;
    private final Context mContext;

    public FetchFxaConfiguration(Context ctx, String cfg_url) {
        mContext = ctx;
        this.configuration_endpoint = cfg_url;
    }

    public String getFxaConfigEndpoint() {
        return configuration_endpoint;
    }

    public AsyncTask<Void, Void, JSONObject> execute() {
        return super.execute();
    }


    /*
     This task requires no arguments.
     */
    @Override
    protected JSONObject doInBackground(Void... params) {
        if (params.length != 0) {
            Log.i(LOG_TAG, "Invalid number of arguments.");
            return null;
        }

        HttpUtil httpUtil = new HttpUtil(System.getProperty("http.agent") + " " +
                FxAGlobals.appName + "/" + FxAGlobals.appVersionName);

        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Content-Type", "application/json");

        HTTPResponse resp = httpUtil.get(getFxaConfigEndpoint(), headers);

        if (resp.isSuccessCode2XX()) {
            try {
                return new JSONObject(resp.body());
            } catch (JSONException e) {
                Log.e(LOG_TAG, "Error marshalling the FxA configuration JSON blob.");
                return null;
            }
        } else {
            Log.w(LOG_TAG, "FxA Configuration HTTP Status: " + resp.httpStatusCode());
            return null;
        }
    }



    @Override
    protected void onPostExecute(JSONObject result) {
        if (result == null) {
            Intent intent = new Intent(FXA_CONFIG_LOAD_FAILURE);
            LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
        } else {
            Intent intent = new Intent(FXA_CONFIG_LOAD);
            intent.putExtra("json", result.toString());
            LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
            Toast.makeText(mContext,
                    mContext.getString(R.string.fxa_loading_config),
                    Toast.LENGTH_SHORT).show();
        }
    }

    public static void registerFxaIntents(Context ctx, BroadcastReceiver callbackReceiver) {
        IntentFilter intentFilter = new IntentFilter();

        intentFilter.addAction(FetchFxaConfiguration.FXA_CONFIG_LOAD);
        intentFilter.addAction(FetchFxaConfiguration.FXA_CONFIG_LOAD_FAILURE);

        LocalBroadcastManager.getInstance(ctx)
                .registerReceiver(callbackReceiver, intentFilter);

    }
}
