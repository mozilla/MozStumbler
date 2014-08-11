package org.mozilla.mozstumbler.client.fragments.leaderboard;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.MainActivity;
import org.mozilla.mozstumbler.client.adapters.StatsAdapter;
import org.mozilla.mozstumbler.client.developers.SampleData;
import org.mozilla.mozstumbler.client.models.User;
import org.mozilla.mozstumbler.client.utilities.MozHelper;

/**
 * Created by JeremyChiang on 2014-08-05.
 */
public class YourStatsFragment extends Fragment {

    private StatsAdapter statsAdapter;
    private LinearLayout statsListView;

    private User user;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_your_stats, container, false);

        user = ((MainActivity)getActivity()).getUser();

        statsListView = (LinearLayout)rootView.findViewById(R.id.your_stats_linear_layout_list_container);

        statsAdapter = new StatsAdapter(getActivity(), user.getStats());

        for (int i = 0; i < statsAdapter.getCount(); i++) {
            statsListView.addView(statsAdapter.getView(i, null, null));
        }

        addPointsAccumulatedView();

        return rootView;
    }

    private void addPointsAccumulatedView() {
        View pointsAccumulatedView = View.inflate(getActivity(), R.layout.list_item_points_accumulated, null);

        pointsAccumulatedView.setBackgroundColor(getResources().getColor(R.color.firefox_light_blue));

        TextView pointsTextView = (TextView)pointsAccumulatedView.findViewById(R.id.points_accumulated_points_text_view);
        pointsTextView.setText(MozHelper.localizeNumber(getTotalPoints()));

        statsListView.addView(pointsAccumulatedView, 0);
    }

    private int getTotalPoints() {

        int totalPoints = 0;

        for (int i = 0; i < statsAdapter.getCount(); i++) {
            totalPoints += statsAdapter.getPointsForScoreItemAtIndex(i);
        }

        return totalPoints;
    }
}
