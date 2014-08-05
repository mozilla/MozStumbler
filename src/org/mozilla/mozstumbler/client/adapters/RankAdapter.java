package org.mozilla.mozstumbler.client.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.models.Player;
import org.mozilla.mozstumbler.client.utilities.MozHelper;

import java.util.ArrayList;

/**
 * Created by JeremyChiang on 2014-08-05.
 */
public class RankAdapter extends BaseAdapter {
    private LayoutInflater inflater;
    private ArrayList<Player> players;

    public RankAdapter(Context context, ArrayList<Player> players) {
        this.inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        this.players = players;
    }

    @Override
    public int getCount() {
        return players.size();
    }

    @Override
    public Object getItem(int i) {
        return players.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        ViewHolder viewHolder = null;

        if (view == null) {
            view = inflater.inflate(R.layout.list_item_rank, null);

            viewHolder = new ViewHolder();
            viewHolder.rankPositionTextView = (TextView)view.findViewById(R.id.rank_position_text_view);
            viewHolder.rankPlayerNameTextView = (TextView)view.findViewById(R.id.rank_player_name_text_view);
            viewHolder.rankPlayerPointsTextView = (TextView)view.findViewById(R.id.rank_player_points_text_view);

            view.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder)view.getTag();
        }

        Player player = players.get(i);

        viewHolder.rankPositionTextView.setText(Integer.toString(player.getPlayerRank()) + ".");
        viewHolder.rankPlayerNameTextView.setText(player.getPlayerName());
        viewHolder.rankPlayerPointsTextView.setText(MozHelper.localizeNumber(player.getPlayerPoints()));

        return view;
    }

    private static class ViewHolder {
        public TextView rankPositionTextView;
        public TextView rankPlayerNameTextView;
        public TextView rankPlayerPointsTextView;
    }
}