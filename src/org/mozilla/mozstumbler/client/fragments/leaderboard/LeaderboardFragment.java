package org.mozilla.mozstumbler.client.fragments.leaderboard;

import android.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Spinner;

import org.json.JSONArray;
import org.json.JSONObject;
import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.leaderboard.GalaxyManager;
import org.mozilla.mozstumbler.client.leaderboard.GalaxyRestClient;

/**
 * Created by JeremyChiang on 2014-08-05.
 */
public class LeaderboardFragment extends Fragment implements GalaxyManager.GalaxyManagerListener {
    private static final String TAG = LeaderboardFragment.class.getName();

    private GalaxyManager galaxyManager;
    private JSONArray scores;

    private Spinner filterSpinner;
    private ImageView filterSpinnerArrow;

    private YourStatsFragment yourStatsFragment;
    private YourRankFragment yourRankFragment;
    private TopTenFragment topTenFragment;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_leaderboard, container, false);

        setupGalaxyLeaderboard();
        setupFilterSpinner(rootView);
        setupFilterSpinnerArrow(rootView);

        addYourStatsFragment();
        addSectionTitleFragment(getString(R.string.leaderboard_title));
        addYourRankFragment();
        addTopTenFragment();

        // Prevent touch from falling through this fragment and interact with the map underneath.
        rootView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                return true;
            }
        });

        return rootView;
    }

    private void setupGalaxyLeaderboard() {
        galaxyManager = new GalaxyManager(this);
        galaxyManager.getGame(GalaxyRestClient.GAME_SLUG);
        galaxyManager.getLeaderboard(GalaxyRestClient.GAME_SLUG, GalaxyRestClient.LEADERBOARD_SLUG);
        galaxyManager.getScores(GalaxyRestClient.GAME_SLUG, GalaxyRestClient.LEADERBOARD_SLUG);
    }

    @Override
    public void gameFound(JSONObject game) {
        if (game == null) {
            galaxyManager.createGame(
                    getActivity().getApplicationContext(),
                    GalaxyRestClient.GAME_SLUG,
                    GalaxyRestClient.GAME_NAME,
                    GalaxyRestClient.GAME_DESC,
                    GalaxyRestClient.GAME_URL);
        }
    }

    @Override
    public void leaderboardFound(JSONObject leaderboard) {
        if (leaderboard == null) {
            galaxyManager.createLeaderboard(
                    getActivity().getApplicationContext(),
                    GalaxyRestClient.GAME_SLUG,
                    GalaxyRestClient.LEADERBOARD_SLUG,
                    GalaxyRestClient.LEADERBOARD_NAME
            );
        }
    }

    @Override
    public void scoresFound(JSONArray scores) {
        this.scores = scores;

        if (this.scores != null) {
            Log.d(TAG, "Scores found = " + scores.toString());
        }
    }

    private void setupFilterSpinner(View rootView) {
        filterSpinner = (Spinner)rootView.findViewById(R.id.leaderboard_filter_spinner);

        ArrayAdapter<CharSequence> adapter =
                ArrayAdapter.createFromResource(getActivity(),
                        R.array.leaderboard_filter_array,
                        R.layout.stats_filter_spinner_item);
        adapter.setDropDownViewResource(R.layout.stats_filter_dropdown_item);

        filterSpinner.setAdapter(adapter);
    }

    private void setupFilterSpinnerArrow(View rootView) {
        filterSpinnerArrow = (ImageView)rootView.findViewById(R.id.leaderboard_filter_spinner_arrow);
        filterSpinnerArrow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                filterSpinner.performClick();
            }
        });
    }

    private void addYourStatsFragment() {
        yourStatsFragment = new YourStatsFragment();

        getFragmentManager().beginTransaction()
                .add(R.id.leaderboard_scroll_view_content_container, yourStatsFragment)
                .commit();
    }

    private void addSectionTitleFragment(String title) {
        SectionTitleFragment sectionTitleFragment = new SectionTitleFragment();

        Bundle titleBundle = new Bundle();
        titleBundle.putString(SectionTitleFragment.KEY_SECTION_TITLE, title);
        sectionTitleFragment.setArguments(titleBundle);

        getFragmentManager().beginTransaction()
                .add(R.id.leaderboard_scroll_view_content_container, sectionTitleFragment)
                .commit();
    }

    private void addYourRankFragment() {
        yourRankFragment = new YourRankFragment();

        getFragmentManager().beginTransaction()
                .add(R.id.leaderboard_scroll_view_content_container, yourRankFragment)
                .commit();
    }

    private void addTopTenFragment() {
        topTenFragment = new TopTenFragment();

        getFragmentManager().beginTransaction()
                .add(R.id.leaderboard_scroll_view_content_container, topTenFragment)
                .commit();
    }
}
