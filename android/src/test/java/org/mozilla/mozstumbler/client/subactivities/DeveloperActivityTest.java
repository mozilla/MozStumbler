package org.mozilla.mozstumbler.client.subactivities;

import android.annotation.TargetApi;
import android.app.Fragment;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.os.Environment;
import android.widget.CheckBox;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
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
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.DataStorageManager;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowEnvironment;
import org.robolectric.shadows.ShadowToast;

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
        // This is really dumb.  robolectric doesn't automatically reset the state
        // of preferences.
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

        // The stumble log checkbox should still be unchecked
        assertFalse(Prefs.getInstance().isSaveStumbleLogs());

        // Check that the toast was displayed
        assertEquals(ShadowToast.getTextOfLatestToast(), devOptions.getString(R.string.no_sdcard_is_mounted));
    }


    public void testDatastorageMovesFiles() throws IOException {

        Context roboContext = (Context)Robolectric.application;
        FileDirTestContext ctx = new FileDirTestContext(roboContext);

        DataStorageManager dsm = DataStorageManager.createGlobalInstance(
                 ctx,
                 null,
                 20000000,
                 52);

        // Create a dummy file in the storage directory
        String mockFilePath = ctx.getFilesDir() + File.separator + "foo.txt";
        File fakeReport = new File(mockFilePath);
        FileWriter fw = new FileWriter(fakeReport);
        fw.write("hello world");
        fw.flush();
        fw.close();
        assertTrue(fakeReport.exists());

        File movedFile = new File(DataStorageManager.sdcard_archive_path() + File.separator + "foo.txt");

        // Make sure we have created the archive directory
        assertTrue(DataStorageManager.archiveDirCreatedAndMounted(ctx));

        assertFalse(movedFile.exists());
        assertTrue(movedFile.exists());
        assertTrue(dsm.delete("foo.txt"));

        assertTrue(movedFile.exists());
        assertFalse(fakeReport.exists());

    }

}
