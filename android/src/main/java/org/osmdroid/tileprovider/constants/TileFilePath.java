package org.osmdroid.tileprovider.constants;
import java.io.File;
import android.os.Environment;

public class TileFilePath {

  public static File directoryOverride;

  public static File getStorageDirectory() {
    if (directoryOverride != null) {
      return directoryOverride;
    }
    return new File(Environment.getExternalStorageDirectory(), "osmdroid");
  }
}
