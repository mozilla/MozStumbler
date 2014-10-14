/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.navdrawer;

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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.Switch;

import org.mozilla.mozstumbler.BuildConfig;
import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.ClientPrefs;
import org.mozilla.mozstumbler.client.ClientStumblerService;
import org.mozilla.mozstumbler.client.IMainActivity;
import org.mozilla.mozstumbler.client.MainApp;
import org.mozilla.mozstumbler.client.Updater;
import org.mozilla.mozstumbler.client.mapview.MapActivity;
import org.mozilla.mozstumbler.client.subactivities.FirstRunFragment;
import org.mozilla.mozstumbler.client.subactivities.LeaderboardActivity;
import org.mozilla.mozstumbler.client.subactivities.PreferencesScreen;
import org.mozilla.mozstumbler.service.core.http.HttpUtil;
import org.mozilla.mozstumbler.service.core.http.IHttpUtil;

public class MainDrawerActivity
        extends ActionBarActivity
        implements IMainActivity {

    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;
    private MetricsView mMetricsView;
    private MapActivity mMapActivity;
    private MenuItem mMenuItemStartStop;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_drawer);

        if (Build.VERSION.SDK_INT > 10) {
            getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        }

        keepScreenOnChanged(ClientPrefs.getInstance().getKeepScreenOn());

        getSupportActionBar().setTitle(getString(R.string.app_name));

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerToggle = new ActionBarDrawerToggle (
                this,                  /* host Activity */
                mDrawerLayout,         /* DrawerLayout object */
                R.drawable.ic_drawer,  /* nav drawer icon to replace 'Up' caret */
                R.string.drawer_open,  /* "open drawer" description */
                R.string.drawer_close  /* "close drawer" description */
        ) {

            @Override
            public void onDrawerClosed(View view) {}

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
            mMapActivity = new MapActivity();
            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
            fragmentTransaction.add(R.id.content_frame, mMapActivity);
            fragmentTransaction.commit();
        } else {
            mMapActivity = (MapActivity) fragment;
        }

        getApp().setMainActivity(this);

        IHttpUtil httpUtil = new HttpUtil();
        Updater upd = new Updater(httpUtil);
        upd.checkForUpdates(this, BuildConfig.MOZILLA_API_KEY);

        mMetricsView = new MetricsView(findViewById(R.id.left_drawer));
    }

    private static final int MENU_START_STOP = 1;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);

        mMenuItemStartStop = menu.add(Menu.NONE, MENU_START_STOP, Menu.NONE, R.string.start_scanning);
        if (Build.VERSION.SDK_INT >= 11) {
            Switch s = new Switch(this);
            s.setChecked(false);
            s.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    mMapActivity.toggleScanning(mMenuItemStartStop);
                }
            });
            mMenuItemStartStop.setActionView(s);
            mMenuItemStartStop.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
        else {
            MenuItemCompat.setShowAsAction(mMenuItemStartStop, MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
            MenuItem item = menu.findItem(R.id.action_preferences);
            MenuItemCompat.setShowAsAction(item, MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
        }

        setStartStopMenuItemState();
        return true;
    }

    public void setStartStopMenuItemState() {
        if (mMenuItemStartStop == null) {
            return;
        }
        boolean isScanning = ((MainApp) getApplication()).getService().isScanning();
        if (Build.VERSION.SDK_INT >= 11) {
            Switch s = (Switch)mMenuItemStartStop.getActionView();
            if (isScanning) {
                s.setChecked(true);
            } else {
                s.setChecked(false);
            }
        } else {
            if (isScanning) {
                mMenuItemStartStop.setIcon(android.R.drawable.ic_media_pause);
                mMenuItemStartStop.setTitle(R.string.stop_scanning);
            } else {
                mMenuItemStartStop.setIcon(android.R.drawable.ic_media_play);
                mMenuItemStartStop.setTitle(R.string.start_scanning);
            }
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }

    @Override
    public void onStart() {
        super.onStart();
        mMetricsView.setMapLayerToggleListener(mMapActivity);
        mMetricsView.update();

        if (ClientPrefs.getInstance().isFirstRun()) {
            FragmentManager fm = getSupportFragmentManager();
            FirstRunFragment.showInstance(fm);
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
                mMapActivity.toggleScanning(item);
                return true;
            case R.id.action_preferences:
                PreferencesScreen.setPrefs(getApp().getPrefs());
                startActivity(new Intent(getApplication(), PreferencesScreen.class));
                return true;
            case R.id.action_view_leaderboard:
                startActivity(new Intent(getApplication(), LeaderboardActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    public void updateUiOnMainThread() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setStartStopMenuItemState();
                updateNumberDisplay();
            }
        });
    }

    private void updateNumberDisplay() {
        ClientStumblerService service = getApp().getService();
        if (service == null) {
            return;
        }

        mMapActivity.formatTextView(R.id.text_cells_visible, "%d", service.getCurrentCellInfoCount());
        mMapActivity.formatTextView(R.id.text_wifis_visible, "%d", service.getVisibleAPCount());

        mMetricsView.update();
    }

    @Override
    public void displayObservationCount(final int count) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mMapActivity.formatTextView(R.id.text_observation_count, "%d", count);
                mMetricsView.setObservationCount(count);
            }
        });
    }

    @Override
    public void setUploadState(boolean isUploadingObservations) {
        mMetricsView.setUploadState(isUploadingObservations);
    }

    public void keepScreenOnChanged(boolean isEnabled) {
        int flag = android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        if (isEnabled) {
            getWindow().addFlags(flag);
        } else {
            getWindow().clearFlags(flag);
        }
    }
}
