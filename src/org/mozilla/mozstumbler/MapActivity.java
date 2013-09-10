package org.mozilla.mozstumbler;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PackageManager;
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

        MOZSTUMBLER_USER_AGENT_STRING = getUserAgentString();

        mMap = ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();

        Log.d(LOGTAG, "onCreate");
    }

    private void setPositionAndMarker(String lat, String lon, String accuracy) {
        LatLng poi = new LatLng(Float.parseFloat(lat), Float.parseFloat(lon));
        Marker hamburg = mMap.addMarker(new MarkerOptions().position(poi).title("You're Here"));

        // Move the camera instantly to poi with a zoom of 15.
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(poi, 15));

        // Zoom in, animating the camera.
        //        mMap.animateCamera(CameraUpdateFactory.zoomTo(20), 2000, null);

        mMap.addCircle(new CircleOptions()
                                     .center(poi)
                                     .radius(Float.parseFloat(accuracy))
                                     .strokeColor(Color.RED)
                                     .fillColor(Color.argb(30, 0, 153, 255))
                                     .strokeWidth(2));
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

    private class GetLocationAndMapItTask extends AsyncTask<String, Void, String> {
        private String mStatus;
        private String mLat;
        private String mLon;
        private String mAccuracy;

        @Override
        public String doInBackground(String... params) {
            Log.d(LOGTAG, "requesting location...");
            try {
                URL url = new URL(LOCATION_URL);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

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
                mLat = result.getString("lat");
                mLon = result.getString("lon");
                mAccuracy = result.getString("accuracy");

                Log.e(LOGTAG, "Location status: " + mStatus);
                Log.e(LOGTAG, "Location lat: " + mLat);
                Log.e(LOGTAG, "Location lon: " + mLon);
                Log.e(LOGTAG, "Location accuracy: " + mAccuracy);

            } catch (JSONException jsonex) {
                Log.e(LOGTAG, "json parse error", jsonex);
            } catch (Exception ex) {
                Log.e(LOGTAG, "error submitting data", ex);
            }

            urlConnection.disconnect();
            return mStatus;
        }

        @Override
        protected void onPostExecute(String result) {
            if (mStatus.equals("ok")) {
                setPositionAndMarker(mLat, mLon, mAccuracy);
            }
            else {
                setPositionAndMarker("0", "0", "32000");
            }
        }

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected void onProgressUpdate(Void... values) {
        }

    }

    private String getUserAgentString() {
        String appName = getString(R.string.app_name);

        String versionName;
        try {
            PackageManager pm = getPackageManager();
            versionName = pm.getPackageInfo("org.mozilla.mozstumbler", 0).versionName;
        } catch (NameNotFoundException e) {
            throw new IllegalArgumentException(e);
        }

        // "MozStumbler/X.Y.Z"
        return appName + '/' + versionName;
    }
}
