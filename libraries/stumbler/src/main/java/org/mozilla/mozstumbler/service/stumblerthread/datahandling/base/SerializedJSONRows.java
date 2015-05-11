package org.mozilla.mozstumbler.service.stumblerthread.datahandling.base;

public class SerializedJSONRows {
    public String filename;
    public final byte[] data;

    public SerializedJSONRows(String filename, byte[] data) {
        this.filename = filename;
        this.data = data;
    }

    public void setFilename(String name) {
        filename = name;
    }
}
