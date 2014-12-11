package org.mozilla.mozstumbler.client.subactivities;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.Environment;

import java.io.File;

/*
 This is a contextwrapper that just overloads the getFilesDir() method for our robolectric
 test cases.
 */
public class FileDirTestContext extends ContextWrapper {

    public FileDirTestContext(Context base) {
        super(base);
    }

    @Override
    public File getFilesDir() {
        return new File(Environment.getExternalStorageDirectory() +
                File.separator +
                "_mock_files_dir");
    }

}
