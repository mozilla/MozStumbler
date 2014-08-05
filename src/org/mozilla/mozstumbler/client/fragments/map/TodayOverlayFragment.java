package org.mozilla.mozstumbler.client.fragments.map;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.mozilla.mozstumbler.R;

/**
 * Created by JeremyChiang on 2014-08-05.
 */
public class TodayOverlayFragment extends Fragment {

    private TextView starScoreTextView;
    private TextView rainbowScoreTextView;
    private TextView coinScoreTextView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_today_overlay, container, true);

        starScoreTextView = (TextView)rootView.findViewById(R.id.star_score_text_view);
        rainbowScoreTextView = (TextView)rootView.findViewById(R.id.rainbow_score_text_view);
        coinScoreTextView = (TextView)rootView.findViewById(R.id.coin_score_text_view);

        return rootView;
    }

    public void setStarScore(int newStarScore) {
        starScoreTextView.setText(Integer.toString(newStarScore));
    }

    public void setRainbowScore(int newRainbowScore) {
        rainbowScoreTextView.setText(Integer.toString(newRainbowScore));
    }

    public void setCoinScore(int newCoinScore) {
        coinScoreTextView.setText(Integer.toString(newCoinScore));
    }
}
