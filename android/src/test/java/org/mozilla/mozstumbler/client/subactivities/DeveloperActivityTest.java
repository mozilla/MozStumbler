package org.mozilla.mozstumbler.client.subactivities;

import android.annotation.TargetApi;
import android.app.Fragment;
import android.os.Build;
import android.os.Environment;
import android.widget.CheckBox;

import static org.mozilla.mozstumbler.client.subactivities.DeveloperActivity.DeveloperOptions;
import static org.robolectric.util.FragmentTestUtil.startFragment;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.Updater;
import org.mozilla.mozstumbler.client.navdrawer.MainDrawerActivity;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.core.http.IHttpUtil;
import org.mozilla.mozstumbler.service.core.http.MockHttpUtil;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowEnvironment;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.util.FragmentTestUtil.startFragment;

/**
 * Created by victorng on 14-11-14.
 */

@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
public class DeveloperActivityTest {

    @Before
    public void setup() {
        Prefs.getInstance().setSaveStumbleLogs(false);
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED);
    }

    @Test
    public void testStumbleButtonIsConnnectedToPref() {
        DeveloperOptions devOptions = new DeveloperOptions();
        startFragment(devOptions);

        CheckBox button = (CheckBox) devOptions.getView().findViewById(R.id.toggleSaveStumbleLogs);
        assertNotNull(button);

        assertFalse(Prefs.getInstance().isSaveStumbleLogs());
        button.toggle();
        assertTrue(Prefs.getInstance().isSaveStumbleLogs());
    }

    @Test
    public void testStumbleButtonUnmountedStorage() {
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_UNMOUNTED);

        DeveloperOptions devOptions = new DeveloperOptions();
        startFragment(devOptions);

        CheckBox button = (CheckBox) devOptions.getView().findViewById(R.id.toggleSaveStumbleLogs);
        assertNotNull(button);

        assertFalse(Prefs.getInstance().isSaveStumbleLogs());
        button.toggle();
        assertFalse(Prefs.getInstance().isSaveStumbleLogs());
    }


}
