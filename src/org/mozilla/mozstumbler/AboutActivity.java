package org.mozilla.mozstumbler;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.TextView;

public class AboutActivity extends Activity {
    private static final String ABOUT_PAGE_URL = "https://location.services.mozilla.com/";
    private static final String ABOUT_MAPBOX_URL = "https://www.mapbox.com/about/maps/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
    }
	
    @Override
    protected void onStart()
    {
        super.onStart();
        TextView textView = (TextView) findViewById(R.id.about_version);
        String str = getResources().getString(R.string.about_version);
        str = String.format(str, PackageUtils.getAppVersion(this));
        textView.setText(str);
    }
	
    public void onClick_ViewMore(View v) {
        Intent openAboutPage = new Intent(Intent.ACTION_VIEW, Uri.parse(ABOUT_PAGE_URL));
        startActivity(openAboutPage);
    }

    public void onClick_ViewMapboxAttribution(View v) {
        Intent openMapboxPage = new Intent(Intent.ACTION_VIEW, Uri.parse(ABOUT_MAPBOX_URL));
        startActivity(openMapboxPage);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return false;
    }

}
