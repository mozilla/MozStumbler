package org.mozilla.mozstumbler.client.fragments.leaderboard;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.adapters.RankAdapter;
import org.mozilla.mozstumbler.client.developers.SampleData;
import org.mozilla.mozstumbler.client.leaderboard.GalaxyRestClient;
import org.mozilla.mozstumbler.client.models.Player;

import java.util.ArrayList;

/**
 * Created by JeremyChiang on 2014-08-05.
 */
public class TopTenFragment extends Fragment {
    private RankAdapter rankAdapter;
    private LinearLayout topTenListView;
    private TextView activeUsersTextView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_top_ten, container, false);

        topTenListView = (LinearLayout)rootView.findViewById(R.id.top_ten_linear_layout_list_container);
        activeUsersTextView = (TextView)rootView.findViewById(R.id.active_users_text_view);

        return rootView;
    }

    public void setSource(JSONArray scores) {
        if (scores != null) {
            ArrayList<Player> players = new ArrayList<Player>();

            for (int i = 0; i < scores.length(); i++) {
                try {
                    JSONObject aScore = (JSONObject)scores.get(i);
                    String playerName = aScore.getString(GalaxyRestClient.KEY_SCORE_USER);
                    int playerScore = aScore.getInt(GalaxyRestClient.KEY_SCORE_SCORE);

                    Player aPlayer = new Player(playerName, playerScore, i + 1);
                    players.add(aPlayer);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            rankAdapter = new RankAdapter(getActivity(), players);
            for (int i = 0; i < rankAdapter.getCount(); i++) {
                topTenListView.addView(rankAdapter.getView(i, null, null));
            }

            activeUsersTextView.setText(rankAdapter.getCount() + " Active Users");
        } else {
            activeUsersTextView.setText("0 Active Users");
        }
    }
}
