package org.mozilla.mozstumbler.client.fragments.map;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.mozilla.mozstumbler.R;

/**
 * Created by JeremyChiang on 2014-08-05.
 */
public class CoinRewardFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_coin_reward, container, false);
        return rootView;
    }
}
