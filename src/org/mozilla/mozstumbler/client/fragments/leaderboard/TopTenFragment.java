package org.mozilla.mozstumbler.client.fragments.leaderboard;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.adapters.RankAdapter;
import org.mozilla.mozstumbler.client.developers.SampleData;

/**
 * Created by JeremyChiang on 2014-08-05.
 */
public class TopTenFragment extends Fragment {
    private RankAdapter rankAdapter;
    private LinearLayout topTenListView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_top_ten, container, false);

        topTenListView = (LinearLayout)rootView.findViewById(R.id.top_ten_linear_layout_list_container);

        rankAdapter = new RankAdapter(getActivity(), SampleData.sampleDataForTopTen());

        for (int i = 0; i < rankAdapter.getCount(); i++) {
            topTenListView.addView(rankAdapter.getView(i, null, null));
        }

        return rootView;
    }
}
