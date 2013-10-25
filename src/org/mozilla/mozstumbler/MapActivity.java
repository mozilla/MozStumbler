package org.mozilla.mozstumbler;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.Void;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class MapActivity extends Activity {
    private static final String LOGTAG = MapActivity.class.getName();

    // TODO factor this out into something that can be shared with Reporter.java
    private static final String LOCATION_URL        = "https://location.services.mozilla.com/v1/search";
    private static final String USER_AGENT_HEADER = "User-Agent";
    private static String MOZSTUMBLER_USER_AGENT_STRING;

    private GoogleMap mMap;
    private ReporterBroadcastReceiver mReceiver;

    // TODO add cell data
    private String mWifiData;

    private class ReporterBroadcastReceiver extends BroadcastReceiver {
        private boolean mDone;

        @Override
        public void onReceive(Context context, Intent intent) {
          if (mDone) {
            return;
          }

          String action = intent.getAction();

          if (!action.equals(ScannerService.MESSAGE_TOPIC)) {
            Log.e(LOGTAG, "Received an unknown intent: " + action);
            return;
          }

          String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);

          if (subject.equals("WifiScanner")) {
            mWifiData = intent.getStringExtra("data");
            zeroPositionAndMarker();
            new GetLocationAndMapItTask().execute("");
            mDone = true;
          }
          else {
            // might be another scanner
            return;
          }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        mWifiData = "";
        mReceiver = new ReporterBroadcastReceiver();
        registerReceiver(mReceiver, new IntentFilter(ScannerService.MESSAGE_TOPIC));

        MOZSTUMBLER_USER_AGENT_STRING = NetworkUtils.getUserAgentString(this);

        mMap = ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();

        Log.d(LOGTAG, "onCreate");
    }

    private void moveToPositionAndMarker(float lat, float lon, float accuracy) {
        LatLng poi = new LatLng(lat, lon);
        mMap.addMarker(new MarkerOptions().position(poi));

        float zoom = zoomFromAccuracy(accuracy);
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(poi, zoom));

        mMap.addCircle(new CircleOptions()
                                     .center(poi)
                                     .radius(accuracy)
                                     .fillColor(Color.argb(50, 0, 153, 255))
                                     .strokeWidth(0));
    }

    private void zeroPositionAndMarker() {
        // A high altitude view of Google Maps' "North Atlantic Ocean" text is a pretty
        // clear indication of "we don't know where you are".
        LatLng northAtlanticOcean = new LatLng(35, -41);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(northAtlanticOcean, 2));
    }

    private static float zoomFromAccuracy(float accuracy) {
        if (accuracy <= 0) {
            Log.w(LOGTAG, "", new IllegalArgumentException("accuracy=" + accuracy));
            return 2;
        }
        // Valid zoom range is [2.0f, 21.0f].
        return 21f - (float) Math.log(accuracy);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(LOGTAG, "onStart");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(LOGTAG, "onStop");
        unregisterReceiver(mReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return false;
    }

    private final class GetLocationAndMapItTask extends AsyncTask<String, Void, String> {
        private String mStatus;
        private float mLat;
        private float mLon;
        private float mAccuracy;

        @Override
        protected void onPreExecute() {
            zeroPositionAndMarker();
        }

        @Override
        public String doInBackground(String... params) {
            Log.d(LOGTAG, "requesting location...");
            HttpURLConnection urlConnection = null;
            try {
                URL url = new URL(LOCATION_URL);
                urlConnection = (HttpURLConnection) url.openConnection();

                urlConnection.setDoOutput(true);
                urlConnection.setRequestProperty(USER_AGENT_HEADER, MOZSTUMBLER_USER_AGENT_STRING);

                Log.d(LOGTAG, "mWifiData: " + mWifiData);

                JSONObject wrapper = new JSONObject("{}");
                try {
                  wrapper.put("wifi", new JSONArray(mWifiData));
                } catch (JSONException jsonex) {
                  Log.w(LOGTAG, "json exception", jsonex);
                  return "";
                }

                String data = wrapper.toString();
                byte[] bytes = data.getBytes();
                urlConnection.setFixedLengthStreamingMode(bytes.length);
                OutputStream out = new BufferedOutputStream(urlConnection.getOutputStream());
                out.write(bytes);
                out.flush();

                int code = urlConnection.getResponseCode();
                Log.d(LOGTAG, "uploaded data: " + data + " to " + LOCATION_URL);
                Log.e(LOGTAG, "urlConnection returned " + code);

                InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                BufferedReader r = new BufferedReader(new InputStreamReader(in));
                StringBuilder total = new StringBuilder(in.available());
                String line;
                while ((line = r.readLine()) != null) {
                    total.append(line);
                }
                r.close();

                JSONObject result = new JSONObject(total.toString());
                mStatus = result.getString("status");
                mLat = Float.parseFloat(result.getString("lat"));
                mLon = Float.parseFloat(result.getString("lon"));
                mAccuracy = Float.parseFloat(result.getString("accuracy"));

                Log.e(LOGTAG, "Location status: " + mStatus);
                Log.e(LOGTAG, "Location lat: " + mLat);
                Log.e(LOGTAG, "Location lon: " + mLon);
                Log.e(LOGTAG, "Location accuracy: " + mAccuracy);

            } catch (JSONException jsonex) {
                Log.e(LOGTAG, "json parse error", jsonex);
            } catch (Exception ex) {
                Log.e(LOGTAG, "error submitting data", ex);
            }
            finally {
              urlConnection.disconnect();
            }

            return mStatus;
        }

        @Override
        protected void onPostExecute(String result) {
            if (mStatus != null && "ok".equals(mStatus)) {
                moveToPositionAndMarker(mLat, mLon, mAccuracy);
            } else {
                Log.e(LOGTAG, "", new IllegalStateException("mStatus=" + mStatus));
            }
        }
    }
}
