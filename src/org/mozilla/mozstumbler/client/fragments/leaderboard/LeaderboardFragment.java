package org.mozilla.mozstumbler.client.fragments.leaderboard;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;

import org.mozilla.mozstumbler.R;

/**
 * Created by JeremyChiang on 2014-08-05.
 */
public class LeaderboardFragment extends Fragment {

    public interface OnSettingsSelectedListener {
        public void settingsSelected();
    }

    private Spinner filterSpinner;
    private ImageButton settingsButton;

    private YourStatsFragment yourStatsFragment;
    private YourRankFragment yourRankFragment;
    private TopTenFragment topTenFragment;

    private OnSettingsSelectedListener settingsSelectedListener;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_leaderboard, container, false);

        setupFilterSpinner(rootView);
        setupSettingsButton(rootView);

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

    private void setupFilterSpinner(View rootView) {
        filterSpinner = (Spinner)rootView.findViewById(R.id.leaderboard_filter_spinner);

        ArrayAdapter<CharSequence> adapter =
                ArrayAdapter.createFromResource(getActivity(),
                        R.array.leaderboard_filter_array,
                        R.layout.stats_filter_spinner_item);
        adapter.setDropDownViewResource(R.layout.stats_filter_dropdown_item);

        filterSpinner.setAdapter(adapter);
    }

    private void setupSettingsButton(View rootView) {
        settingsButton = (ImageButton)rootView.findViewById(R.id.leaderboard_settings_image_button);

        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                settingsSelectedListener.settingsSelected();
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

    public void setSettingsSelectedListener(OnSettingsSelectedListener settingsSelectedListener) {
        this.settingsSelectedListener = settingsSelectedListener;
    }
}
