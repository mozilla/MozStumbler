package org.mozilla.mozstumbler.client.fragments.map;

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
public class StumblerOffFragment extends Fragment {

    public interface DismissStumblerOffListener {
        public void dismissStumblerOffFragment();
    }

    private DismissStumblerOffListener dismissStumblerOffListener;

    private Button turnOnStumblerButton;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_stumbler_off, container, false);

        turnOnStumblerButton = (Button)rootView.findViewById(R.id.turn_on_stumbler_button);
        turnOnStumblerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismissStumblerOffListener.dismissStumblerOffFragment();
            }
        });

        return rootView;
    }

    public void setDismissStumblerOffListener(DismissStumblerOffListener dismissStumblerOffListener) {
        this.dismissStumblerOffListener = dismissStumblerOffListener;
    }
}
