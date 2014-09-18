package cz.posvic.bootstrap.app;

import android.view.MenuItem;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;
import org.robolectric.tester.android.view.TestMenuItem;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import org.mozilla.mozstumbler.client.MainActivity;

@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
public class MainActivityTest {

	private MainActivity activity;

	@Before
	public void setUp() throws Exception {
		activity = Robolectric.buildActivity(MainActivity.class).create().start().resume().get();
	}

	@Test
	public void activityShouldNotBeNull() {
		assertNotNull(activity);
	}
}
