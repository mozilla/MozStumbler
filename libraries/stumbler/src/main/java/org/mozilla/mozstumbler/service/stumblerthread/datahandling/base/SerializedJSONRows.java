package org.mozilla.mozstumbler.service.stumblerthread.datahandling.base;

import java.util.HashMap;

public class SerializedJSONRows {
    public String filename = "";
    public final byte[] data;
    public enum StorageState { ON_DISK, IN_MEMORY }
    public StorageState storageState;

    public SerializedJSONRows(byte[] data, StorageState storageState) {
        this.data = data;
        this.storageState = storageState;
    }

    public void tally(HashMap<String, Integer> tallyValues) {
    }
}
