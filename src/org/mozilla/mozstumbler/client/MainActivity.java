package org.mozilla.mozstumbler.client;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.graphics.Color;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.IBinder;
import android.os.StrictMode;
import android.provider.Settings;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;
import org.mozilla.mozstumbler.BuildConfig;
import org.mozilla.mozstumbler.DateTimeUtils;
import org.mozilla.mozstumbler.client.mapview.MapActivity;
import org.mozilla.mozstumbler.service.scanners.GPSScanner;
import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.SharedConstants;
import org.mozilla.mozstumbler.service.StumblerService;
import org.mozilla.mozstumbler.service.scanners.WifiScanner;
import org.mozilla.mozstumbler.service.scanners.cellscanner.CellScanner;
import org.mozilla.mozstumbler.DatabaseContract;
import org.mozilla.mozstumbler.client.sync.SyncUtils;

public final class MainActivity extends FragmentActivity {
    private static final String LOGTAG = MainActivity.class.getName();

    public static final String ACTION_BASE = SharedConstants.ACTION_NAMESPACE + ".MainActivity.";
    public static final String ACTION_UPDATE_UI = ACTION_BASE + "UPDATE_UI";

    /** if mConnectionRemote exists, start scanning, otherwise do nothing  */
    public static final String ACTION_UNPAUSE_SCANNING = ACTION_BASE + "UNPAUSE_SCANNING";

    private static final String LEADERBOARD_URL = "https://location.services.mozilla.com/leaders";
    private static final String INTENT_TURN_OFF = "org.mozilla.mozstumbler.turnMeOff";
    private static final int    NOTIFICATION_ID = 1;

    private StumblerService mConnectionRemote;
    private ServiceConnection        mConnection;
    private ServiceBroadcastReceiver mReceiver;
    private int                      mGpsFixes;
    private int                      mGpsSats;
    private boolean                  mNeedsUpdate = false;
    private boolean                  mGeofenceHere = false;

    private class ServiceBroadcastReceiver extends BroadcastReceiver {
        private boolean mReceiverIsRegistered;

        public void register() {
            if (!mReceiverIsRegistered) {
                IntentFilter intentFilter = new IntentFilter();
                intentFilter.addAction(WifiScanner.ACTION_WIFIS_SCANNED);
                intentFilter.addAction(CellScanner.ACTION_CELLS_SCANNED);
                intentFilter.addAction(GPSScanner.ACTION_GPS_UPDATED);
                intentFilter.addAction(MainActivity.ACTION_UNPAUSE_SCANNING);
                intentFilter.addAction(MainActivity.ACTION_UPDATE_UI);
                registerReceiver(this, intentFilter);
                LocalBroadcastManager.getInstance(MainActivity.this).registerReceiver(this,
                        intentFilter);
                mReceiverIsRegistered = true;
            }
        }

        public void unregister() {
            if (mReceiverIsRegistered) {
                LocalBroadcastManager.getInstance(MainActivity.this).unregisterReceiver(this);
                mReceiverIsRegistered = false;
            }
        }

        private void receivedGpsMessage(Intent intent) {
            String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);
            if (subject.equals(GPSScanner.SUBJECT_NEW_STATUS)) {
                mGpsFixes = intent.getIntExtra(GPSScanner.NEW_STATUS_ARG_FIXES ,0);
                mGpsSats = intent.getIntExtra(GPSScanner.NEW_STATUS_ARG_SATS, 0);
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(GPSScanner.ACTION_GPS_UPDATED)) {
                receivedGpsMessage(intent);
            } else if (action.equals(MainActivity.ACTION_UNPAUSE_SCANNING) &&
                       null != mConnectionRemote) {
                startScanning();
            }

            updateUI();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mGeofenceHere = mConnectionRemote.getPrefs().getGeofenceHere();
        if (mGeofenceHere)
            mConnectionRemote.getPrefs().setGeofenceEnabled(false);

        setGeofenceText();
        mNeedsUpdate = true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (BuildConfig.DEBUG) enableStrictMode();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SyncUtils.CreateSyncAccount(this);

        if (BuildConfig.MOZILLA_API_KEY != null) {
            Updater.checkForUpdates(this);
        }

        getSupportLoaderManager().initLoader(0, null, mSyncStatsLoaderCallbacks);

        Log.d(LOGTAG, "onCreate");
    }

