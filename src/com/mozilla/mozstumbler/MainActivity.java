package com.mozilla.mozstumbler;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.Button;

public class MainActivity extends Activity {
	private Scanner mScanner;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		Button scanningBtn = (Button) findViewById(R.id.toggle_scanning);
		scanningBtn.setText(R.string.start_scanning);
	}

	public void onBtnClicked(View v) {
		if (v.getId() == R.id.toggle_scanning) {

			// handle the click here
			Button b = (Button) v;
			int buttonText;

			if (mScanner == null) {
				mScanner = new Scanner(this);
				mScanner.startScanning();
				buttonText = R.string.stop_scanning;
			} else {
				mScanner.stopScanning();
				mScanner = null;
				buttonText = R.string.start_scanning;
			}

			b.setText(buttonText);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
}
