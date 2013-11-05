package org.mozilla.mozstumbler;

import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.view.Menu;
import android.view.View;
import android.widget.TextView;

public class AboutActivity extends Activity {
	private static final String ABOUT_PAGE_URL = "https://wiki.mozilla.org/Services/Location/About";

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

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		return false;
	}

}
