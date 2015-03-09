/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.navdrawer;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarActivity;
import android.text.Html;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import org.mozilla.mozstumbler.BuildConfig;
import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.ClientPrefs;
import org.mozilla.mozstumbler.client.ClientStumblerService;
import org.mozilla.mozstumbler.client.IMainActivity;
import org.mozilla.mozstumbler.client.MainApp;
import org.mozilla.mozstumbler.client.Updater;
import org.mozilla.mozstumbler.client.mapview.MapFragment;
import org.mozilla.mozstumbler.client.subactivities.FirstRunFragment;
import org.mozilla.mozstumbler.client.subactivities.LeaderboardActivity;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

public class MainDrawerActivity
        extends ActionBarActivity
        implements IMainActivity {

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_drawer);

        init();
    }

    private void init() {
        assert (findViewById(android.R.id.content) != null);

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

        ClientStumblerService svc = app.getService();
        if (svc == null) {
            return;
        }
        if (!svc.isStopped()) {
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
                showWhatsNewDialog();
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
        ClientStumblerService service = getApp().getService();
        if (service == null) {
            return;
        }

        mMapFragment.formatTextView(R.id.text_cells_visible, "%d", service.getVisibleCellInfoCount());
        mMapFragment.formatTextView(R.id.text_wifis_visible, "%d", service.getVisibleAPCount());

        int observationCount = service.getObservationCount();
        mMapFragment.formatTextView(R.id.text_observation_count, "%d", observationCount);

        if (updateMetrics) {
            mMetricsView.setObservationCount(observationCount, service.getUniqueCellCount(),
                    service.getUniqueAPCount(), getApp().isScanningOrPaused());
            mMetricsView.update();
        }
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


    public void showWhatsNewDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        CharSequence whats_new_text = getText(R.string.whats_new);

        if (whats_new_text.toString().trim().length() == 0) {
            // Don't show the dialog if the what's new text is
            // an empty string.
            return;
        }

        builder.setMessage(R.string.whats_new_title)
                .setCancelable(false)
                .setTitle(Html.fromHtml(getString(R.string.whats_new_title)))
                .setMessage(whats_new_text)
                .setPositiveButton(getText(R.string.ok), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }
                });


        AlertDialog alert = builder.create();
        alert.show();
        TextView msgTxt = (TextView) alert.findViewById(android.R.id.message);
        msgTxt.setTextSize(12);
    }
}

