package org.mozilla.mozstumbler;

import org.mozilla.mozstumbler.preferences.PreferencesScreen;
import org.mozilla.mozstumbler.preferences.Prefs;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.StrictMode;
import android.provider.Settings;
import android.text.Editable;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final String LOGTAG = MainActivity.class.getName();
    private static final String LEADERBOARD_URL = "https://location.services.mozilla.com/leaders";

    private Prefs                    mPrefs;
    private ScannerServiceInterface  mConnectionRemote;
    private ServiceConnection        mConnection;
    private ServiceBroadcastReceiver mReceiver;
    private int                      mGpsFixes;

    private class ServiceBroadcastReceiver extends BroadcastReceiver {
        private boolean mReceiverIsRegistered;

        public void register() {
            if (!mReceiverIsRegistered) {
                registerReceiver(this, new IntentFilter(ScannerService.MESSAGE_TOPIC));
                mReceiverIsRegistered = true;
            }
        }

        public void unregister() {
            if (mReceiverIsRegistered) {
                unregisterReceiver(this);
                mReceiverIsRegistered = false;
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(ScannerService.MESSAGE_TOPIC)) {

                String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);

                if (subject.equals("Reporter")) {
                    updateUI();
                    Log.d(LOGTAG, "Received a reporter intent...");
                    return;
                } 

                if (subject.equals("Scanner")) {
                    if (intent.hasExtra("fixes")) {
                        mGpsFixes = intent.getIntExtra("fixes", 0);
                    }
                    else if (intent.hasExtra("enable")) {
                        int enable = intent.getIntExtra("enable", -1);

                        if (mConnectionRemote != null) {
                            try {
                                if (enable == 1) {
                                  Log.d(LOGTAG, "Enabling scanning");
                                    mConnectionRemote.startScanning();
                                } else if (enable == 0) {
                                  Log.d(LOGTAG, "Disabling scanning");
                                    mConnectionRemote.stopScanning();
                                }
                            } catch (RemoteException e) {
                              Log.e(LOGTAG, "", e);
                            }
                        }
                    }

                    updateUI();
                    Log.d(LOGTAG, "Received a scanner intent...");
                    return;
                }

                Log.e(LOGTAG, "Unknown scanner message!");
                return;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enableStrictMode();
        setContentView(R.layout.activity_main);

        new Prefs(this).setDefaultValues();
        Updater.checkForUpdates(this);

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

        checkGps();

        mReceiver = new ServiceBroadcastReceiver();
        mReceiver.register();
        mPrefs = new Prefs(this);

        mConnection = new ServiceConnection() {
            public void onServiceConnected(ComponentName className, IBinder binder) {
                mConnectionRemote = ScannerServiceInterface.Stub.asInterface(binder);
                Log.d(LOGTAG, "Service connected");

                // each time we reconnect, check to see if we're suppose to be
                // in power saving mode.  if not, start the scanning. TODO: we
                // shouldn't just stopScanning if we find that we are in PSM.
                // Instead, we should see if we were scanning do to an activity
                // recognition.  If we were, don't stop.
                if (mConnectionRemote != null) {
                    try {
                        if (mPrefs.getPowerSavingMode()) {
                            mConnectionRemote.stopScanning();
                        } else {
                            mConnectionRemote.startScanning();
                        }
                    } catch (RemoteException e) {
                        Log.e(LOGTAG, "", e);
                    }
                }
                updateUI();
            }

            public void onServiceDisconnected(ComponentName className) {
                mConnectionRemote = null;
                Log.d(LOGTAG, "Service disconnected", new Exception());
            }
        };

        Intent intent = new Intent(this, ScannerService.class);
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
        int locationsScanned = 0;
        double latitude = 0;
        double longitude = 0;
        int APs = 0;
        int visibleAPs = 0;
        int cellInfoScanned = 0;
        int currentCellInfo = 0;
        long lastUploadTime = 0;
        long reportsSent = 0;
        String detectedActivity = null;

        try {
            scanning = mConnectionRemote.isScanning();
            locationsScanned = mConnectionRemote.getLocationCount();
            latitude = mConnectionRemote.getLatitude();
            longitude = mConnectionRemote.getLongitude();
            APs = mConnectionRemote.getAPCount();
            visibleAPs = mConnectionRemote.getVisibleAPCount();
            cellInfoScanned = mConnectionRemote.getCellInfoCount();
            currentCellInfo = mConnectionRemote.getCurrentCellInfoCount();
            lastUploadTime = mConnectionRemote.getLastUploadTime();
            reportsSent = mConnectionRemote.getReportsSent();
            detectedActivity = mConnectionRemote.getDetectedActivity();
        } catch (RemoteException e) {
            Log.e(LOGTAG, "", e);
        }

        Log.d(LOGTAG, "!!!!! we are scanning?? = " + scanning);
        ProgressBar progress = (ProgressBar) findViewById(R.id.progressBar);
        if (scanning) {
            progress.setVisibility(View.VISIBLE);
        } else {
            progress.setVisibility(View.GONE);
        }


        String lastUploadTimeString = (lastUploadTime > 0)
                                      ? DateTimeUtils.formatTimeForLocale(lastUploadTime)
                                      : "-";

        String lastLocationString = (mGpsFixes > 0 && locationsScanned > 0)
                                    ? formatLocation(latitude, longitude)
                                    : "-";

        formatTextView(R.id.detected_activity, R.string.detected_activity, detectedActivity);
        formatTextView(R.id.gps_satellites, R.string.gps_satellites, mGpsFixes);
        formatTextView(R.id.last_location, R.string.last_location, lastLocationString);
        formatTextView(R.id.visible_wifi_access_points, R.string.visible_wifi_access_points, visibleAPs);
        formatTextView(R.id.wifi_access_points, R.string.wifi_access_points, APs);
        formatTextView(R.id.cells_visible, R.string.cells_visible, currentCellInfo);
        formatTextView(R.id.cells_scanned, R.string.cells_scanned, cellInfoScanned);
        formatTextView(R.id.locations_scanned, R.string.locations_scanned, locationsScanned);
        formatTextView(R.id.last_upload_time, R.string.last_upload_time, lastUploadTimeString);
        formatTextView(R.id.reports_sent, R.string.reports_sent, reportsSent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_about) {
            startActivity(new Intent(getApplication(), AboutActivity.class));
            return true;
        }
        
        if (item.getItemId() == R.id.action_preferences) {
            startActivity(new Intent(getApplication(), PreferencesScreen.class));
            return true;
        }

        if (item.getItemId() == R.id.action_view_leaderboard) {
            Intent openLeaderboard = new Intent(Intent.ACTION_VIEW, Uri.parse(LEADERBOARD_URL));
            startActivity(openLeaderboard);
            return true;
        }

        if (item.getItemId() == R.id.action_test_mls) {
            Intent intent = new Intent(this, MapActivity.class);
            startActivity(intent);
            return true;
        }

        if (item.getItemId() == R.id.action_exit) {
            if (mConnectionRemote != null) {
                try {
                    mConnectionRemote.stopScanning();
                } catch (RemoteException e) {
                    Log.e(LOGTAG, "", e);
                }
            }
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
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
}
