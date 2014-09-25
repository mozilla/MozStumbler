/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.navdrawer;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;

import org.mozilla.mozstumbler.BuildConfig;
import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.ClientPrefs;
import org.mozilla.mozstumbler.client.ClientStumblerService;
import org.mozilla.mozstumbler.client.IMainActivity;
import org.mozilla.mozstumbler.client.Updater;
import org.mozilla.mozstumbler.client.mapview.MapActivity;
import org.mozilla.mozstumbler.client.subactivities.LeaderboardActivity;
import org.mozilla.mozstumbler.client.MainApp;
import org.mozilla.mozstumbler.client.subactivities.PreferencesScreen;

public class MainDrawerActivity extends ActionBarActivity implements IMainActivity {
    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;
    private MetricsView mMetricsView;
    private MapActivity mMapActivity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_drawer);

        if (ClientPrefs.getInstance().getIsHardwareAccelerated() &&
                Build.VERSION.SDK_INT > 10) {
            getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        }

        if (ClientPrefs.getInstance().getKeepScreenOn()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        getSupportActionBar().setTitle(getString(R.string.app_name));

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerToggle = new ActionBarDrawerToggle (
                this,                  /* host Activity */
                mDrawerLayout,         /* DrawerLayout object */
                R.drawable.ic_drawer,  /* nav drawer icon to replace 'Up' caret */
                R.string.drawer_open,  /* "open drawer" description */
                R.string.drawer_close  /* "close drawer" description */
        ) {

            public void onDrawerClosed(View view) {
            }

            public void onDrawerOpened(View drawerView) {
               // mMetricsView.populate();
            }
        };

        // Set the drawer toggle as the DrawerListener
        mDrawerLayout.setDrawerListener(mDrawerToggle);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        mMapActivity = new MapActivity();
        fragmentTransaction.add(R.id.content_frame, mMapActivity);
        fragmentTransaction.commit();

        getApp().setMainActivity(this);

        Updater upd = new Updater();
        upd.checkForUpdates(this, BuildConfig.MOZILLA_API_KEY);

        mMetricsView = new MetricsView(findViewById(R.id.left_drawer), mMapActivity);
    }

    private static final int MENU_START_STOP = 1;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        MenuItem startStop = menu.add(Menu.NONE, MENU_START_STOP, Menu.NONE, R.string.start_scanning);
        if (Build.VERSION.SDK_INT >= 11) {
            startStop.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
        else {
            MenuItemCompat.setShowAsAction(startStop, MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
            MenuItem item = menu.findItem(R.id.action_preferences);
            MenuItemCompat.setShowAsAction(item, MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
        }

        boolean isScanning = ((MainApp) getApplication()).getService().isScanning();
        setStartStopMenuState(startStop, isScanning);
        return true;
    }

    private void setStartStopMenuState(MenuItem menuItem, boolean scanning) {
        if (scanning) {
            menuItem.setIcon(android.R.drawable.ic_media_pause);
            menuItem.setTitle(R.string.stop_scanning);
        } else {
            menuItem.setIcon(android.R.drawable.ic_media_play);
            menuItem.setTitle(R.string.start_scanning);
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
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
    public void displayObservationCount(int count) {
        mMapActivity.formatTextView(R.id.text_observation_count, "%d", count);
        mMetricsView.setObservationCount(count);
    }

}
