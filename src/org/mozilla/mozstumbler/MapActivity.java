package org.mozilla.mozstumbler;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.net.wifi.ScanResult;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.Void;
import java.net.MalformedURLException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView.Projection;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.SafeDrawOverlay;
import org.osmdroid.views.safecanvas.ISafeCanvas;
import org.osmdroid.views.safecanvas.SafePaint;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.XYTileSource;

public final class MapActivity extends Activity {
    private static final String LOGTAG = MapActivity.class.getName();

    // TODO factor this out into something that can be shared with Reporter.java
    private static final String LOCATION_URL        = "https://location.services.mozilla.com/v1/search";
    private static final String USER_AGENT_HEADER = "User-Agent";
    private static String MOZSTUMBLER_USER_AGENT_STRING;

    private MapView mMap;
    private AccuracyCircleOverlay mAccuracyCircleOverlay;

    private ReporterBroadcastReceiver mReceiver;

    // TODO add cell data
    private List<ScanResult> mWifiData;

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
            if (!WifiScanner.WIFI_SCANNER_EXTRA_SUBJECT.equals(subject)) {
                // might be another scanner
                return;
            }

            mWifiData = intent.getParcelableArrayListExtra(WifiScanner.WIFI_SCANNER_ARG_SCAN_RESULTS);
            new GetLocationAndMapItTask().execute("");
            mDone = true;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        Context context = getApplicationContext();
        mWifiData = Collections.emptyList();
        MOZSTUMBLER_USER_AGENT_STRING = NetworkUtils.getUserAgentString(this);

        OnlineTileSourceBase tileSource = new XYTileSource("MozStumbler Tile Store",
                                                           null,
                                                           1, 20, 256,
                                                           ".png",
                                                           getMapURL(context));

        mMap = (MapView) this.findViewById(R.id.map);

        mMap.setTileSource(tileSource);
        mMap.setBuiltInZoomControls(true);
        mMap.setMultiTouchControls(true);

        mAccuracyCircleOverlay = new AccuracyCircleOverlay(this);
        mMap.getOverlays().add(mAccuracyCircleOverlay);

        mReceiver = new ReporterBroadcastReceiver();
        registerReceiver(mReceiver, new IntentFilter(ScannerService.MESSAGE_TOPIC));

        Log.d(LOGTAG, "onCreate");
    }

    private static String getMapURL(Context context) {
        String tileServerURL = PackageUtils.getMetaDataString(context, "org.mozilla.mozstumbler.TILE_SERVER_URL");

        if (tileServerURL == null || tileServerURL.equals("http://tile.openstreetmap.org/")) {
            String apiKey = PackageUtils.getMetaDataString(context, "org.mozilla.mozstumbler.MAP_API_KEY");
            if (apiKey == null || "FAKE_MAP_API_KEY".equals(apiKey)) {
                tileServerURL = "http://tile.openstreetmap.org/";
            } else {
                tileServerURL = "http://api.tiles.mapbox.com/v3/" + apiKey + "/";
            }
        }
        return tileServerURL;
    }

    private static class AccuracyCircleOverlay extends SafeDrawOverlay {
        private GeoPoint mPoint;
        private float mAccuracy;

        public AccuracyCircleOverlay(Context ctx) {
            super(ctx);
        }

        public void set(GeoPoint point, float accuracy) {
            mPoint = (GeoPoint) point.clone();
            mAccuracy = accuracy;
        }

        protected void drawSafe(ISafeCanvas c, MapView osmv, boolean shadow) {
            if (shadow || mPoint == null) {
                return;
            }
            Projection pj = osmv.getProjection();
            Point center = pj.toPixels(mPoint, null);
            float radius = pj.metersToEquatorPixels(mAccuracy);
            SafePaint circle = new SafePaint();
            circle.setARGB(0, 100, 100, 255);

            // Fill
            circle.setAlpha(40);
            circle.setStyle(Style.FILL);
            c.drawCircle(center.x, center.y, radius, circle);

            // Border
            circle.setAlpha(165);
            circle.setStyle(Style.STROKE);
            c.drawCircle(center.x, center.y, radius, circle);
        }
    }

    private void positionMapAt(float lat, float lon, float accuracy) {
        GeoPoint point = new GeoPoint(lat, lon);
        mMap.getController().setCenter(point);
        mMap.getController().setZoom(17);
        mMap.getController().animateTo(point);
        mAccuracyCircleOverlay.set(point, accuracy);
        mMap.invalidate();
    }

    @Override
    protected void onStart() {
        super.onStart();

        Context context = getApplicationContext();
        Intent i = new Intent(ScannerService.MESSAGE_TOPIC);
        i.putExtra(Intent.EXTRA_SUBJECT, "Scanner");
        i.putExtra("enable", 1);
        context.sendBroadcast(i);
        Log.d(LOGTAG, "onStart");
    }

    @Override
    protected void onStop() {
        super.onStop();

        Context context = getApplicationContext();
        Intent i = new Intent(ScannerService.MESSAGE_TOPIC);
        i.putExtra(Intent.EXTRA_SUBJECT, "Scanner");
        i.putExtra("enable", 0);
        context.sendBroadcast(i);

        Log.d(LOGTAG, "onStop");
        mMap.getTileProvider().clearTileCache();
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
            mReceiver = null;
        }
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
        public String doInBackground(String... params) {
            Log.d(LOGTAG, "requesting location...");
            HttpURLConnection urlConnection = null;
            try {
                URL url;
                Context context = getApplicationContext();
                String apiKey = PackageUtils.getMetaDataString(context, "org.mozilla.mozstumbler.API_KEY");
                try {
                    url = new URL(LOCATION_URL + "?key=" + apiKey);
                } catch (MalformedURLException e) {
                    throw new IllegalArgumentException(e);
                }
                urlConnection = (HttpURLConnection) url.openConnection();

                urlConnection.setDoOutput(true);
                urlConnection.setRequestProperty(USER_AGENT_HEADER, MOZSTUMBLER_USER_AGENT_STRING);

                Log.d(LOGTAG, "mWifiData: " + mWifiData);

                JSONObject wrapper = new JSONObject("{}");
                try {
                    JSONArray wifiData = new JSONArray();
                    for (ScanResult result : mWifiData) {
                        JSONObject item = new JSONObject();
                        item.put("key", BSSIDBlockList.canonicalizeBSSID(result.BSSID));
                        item.put("frequency", result.frequency);
                        item.put("signal", result.level);
                        wifiData.put(item);
                    }
                    wrapper.put("wifi", wifiData);
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
                Log.d(LOGTAG, "urlConnection returned " + code);

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
                Log.d(LOGTAG, "Location status: " + mStatus);
                if ("ok".equals(mStatus)) {
                    mLat = Float.parseFloat(result.getString("lat"));
                    mLon = Float.parseFloat(result.getString("lon"));
                    mAccuracy = Float.parseFloat(result.getString("accuracy"));
                    Log.d(LOGTAG, "Location lat: " + mLat);
                    Log.d(LOGTAG, "Location lon: " + mLon);
                    Log.d(LOGTAG, "Location accuracy: " + mAccuracy);
                }
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
                positionMapAt(mLat, mLon, mAccuracy);
            } else if (mStatus != null && "not_found".equals(mStatus)) {
                    Toast.makeText(getApplicationContext(),
                            getResources().getString(R.string.location_not_found),
                            Toast.LENGTH_LONG).show();
            } else {
                Log.e(LOGTAG, "", new IllegalStateException("mStatus=" + mStatus));
            }
        }
    }
}
