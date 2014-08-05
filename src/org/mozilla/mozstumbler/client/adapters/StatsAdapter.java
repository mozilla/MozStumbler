package org.mozilla.mozstumbler.client.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.models.Score;
import org.mozilla.mozstumbler.client.utilities.MozHelper;

import java.util.ArrayList;

/**
 * Created by JeremyChiang on 2014-08-05.
 */
public class StatsAdapter extends BaseAdapter {
    private LayoutInflater inflater;
    private ArrayList<Score> scores;

    public StatsAdapter(Context context, ArrayList<Score> scores) {
        this.inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        this.scores = scores;
    }

    @Override
    public int getCount() {
        return scores.size();
    }

    @Override
    public Object getItem(int i) {
        return scores.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        ViewHolder viewHolder = null;

        if (view == null) {
            view = inflater.inflate(R.layout.list_item_score, null);

            viewHolder = new ViewHolder();
            viewHolder.scoreIconImageView = (ImageView)view.findViewById(R.id.score_icon_image_view);
            viewHolder.scoreTitleTextView = (TextView)view.findViewById(R.id.score_title_text_view);
            viewHolder.scorePointsTextView = (TextView)view.findViewById(R.id.score_points_text_view);

            view.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder)view.getTag();
        }

        Score score = scores.get(i);

        switch (score.getScoreType()) {
            case STAR:
                viewHolder.scoreIconImageView.setImageResource(R.drawable.star_score_board);
                break;
            case RAINBOW:
                viewHolder.scoreIconImageView.setImageResource(R.drawable.rainbow_score_board);
                break;
            case COIN:
                viewHolder.scoreIconImageView.setImageResource(R.drawable.gold_coin_score_board);
                break;
            default:
                break;
        }

        viewHolder.scoreTitleTextView.setText(score.getScoreTitle());
        viewHolder.scorePointsTextView.setText(MozHelper.localizeNumber(score.getScorePoints()));

        return view;
    }

    private static class ViewHolder {
        public ImageView scoreIconImageView;
        public TextView scoreTitleTextView;
        public TextView scorePointsTextView;
    }

    public int getPointsForScoreItemAtIndex(int index) {
        return scores.get(index).getScorePoints();
    }
}