    private void checkGps() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            new AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle(R.string.app_name)
                .setMessage(R.string.gps_alert_msg)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    }
                })
                .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .show();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        mReceiver = new ServiceBroadcastReceiver();
        mReceiver.register();

        mConnection = new ServiceConnection() {
            public void onServiceConnected(ComponentName className, IBinder binder) {
                StumblerService.StumblerBinder serviceBinder = (StumblerService.StumblerBinder) binder;
                mConnectionRemote = serviceBinder.getService();
                Log.d(LOGTAG, "Service connected");

                /* FIXME
                // each time we reconnect, check to see if we're suppose to be
                // in power saving mode.  if not, start the scanning. TODO: we
                // shouldn't just stopScanning if we find that we are in PSM.
                // Instead, we should see if we were scanning do to an activity
                // recognition.  If we were, don't stop.
                if (mConnectionRemote != null) {
                    if (mPrefs.getPowerSavingMode()) {
                        stopScanning();
                    } else {
                        startScanning();
                    }
                }
                */
                updateUI();
            }

            public void onServiceDisconnected(ComponentName className) {
                mConnectionRemote = null;
                Log.d(LOGTAG, "Service disconnected", new Exception());
            }
        };

        Intent intent = new Intent(this, StumblerService.class);
        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        Log.d(LOGTAG, "onStart");
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(mConnection);
        mConnection = null;
        mConnectionRemote = null;
        mReceiver.unregister();
        mReceiver = null;
        Log.d(LOGTAG, "onStop");
    }

    protected void updateUI() {
        // TODO time this to make sure we're not blocking too long on mConnectionRemote
        // if we care, we can bundle this into one call -- or use android to remember
        // the state before the rotation.

        if (mConnectionRemote == null) {
            return;
        }

        Log.d(LOGTAG, "Updating UI");
        boolean scanning = false;
        scanning = mConnectionRemote.isScanning();

        CompoundButton scanningBtn = (CompoundButton) findViewById(R.id.toggle_scanning);
        scanningBtn.setChecked(scanning);

        int locationsScanned = 0;
        double latitude = 0;
        double longitude = 0;
        int wifiStatus = WifiScanner.STATUS_IDLE;
        int APs = 0;
        int visibleAPs = 0;
        int cellInfoScanned = 0;
        int currentCellInfo = 0;
        boolean isGeofenced = false;

        scanning = mConnectionRemote.isScanning();
        locationsScanned = mConnectionRemote.getLocationCount();
        latitude = mConnectionRemote.getLatitude();
        longitude = mConnectionRemote.getLongitude();
        wifiStatus = mConnectionRemote.getWifiStatus();
        APs = mConnectionRemote.getAPCount();
        visibleAPs = mConnectionRemote.getVisibleAPCount();
        cellInfoScanned = mConnectionRemote.getCellInfoCount();
        currentCellInfo = mConnectionRemote.getCurrentCellInfoCount();
        isGeofenced = mConnectionRemote.isGeofenced();

        String lastLocationString = (mGpsFixes > 0 && locationsScanned > 0)
                                    ? formatLocation(latitude, longitude)
                                    : "-";

        formatTextView(R.id.gps_satellites, R.string.gps_satellites, mGpsFixes, mGpsSats);
        formatTextView(R.id.last_location, R.string.last_location, lastLocationString);
        formatTextView(R.id.wifi_access_points, R.string.wifi_access_points, APs);
        setVisibleAccessPoints(scanning, wifiStatus, visibleAPs);
        formatTextView(R.id.cells_visible, R.string.cells_visible, currentCellInfo);
        formatTextView(R.id.cells_scanned, R.string.cells_scanned, cellInfoScanned);
        formatTextView(R.id.locations_scanned, R.string.locations_scanned, locationsScanned);
        mConnectionRemote.checkPrefs();
        mNeedsUpdate = false;
        if (mGeofenceHere) {
            if (mGpsFixes > 0 && locationsScanned > 0) {
                mConnectionRemote.getPrefs().setGeofenceLatLong((float)latitude, (float)longitude);
                mConnectionRemote.getPrefs().setGeofenceEnabled(true);
                mConnectionRemote.getPrefs().setGeofenceHere(false);
                mGeofenceHere = false;
                setGeofenceText();
            }
            mNeedsUpdate = true;
        }
        TextView geofence_tv = (TextView) findViewById(R.id.geofence_status);
        geofence_tv.setTextColor(isGeofenced ? Color.RED : Color.BLACK);
    }

    public void onToggleScanningClicked(View v) {
        if (mConnectionRemote == null) {
            return;
        }

        boolean scanning = mConnectionRemote.isScanning();
        Log.d(LOGTAG, "Connection remote return: isScanning() = " + scanning);

        if (scanning) {
            stopScanning();
        } else {
            startScanning();
            checkGps();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_about:
                startActivity(new Intent(getApplication(), AboutActivity.class));
                return true;
            case R.id.action_preferences:
                PreferencesScreen.setPrefs(mConnectionRemote.getPrefs());
                startActivity(new Intent(getApplication(), PreferencesScreen.class));
                return true;
            case R.id.action_view_leaderboard:
                Uri.Builder builder = Uri.parse(LEADERBOARD_URL).buildUpon();
                builder.fragment(mConnectionRemote.getPrefs().getNickname());
                Uri leaderboardUri = builder.build();
                Intent openLeaderboard = new Intent(Intent.ACTION_VIEW, leaderboardUri);
                startActivity(openLeaderboard);
                return true;
            case R.id.action_test_mls:
                Intent intent = new Intent(this, MapActivity.class);
                startActivity(intent);
                return true;
            case R.id.action_upload_observations:
                UploadReportsDialog newFragment = new UploadReportsDialog();
                newFragment.show(getSupportFragmentManager(), "UploadReportsDialog");
                return true;
            default:
                return super.onOptionsItemSelected(item);

        }
    }

    @TargetApi(9)
    private void enableStrictMode() {
        if (VERSION.SDK_INT < 9) {
            return;
        }

        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                                                              .detectAll()
                                                              .permitDiskReads()
                                                              .permitDiskWrites()
                                                              .penaltyLog().build());

        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                                                      .detectAll()
                                                      .penaltyLog().build());
    }

    private void setVisibleAccessPoints(boolean scanning, int wifiStatus, int apsCount) {
        String status;
        if (!scanning) {
            status = "";
        } else {
            switch (wifiStatus) {
                case WifiScanner.STATUS_IDLE:
                    status = "";
                    break;
                case WifiScanner.STATUS_WIFI_DISABLED:
                    status = getString(R.string.wifi_disabled);
                    break;
                case WifiScanner.STATUS_ACTIVE:
                    status = String.valueOf(apsCount);
                    break;
                default:
                    throw new IllegalArgumentException();
            }
        }

        formatTextView(R.id.visible_wifi_access_points, R.string.visible_wifi_access_points, status);
    }

    private void formatTextView(int textViewId, int stringId, Object... args) {
        TextView textView = (TextView) findViewById(textViewId);
        String str = getResources().getString(stringId);
        str = String.format(str, args);
        textView.setText(str);
    }

    private String formatLocation(double latitude, double longitude) {
        final String coordinateFormatString = getResources().getString(R.string.coordinate);
        return String.format(coordinateFormatString, latitude, longitude);
    }

    private void setGeofenceText() {
        if (mConnectionRemote.getPrefs().getGeofenceEnabled()) {
            float latLong[] = mConnectionRemote.getPrefs().getGeofenceLatLong();
            formatTextView(R.id.geofence_status, R.string.geofencing_on, latLong[0], latLong[1]);
        } else {
            formatTextView(R.id.geofence_status, R.string.geofencing_off);
        }
    }

    private final LoaderManager.LoaderCallbacks<Cursor> mSyncStatsLoaderCallbacks =
            new LoaderManager.LoaderCallbacks<Cursor>() {

        @Override
        public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
            return new CursorLoader(MainActivity.this, DatabaseContract.Stats.CONTENT_URI,
                    null, null, null, null);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
            long lastUploadTime = 0;
            long observationsSent = 0;
            if (BuildConfig.DEBUG) Log.v(LOGTAG, "mSyncStatsLoaderCallbacks.onLoadFinished()");

            if (cursor != null) {
                cursor.moveToPosition(-1);
                while (cursor.moveToNext()) {
                    String key = cursor.getString(cursor.getColumnIndex(DatabaseContract.Stats.KEY));
                    String value = cursor.getString(cursor.getColumnIndex(DatabaseContract.Stats.VALUE));
                    if (DatabaseContract.Stats.KEY_LAST_UPLOAD_TIME.equals(key)) {
                        lastUploadTime = Long.valueOf(value);
                    }else if (DatabaseContract.Stats.KEY_OBSERVATIONS_SENT.equals(key)) {
                        observationsSent = Long.valueOf(value);
                    }
                }
            }
            String lastUploadTimeString = (lastUploadTime > 0)
                    ? DateTimeUtils.formatTimeForLocale(lastUploadTime)
                    : "-";
            formatTextView(R.id.last_upload_time, R.string.last_upload_time, lastUploadTimeString);
            formatTextView(R.id.reports_sent, R.string.reports_sent, observationsSent);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> cursorLoader) {

        }
    };

    private void startScanning() {
        mConnectionRemote.startForeground(NOTIFICATION_ID, buildNotification());
        mConnectionRemote.startScanning();
    }

    private void stopScanning() {
        mConnectionRemote.stopForeground(true);
        mConnectionRemote.stopScanning();
        SyncUtils.TriggerRefresh(false);
    }

    private Notification buildNotification() {
        Context context = getApplicationContext();
        Intent turnOffIntent = new Intent(INTENT_TURN_OFF);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, turnOffIntent, 0);

        Intent notificationIntent = new Intent(context, MainActivity.class);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_FROM_BACKGROUND);
        PendingIntent contentIntent = PendingIntent.getActivity(context, NOTIFICATION_ID,
                                                                notificationIntent,
                                                                PendingIntent.FLAG_CANCEL_CURRENT);

        return new NotificationCompat.Builder(context)
                                     .setSmallIcon(R.drawable.ic_status_scanning)
                                     .setContentTitle(getText(R.string.service_name))
                                     .setContentText(getText(R.string.service_scanning))
                                     .setContentIntent(contentIntent)
                                     .setOngoing(true)
                                     .setPriority(NotificationCompat.PRIORITY_LOW)
                                     .addAction(R.drawable.ic_action_cancel,
                                                getString(R.string.stop_scanning), pendingIntent)
                                     .build();

    }
}
