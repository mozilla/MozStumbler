/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.navdrawer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.Switch;

import org.mozilla.mozstumbler.BuildConfig;
import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.ClientPrefs;
import org.mozilla.mozstumbler.client.IMainActivity;
import org.mozilla.mozstumbler.client.MainApp;
import org.mozilla.mozstumbler.client.Updater;
import org.mozilla.mozstumbler.client.mapview.MapFragment;
import org.mozilla.mozstumbler.client.subactivities.FirstRunFragment;
import org.mozilla.mozstumbler.client.subactivities.LeaderboardActivity;
import org.mozilla.mozstumbler.service.stumblerthread.StumblerServiceIntentActions;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.log.ILogger;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

import java.util.concurrent.atomic.AtomicBoolean;

public class MainDrawerActivity
        extends ActionBarActivity
        implements IMainActivity {

    private ILogger Log = (ILogger) ServiceLocator.getInstance().getService(ILogger.class);
    private static final String LOG_TAG = LoggerUtil.makeLogTag(MainDrawerActivity.class);

    private static final int MENU_START_STOP = 1;
    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;
    private MetricsView mMetricsView;
    private MapFragment mMapFragment;
    private MenuItem mMenuItemStartStop;
    final CompoundButton.OnCheckedChangeListener mStartStopButtonListener =
            new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    mMapFragment.toggleScanning(mMenuItemStartStop);
                }
            };


    // These are counts that come back from the service on request
    private int mSvcVisibleAP = 0;
    private int mSvcVisibleCell = 0;
    private int mSvcObservationPoints = 0;
    private int mSvcUniqueCell = 0;
    private int mSvcUniqueAP = 0;

    private final BroadcastReceiver svcStatsRespReceiver = new BroadcastReceiver() {
        // This captures state change from the ScanManager
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            } else if (!intent.getAction().startsWith(StumblerServiceIntentActions.SVC_RESP_NS)) {
                return;
            }

            int count = intent.getIntExtra("count", 0);
            if (intent.getAction().equals(StumblerServiceIntentActions.SVC_RESP_VISIBLE_AP)) {
                mSvcVisibleAP = count;
            } else if (intent.getAction().equals(StumblerServiceIntentActions.SVC_RESP_VISIBLE_CELL)) {
                mSvcVisibleCell = count;
            } else if (intent.getAction().equals(StumblerServiceIntentActions.SVC_RESP_OBSERVATION_PT)) {
                mSvcObservationPoints = count;
            } else if (intent.getAction().equals(StumblerServiceIntentActions.SVC_RESP_UNIQUE_CELL_COUNT)) {
                mSvcUniqueCell = count;
            } else if (intent.getAction().equals(StumblerServiceIntentActions.SVC_RESP_UNIQUE_WIFI_COUNT)) {
                mSvcUniqueAP = count;
            }
        };
    };
    private AtomicBoolean initializeIntentFilters = new AtomicBoolean(false);


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_drawer);

        init();
    }

    private void init() {
        assert (findViewById(android.R.id.content) != null);

        if (!initializeIntentFilters.getAndSet(true)) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(StumblerServiceIntentActions.SVC_RESP_VISIBLE_AP);
            filter.addAction(StumblerServiceIntentActions.SVC_RESP_VISIBLE_CELL);
            filter.addAction(StumblerServiceIntentActions.SVC_RESP_OBSERVATION_PT);
            filter.addAction(StumblerServiceIntentActions.SVC_RESP_UNIQUE_CELL_COUNT);
            filter.addAction(StumblerServiceIntentActions.SVC_RESP_UNIQUE_WIFI_COUNT);
            LocalBroadcastManager.getInstance(getApplicationContext())
                    .registerReceiver(svcStatsRespReceiver,
                            filter);
        }


        if (Build.VERSION.SDK_INT > 10) {
            getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        }

        getSupportActionBar().setTitle(getString(R.string.app_name));

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerToggle = new ActionBarDrawerToggle(
                this,                  /* host Activity */
                mDrawerLayout,         /* DrawerLayout object */
                R.drawable.ic_drawer,  /* nav drawer icon to replace 'Up' caret */
                R.string.drawer_open,  /* "open drawer" description */
                R.string.drawer_close  /* "close drawer" description */
        ) {

            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
                mMapFragment.setZoomButtonsVisible(false);
            }

            @Override
            public void onDrawerClosed(View view) {
            }

            @Override
            public void onDrawerOpened(View drawerView) {
                mMetricsView.onOpened();
            }
        };

        // Set the drawer toggle as the DrawerListener
        mDrawerLayout.setDrawerListener(mDrawerToggle);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        FragmentManager fragmentManager = getSupportFragmentManager();
        Fragment fragment = fragmentManager.findFragmentById(R.id.content_frame);
        if (fragment == null) {
            mMapFragment = new MapFragment();
            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
            fragmentTransaction.add(R.id.content_frame, mMapFragment);
            fragmentTransaction.commit();
        } else {
            mMapFragment = (MapFragment) fragment;
        }

        getApp().setMainActivity(this);

        if (BuildConfig.GITHUB) {
            Updater upd = new Updater();
            upd.checkForUpdates(this, BuildConfig.MOZILLA_API_KEY);
        }

        mMetricsView = new MetricsView(findViewById(R.id.left_drawer));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);

        mMenuItemStartStop = menu.add(Menu.NONE, MENU_START_STOP, Menu.NONE, R.string.start_scanning);
        if (Build.VERSION.SDK_INT >= 14) {
            Switch s = new Switch(this);
            s.setChecked(false);
            s.setOnCheckedChangeListener(mStartStopButtonListener);
            mMenuItemStartStop.setActionView(s);
            mMenuItemStartStop.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        } else {
            MenuItemCompat.setShowAsAction(mMenuItemStartStop, MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
        }

        updateStartStopMenuItemState();
        return true;
    }

    private void updateStartStopMenuItemState() {
        if (mMenuItemStartStop == null) {
            return;
        }

        MainApp app = (MainApp) getApplication();
        if (app == null) {
            return;
        }

        if (!app.isStopped()) {
            keepScreenOn(ClientPrefs.getInstance(this).getKeepScreenOn());
        } else {
            keepScreenOn(false);
        }

        if (Build.VERSION.SDK_INT >= 14) {
            Switch s = (Switch) mMenuItemStartStop.getActionView();
            s.setOnCheckedChangeListener(null);
            if (app.isScanningOrPaused() && !s.isChecked()) {
                s.setChecked(true);
            } else if (!app.isScanningOrPaused() && s.isChecked()) {
                s.setChecked(false);
            }
            s.setOnCheckedChangeListener(mStartStopButtonListener);
        } else {
            boolean buttonStateIsScanning = mMenuItemStartStop.getTitle().equals(getString(R.string.stop_scanning));
            if (app.isScanningOrPaused() && !buttonStateIsScanning) {
                mMenuItemStartStop.setIcon(android.R.drawable.ic_media_pause);
                mMenuItemStartStop.setTitle(R.string.stop_scanning);
            } else if (!app.isScanningOrPaused() && buttonStateIsScanning) {
                mMenuItemStartStop.setIcon(android.R.drawable.ic_media_play);
                mMenuItemStartStop.setTitle(R.string.start_scanning);
            }
        }

        mMapFragment.dimToolbar();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
        mMetricsView.setMapLayerToggleListener(mMapFragment);
    }

    @Override
    public void onPostResume() {
        super.onPostResume();
        mMetricsView.update();

        ClientPrefs prefs = ClientPrefs.getInstance(this);
        if (prefs.isFirstRun()) {
            FragmentManager fm = getSupportFragmentManager();
            FirstRunFragment.showInstance(fm);
            prefs.setDontShowChangelog();
            MainApp.getAndSetHasBootedOnce();
        } else if (!MainApp.getAndSetHasBootedOnce()) {

            long currentVersionNumber = BuildConfig.VERSION_CODE;
            long savedVersionNumber = prefs.getLastVersion();
            if (currentVersionNumber != savedVersionNumber) {
                prefs.setDontShowChangelog();

                //@TODO This is where we were showing what's new dialog.
                // We can still use this spot to show a what's new with a list that is pulled from the web
            }

            findViewById(android.R.id.content).postDelayed(new Runnable() {
                @Override
                public void run() {
                    getApp().startScanning();
                }
            }, 750);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    private MainApp getApp() {
        return (MainApp) this.getApplication();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Pass the event to ActionBarDrawerToggle, if it returns
        // true, then it has handled the app icon touch event
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        switch (item.getItemId()) {
            case MENU_START_STOP:
                mMapFragment.toggleScanning(item);
                return true;
            case R.id.action_view_leaderboard:
                startActivity(new Intent(getApplication(), LeaderboardActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void updateUiOnMainThread(final boolean updateMetrics) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mMapFragment == null || mMapFragment.getActivity() == null) {
                    return;
                }

                updateStartStopMenuItemState();
                updateNumberDisplay(updateMetrics);
            }
        });
    }

    private void updateNumberDisplay(boolean updateMetrics) {
        // TODO: this is silly.  we can just ask for everything in one
        // request.
        broadcastSvcDataRequest(StumblerServiceIntentActions.SVC_REQ_VISIBLE_CELL);
        broadcastSvcDataRequest(StumblerServiceIntentActions.SVC_REQ_VISIBLE_AP);
        broadcastSvcDataRequest(StumblerServiceIntentActions.SVC_REQ_OBSERVATION_PT);
        broadcastSvcDataRequest(StumblerServiceIntentActions.SVC_REQ_UNIQUE_CELL_COUNT);
        broadcastSvcDataRequest(StumblerServiceIntentActions.SVC_REQ_UNIQUE_WIFI_COUNT);

        // TODO: the actual write to a particular UI field can probably be dispatched in the
        // BroadcastReceiver
        final boolean active = getApp().isScanning();
        mMapFragment.formatTextView(R.id.text_cells_visible, "%d", active ? mSvcVisibleCell : 0);
        mMapFragment.formatTextView(R.id.text_wifis_visible, "%d", active ? mSvcVisibleAP : 0);
        mMapFragment.formatTextView(R.id.text_observation_count, "%d", mSvcObservationPoints);

        if (updateMetrics) {
            mMetricsView.setObservationCount(mSvcObservationPoints,
                    mSvcUniqueCell,
                    mSvcUniqueAP);
            mMetricsView.update();
        }
    }

    /*
    Make a blocking synchronous request to fetch some stats from the stumbler service.
     */
    private void broadcastSvcDataRequest(String svcStatsRequestAction) {
        Intent intent = new Intent(svcStatsRequestAction);
        LocalBroadcastManager.getInstance(this.getApplicationContext()).sendBroadcastSync(intent);
    }

    @Override
    public void setUploadState(boolean isUploadingObservations) {
        mMetricsView.setUploadState(isUploadingObservations);
    }

    public void keepScreenOn(boolean isEnabled) {
        int flag = android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        if (isEnabled) {
            getWindow().addFlags(flag);
        } else {
            getWindow().clearFlags(flag);
        }
    }

    @Override
    public void isPausedDueToNoMotion(boolean isPaused) {
        mMapFragment.showPausedDueToNoMotionMessage(isPaused);
    }

    @Override
    public void stop() {
        mMapFragment.stop();
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        mMetricsView.update();
    }
}

