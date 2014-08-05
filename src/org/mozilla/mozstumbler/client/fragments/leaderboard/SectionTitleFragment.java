package org.mozilla.mozstumbler.client.fragments.leaderboard;

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
public class SectionTitleFragment extends Fragment {

    public static final String KEY_SECTION_TITLE = "KEY_SECTION_TITLE";

    private TextView sectionTitleTextView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_section_title, container, false);

        sectionTitleTextView = (TextView)rootView.findViewById(R.id.section_title_text_view);

        Bundle titleBundle = getArguments();

        if (titleBundle != null) {
            String title = titleBundle.getString(KEY_SECTION_TITLE);
            sectionTitleTextView.setText(title);
        }

        return rootView;
    }
}
