package org.mozilla.mozstumbler.client.fragments;

import android.app.Fragment;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.PackageUtils;

/**
 * Created by JeremyChiang on 2014-08-09.
 */
public class AboutFragment extends Fragment {

    private static final String ABOUT_PAGE_URL = "https://location.services.mozilla.com/";
    private static final String ABOUT_MAPBOX_URL = "https://www.mapbox.com/about/maps/";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_about, container, false);
        rootView.setBackgroundColor(getResources().getColor(android.R.color.white));

        setupVersionTextView(rootView);
        setupMapBoxURLTextView(rootView);
        setupViewMoreTextView(rootView);

        return rootView;
    }

    private void setupVersionTextView(View rootView) {
        TextView versionTextView = (TextView)rootView.findViewById(R.id.about_version);
        String versionString = getResources().getString(R.string.about_version);
        versionString = String.format(versionString, PackageUtils.getAppVersion(getActivity()));
        versionTextView.setText(versionString);
    }

    private void setupMapBoxURLTextView(View rootView) {
        TextView mapboxURLTextView = (TextView)rootView.findViewById(R.id.mapbox_url_text_view);
        mapboxURLTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent openMapboxURLIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(ABOUT_MAPBOX_URL));
                startActivity(openMapboxURLIntent);
            }
        });
    }

    private void setupViewMoreTextView(View rootView) {
        TextView viewMoreTextView = (TextView)rootView.findViewById(R.id.view_more_text_view);
        viewMoreTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent openAboutURLIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(ABOUT_PAGE_URL));
                startActivity(openAboutURLIntent);
            }
        });
    }
}
