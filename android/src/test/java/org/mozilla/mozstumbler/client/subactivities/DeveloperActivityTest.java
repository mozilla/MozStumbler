package org.mozilla.mozstumbler.client.subactivities;

import android.content.Context;
import android.os.Environment;
import android.widget.CheckBox;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.ClientDataStorageManager;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.core.logging.ClientLog;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.DataStorageManager;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.ReportBatch;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.base.SerializedJSONRows;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowEnvironment;
import org.robolectric.shadows.ShadowToast;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mozilla.mozstumbler.client.subactivities.DeveloperActivity.DeveloperOptions;
import static org.robolectric.util.FragmentTestUtil.startFragment;

@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
public class DeveloperActivityTest {

    private static final String LOG_TAG = LoggerUtil.makeLogTag(DeveloperActivityTest.class);

    @Before
    public void setup() {
        // This is really dumb.  robolectric doesn't automatically reset the state
        // of preferences.
        Prefs.getInstanceWithoutContext().setSaveStumbleLogs(false);
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED);
    }

    @Test
    public void testStumbleButtonIsConnectedToPref() {
        DeveloperOptions devOptions = new DeveloperOptions();
        startFragment(devOptions);

        CheckBox button = (CheckBox) devOptions.getView().findViewById(R.id.toggleSaveStumbleLogs);
        assertNotNull(button);

        assertFalse(Prefs.getInstanceWithoutContext().isSaveStumbleLogs());
        button.toggle();
        assertTrue(Prefs.getInstanceWithoutContext().isSaveStumbleLogs());
    }

    @Test
    public void testStumbleButtonUnmountedStorage() {
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_UNMOUNTED);

        DeveloperOptions devOptions = new DeveloperOptions();
        startFragment(devOptions);

        CheckBox button = (CheckBox) devOptions.getView().findViewById(R.id.toggleSaveStumbleLogs);
        assertNotNull(button);

        assertFalse(Prefs.getInstanceWithoutContext().isSaveStumbleLogs());
        button.toggle();

        // The stumble log checkbox should still be unchecked
        assertFalse(Prefs.getInstanceWithoutContext().isSaveStumbleLogs());

        // Check that the toast was displayed
        assertEquals(ShadowToast.getTextOfLatestToast(),
                devOptions.getString(R.string.create_log_archive_failure));
    }


    @Test
    public void testDatastorageMovesFiles() throws IOException {

        Context roboContext = (Context) Robolectric.application;
        FileDirTestContext ctx = new FileDirTestContext(roboContext);

        // Make sure that we have a fresh data storage manager here
        ClientDataStorageManager.removeInstance();
        DataStorageManager dsm = ClientDataStorageManager.createGlobalInstance(
                ctx,
                null,
                20000000,
                52);
        DeveloperOptions devOptions = new DeveloperOptions();
        startFragment(devOptions);

        CheckBox button = (CheckBox) devOptions.getView().findViewById(R.id.toggleSaveStumbleLogs);
        assertNotNull(button);

        assertFalse(Prefs.getInstanceWithoutContext().isSaveStumbleLogs());
        button.toggle();
        assertTrue(Prefs.getInstanceWithoutContext().isSaveStumbleLogs());
        assertTrue(ShadowToast.getTextOfLatestToast().startsWith(ctx.getString(R.string.create_log_archive_success)));

        String filename = "foo.txt";

        // note that this 'reports' directory was created when the ClientDataStorageManager
        // was instantiated with createGlobalInstance()
        String mockFilePath = DataStorageManager.getSystemStorageDir(ctx) +
                File.separator +
                "/reports" +
                File.separator +
                filename;

        File fakeReport = new File(mockFilePath);
        ClientLog.d(LOG_TAG, "Trying to write fake report: [" + mockFilePath + "]");
        FileWriter fw = new FileWriter(fakeReport);
        fw.write("hello world");
        fw.flush();
        fw.close();
        assertTrue(fakeReport.exists());

        File movedFile = new File(ClientDataStorageManager.sdcardArchivePath() + File.separator + "foo.txt");

        assertFalse(movedFile.exists());
        assertTrue(fakeReport.exists());

        // Note that delete will automatically assume that the root path
        // is in the getStorageDir+'/reports' directory
        ReportBatch file = new ReportBatch(null, SerializedJSONRows.StorageState.ON_DISK, 0, 0, 0);
        file.filename = filename;
        assertTrue(dsm.delete(file));

        assertTrue(movedFile.exists());
        assertFalse(fakeReport.exists());
    }
}
