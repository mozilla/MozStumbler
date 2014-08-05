package org.mozilla.mozstumbler.client.activities;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;

import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.fragments.SettingsFragment;
import org.mozilla.mozstumbler.client.fragments.TabBarFragment;
import org.mozilla.mozstumbler.client.fragments.leaderboard.LeaderboardFragment;
import org.mozilla.mozstumbler.client.fragments.map.MapFragment;

/**
 * Created by JeremyChiang on 2014-08-05.
 */
public class MozStumblerActivity extends Activity implements TabBarFragment.OnTabSelectedListener,
        LeaderboardFragment.OnSettingsSelectedListener, TabBarFragment.OnBackButtonPressedListener {

    private TabBarFragment tabBarFragment;

    private MapFragment mapFragment;
    private LeaderboardFragment leaderboardFragment;
    private SettingsFragment settingsFragment;

    private Fragment currentContentFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_moz_stumbler);

        final ActionBar actionBar = getActionBar();
        actionBar.hide();

        if (savedInstanceState == null) {
            tabBarFragment = new TabBarFragment();
            tabBarFragment.setTabSelectedListener(this);
            tabBarFragment.setBackButtonPressedListener(this);

            mapFragment = new MapFragment();
            leaderboardFragment = new LeaderboardFragment();
            leaderboardFragment.setSettingsSelectedListener(this);

            settingsFragment = new SettingsFragment();

            showTabBarFragment();
            showMapFragment();
        }
    }

    private void showTabBarFragment() {
        getFragmentManager().beginTransaction()
                .add(R.id.container, tabBarFragment)
                .commit();
    }

    private void showMapFragment() {
        if (currentContentFragment == null) {

            getFragmentManager().beginTransaction()
                    .add(R.id.content, mapFragment)
                    .commit();

            currentContentFragment = mapFragment;
        }
        else if (currentContentFragment != mapFragment) {

            getFragmentManager().beginTransaction()
                    .remove(currentContentFragment)
                    .commit();

            currentContentFragment = mapFragment;
        }
    }

    private void showLeaderboardFragment() {
        getFragmentManager().beginTransaction()
                .add(R.id.content, leaderboardFragment)
                .commit();

        currentContentFragment = leaderboardFragment;
    }

    private void showSettingsFragment() {
        getFragmentManager().beginTransaction()
                .add(R.id.content, settingsFragment)
                .commit();

        currentContentFragment = settingsFragment;

        tabBarFragment.toggleBackButton(true, getString(R.string.settings_title));
    }

    @Override
    public void tabSelected(TabBarFragment.SelectedTab selectedTab) {
        switch (selectedTab) {
            case MAP_TAB:
                showMapFragment();
                break;
            case LEADERBOARD_TAB:
                showLeaderboardFragment();
                break;
            default:
                break;
        }
    }

    @Override
    public void settingsSelected() {
        showSettingsFragment();
    }

    @Override
    public void backButtonPressed() {
        if (currentContentFragment == settingsFragment) {

            getFragmentManager().beginTransaction()
                    .remove(settingsFragment)
                    .commit();

            currentContentFragment = leaderboardFragment;

            tabBarFragment.toggleBackButton(false, null);
        }
    }
}
