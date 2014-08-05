package org.mozilla.mozstumbler.client.fragments.leaderboard;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.adapters.RankAdapter;
import org.mozilla.mozstumbler.client.developers.SampleData;

/**
 * Created by JeremyChiang on 2014-08-05.
 */
public class YourRankFragment extends Fragment {
    private RankAdapter rankAdapter;
    private LinearLayout rankListView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_your_rank, container, false);

        rankListView = (LinearLayout)rootView.findViewById(R.id.your_rank_linear_layout_list_container);

        rankAdapter = new RankAdapter(getActivity(), SampleData.sampleDataForYourRank());

        for (int i = 0; i < rankAdapter.getCount(); i++) {
            View listItemView = rankAdapter.getView(i, null, null);

            if (i == 0) {
                highlightListItem(listItemView);
            }

            rankListView.addView(listItemView);
        }

        return rootView;
    }

    private void highlightListItem(View listItemView) {
        LinearLayout rankListItem = (LinearLayout)listItemView.findViewById(R.id.rank_list_item);
        TextView rankPositionTextView = (TextView)listItemView.findViewById(R.id.rank_position_text_view);
        TextView rankPlayerNameTextView = (TextView)listItemView.findViewById(R.id.rank_player_name_text_view);
        TextView rankPlayerPointsTextView = (TextView)listItemView.findViewById(R.id.rank_player_points_text_view);

        rankListItem.setBackgroundColor(getResources().getColor(R.color.firefox_light_blue));
        rankPositionTextView.setTextColor(getResources().getColor(android.R.color.white));
        rankPlayerNameTextView.setTextColor(getResources().getColor(android.R.color.white));
        rankPlayerPointsTextView.setTextColor(getResources().getColor(android.R.color.white));
    }
}
