package org.mozilla.mozstumbler.client.fragments;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.mozilla.mozstumbler.R;

/**
 * Created by JeremyChiang on 2014-08-05.
 */
public class TabBarFragment extends Fragment {

    public static enum SelectedTab {
        MAP_TAB,
        LEADERBOARD_TAB
    }

    public interface OnTabSelectedListener {
        public void tabSelected(SelectedTab selectedTab);
    }

    public interface OnBackButtonPressedListener {
        public void backButtonPressed();
    }

    private SelectedTab selectedTab;
    private OnTabSelectedListener tabSelectedListener;
    private OnBackButtonPressedListener backButtonPressedListener;

    private Button mapButton;
    private Button leaderboardButton;

    private TextView backArrow;
    private Button backButton;

    private View tabDivider;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        selectedTab = SelectedTab.MAP_TAB;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_tab_bar, container, false);

        mapButton = (Button)rootView.findViewById(R.id.map_button);
        leaderboardButton = (Button)rootView.findViewById(R.id.leaderboard_button);

        backButton = (Button)rootView.findViewById(R.id.back_button);
        backArrow = (TextView)rootView.findViewById(R.id.back_arrow);

        tabDivider = rootView.findViewById(R.id.tab_divider);

        setSelectedTab(selectedTab);

        mapButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setSelectedTab(SelectedTab.MAP_TAB);
            }
        });

        leaderboardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setSelectedTab(SelectedTab.LEADERBOARD_TAB);
            }
        });

        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                backButtonPressedListener.backButtonPressed();
            }
        });

        return rootView;
    }

    public void setSelectedTab(SelectedTab selectedTab) {
        this.selectedTab = selectedTab;

        switch(this.selectedTab) {
            case MAP_TAB:
                mapButton.setAlpha(1.0f);
                leaderboardButton.setAlpha(0.5f);
                break;
            case LEADERBOARD_TAB:
                mapButton.setAlpha(0.5f);
                leaderboardButton.setAlpha(1.0f);
                break;
            default:
                break;
        }

        if (tabSelectedListener != null) {
            tabSelectedListener.tabSelected(this.selectedTab);
        }
    }

    public void setTabSelectedListener(OnTabSelectedListener tabSelectedListener) {
        this.tabSelectedListener = tabSelectedListener;
    }

    public void setBackButtonPressedListener(OnBackButtonPressedListener backButtonPressedListener) {
        this.backButtonPressedListener = backButtonPressedListener;
    }

    public void toggleBackButton(boolean enableBackButton, String backButtonTitle) {
        if (enableBackButton) {
            backArrow.setVisibility(View.VISIBLE);
            backButton.setVisibility(View.VISIBLE);
            backButton.setText(backButtonTitle);

            mapButton.setVisibility(View.INVISIBLE);
            leaderboardButton.setVisibility(View.INVISIBLE);
            tabDivider.setVisibility(View.INVISIBLE);

        } else {
            backArrow.setVisibility(View.INVISIBLE);
            backButton.setVisibility(View.INVISIBLE);
            backButton.setText("");

            mapButton.setVisibility(View.VISIBLE);
            leaderboardButton.setVisibility(View.VISIBLE);
            tabDivider.setVisibility(View.VISIBLE);
        }
    }
}
