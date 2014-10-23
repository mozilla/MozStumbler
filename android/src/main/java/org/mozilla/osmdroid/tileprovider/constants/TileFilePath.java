package org.mozilla.osmdroid.tileprovider.constants;

import android.os.Environment;

import java.io.File;

public class TileFilePath {

    public static File directoryOverride;

    public static File getStorageDirectory() {
        if (directoryOverride != null) {
            return directoryOverride;
        }
        return new File(Environment.getExternalStorageDirectory(), "osmdroid");
    }
}
