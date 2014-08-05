package org.mozilla.mozstumbler.client.developers;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import org.mozilla.mozstumbler.R;

/**
 * Created by JeremyChiang on 2014-08-05.
 */
public class DeveloperOverlayFragment extends Fragment {

    public interface DeveloperActionListener {
        public void simulateCoinReward();
        public void simulateStumblerOff();
    }

    private Button coinRewardButton;
    private Button stumblerOffButton;

    private DeveloperActionListener developerActionListener;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_developer_overlay, container, false);

        coinRewardButton = (Button)rootView.findViewById(R.id.coin_reward_button);
        stumblerOffButton = (Button)rootView.findViewById(R.id.stumbler_off_button);

        coinRewardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                developerActionListener.simulateCoinReward();
            }
        });

        stumblerOffButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                developerActionListener.simulateStumblerOff();
            }
        });

        return rootView;
    }

    public void setDeveloperActionListener(DeveloperActionListener developerActionListener) {
        this.developerActionListener = developerActionListener;
    }
}
