package org.mozilla.mozstumbler.client.tests;

import android.test.mock.MockContext;
import com.google.common.io.Files;

import java.io.File;

/**
 * Created by victorng on 2014-09-15.
 */
public class MockUpdaterContext extends MockContext {

    @Override
    public File getExternalFilesDir(String type) {
        return Files.createTempDir();
    }
}
