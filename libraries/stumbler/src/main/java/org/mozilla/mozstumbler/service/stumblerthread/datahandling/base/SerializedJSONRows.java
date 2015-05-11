package org.mozilla.mozstumbler.service.stumblerthread.datahandling.base;

public class SerializedJSONRows {
    public String filename = "";
    public final byte[] data;
    public enum StorageState { ON_DISK, IN_MEMORY_ONLY };
    public StorageState storageState;

    public SerializedJSONRows(byte[] data, StorageState storageState) {
        this.data = data;
        this.storageState = storageState;
    }
}
