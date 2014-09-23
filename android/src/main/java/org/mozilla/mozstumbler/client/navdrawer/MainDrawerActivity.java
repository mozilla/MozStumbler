package org.mozilla.mozstumbler.client.navdrawer;

import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.MainApp;
import org.mozilla.mozstumbler.client.PreferencesScreen;
import org.mozilla.mozstumbler.client.UploadReportsDialog;

public class MainDrawerActivity extends ActionBarActivity {
    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_drawer);

        getSupportActionBar().setTitle(getString(R.string.app_name));

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        View drawer = findViewById(R.id.left_drawer);

        mDrawerToggle = new ActionBarDrawerToggle (
                this,                  /* host Activity */
                mDrawerLayout,         /* DrawerLayout object */
                R.drawable.ic_drawer,  /* nav drawer icon to replace 'Up' caret */
                R.string.drawer_open,  /* "open drawer" description */
                R.string.drawer_close  /* "close drawer" description */
        ) {

            /** Called when a drawer has settled in a completely closed state. */
            public void onDrawerClosed(View view) {

            }

            /** Called when a drawer has settled in a completely open state. */
            public void onDrawerOpened(View drawerView) {

            }
        };

        // Set the drawer toggle as the DrawerListener
        mDrawerLayout.setDrawerListener(mDrawerToggle);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
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
            case R.id.action_preferences:
                PreferencesScreen.setPrefs(getApp().getPrefs());
                startActivity(new Intent(getApplication(), PreferencesScreen.class));
                return true;
            case R.id.action_view_leaderboard:

                return true;
            case R.id.action_upload_observations:
                UploadReportsDialog newFragment = new UploadReportsDialog();
                newFragment.show(getSupportFragmentManager(), "UploadReportsDialog");
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

}
