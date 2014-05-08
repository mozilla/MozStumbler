package org.mozilla.mozstumbler.client;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.mozstumbler.StumblerBundle;
import org.mozilla.mozstumbler.DatabaseContract;

public final class StumblerBundleReceiver extends BroadcastReceiver {
    private static final String LOGTAG = StumblerBundleReceiver.class.getName();

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(LOGTAG, "onReceive!");

        StumblerBundle bundle = intent.getParcelableExtra("StumblerBundle");
        onReceiveStumblerBundle(context, bundle);
    }

    private void onReceiveStumblerBundle(Context context, StumblerBundle bundle) {
        ContentValues values = new ContentValues(10);

        JSONObject mlsObj = null;
        try {
            mlsObj = bundle.toMLSJSON();
            Log.d(LOGTAG, "Received bundle: " + mlsObj.toString());

            values.put(DatabaseContract.Reports.TIME, mlsObj.getLong("time"));
            values.put(DatabaseContract.Reports.LAT, mlsObj.getDouble("lat"));
            values.put(DatabaseContract.Reports.LON, mlsObj.getDouble("lon"));

            if (mlsObj.has("altitude")) {
                values.put(DatabaseContract.Reports.ALTITUDE, mlsObj.getInt("altitude"));
            }

            if (mlsObj.has("accuracy")) {
                values.put(DatabaseContract.Reports.ACCURACY, mlsObj.getInt("accuracy"));
            }

            if (mlsObj.has("radio")) {
                values.put(DatabaseContract.Reports.RADIO, mlsObj.getString("radio"));
            }

            if (mlsObj.has("cell")) {
                JSONArray cells = mlsObj.getJSONArray("cell");
                values.put(DatabaseContract.Reports.CELL, cells.toString());
                values.put(DatabaseContract.Reports.CELL_COUNT, cells.length());
            }

            JSONArray wifis = mlsObj.getJSONArray("wifi");
            values.put(DatabaseContract.Reports.WIFI, wifis.toString());
            values.put(DatabaseContract.Reports.WIFI_COUNT, wifis.length());
        } catch (JSONException e) {
            Log.w(LOGTAG, "Failed to convert bundle to JSON: " + e);
            return;
        }

        context.getContentResolver().insert(DatabaseContract.Reports.CONTENT_URI, values);
    }
}
