package org.mozilla.osmdroid.tileprovider.modules;

import org.apache.http.util.ByteArrayBuffer;
import org.mozilla.mozstumbler.service.core.logging.ClientLog;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by victorng on 14-10-24.
 * <p/>
 * This class represents a serializable Tile.
 */
public class SerializableTile {

    // Cache tiles locally for 12 hours. Ichanea doesn't update tiles
    // more than once a day anyway and this should be good enough to 
    // enable offline stumbles.
    public static final long CACHE_TILE_MS = 60 * 60 * 12 * 1000;
    final protected static char[] hexArray = "0123456789abcdef".toCharArray();
    private static final String LOG_TAG = LoggerUtil.makeLogTag(SerializableTile.class);
    final byte[] FILE_HEADER = {(byte) 0xde, (byte) 0xca, (byte) 0xfb, (byte) 0xad};
    byte[] tData = new byte[0];
    Map<String, String> headers = new HashMap<String, String>();
    private File myFile;

    public SerializableTile(File sTileFile) {

        if (sTileFile.exists()) {
            boolean tileIsCurrent = false;
            try {
                fromFile(sTileFile);
            } catch (FileNotFoundException e) {
                ClientLog.e(LOG_TAG, "TileFile was deleted by Android during tile load.", e);
                tData = null;
            }
        }
    }

    public SerializableTile(byte[] tileBytes, String etag) {
        setTileData(tileBytes);
        setHeader("etag", etag);
    }

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public byte[] getTileData() {
        return tData;
    }

    public void setTileData(byte[] tileData) {
        if (tileData == null) {
            tileData = new byte[0];
        }
        tData = tileData;
    }

    public void clearHeaders() {
        headers.clear();
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> h) {
        headers.clear();
        if (h == null) {
            return;
        }

        for (Map.Entry<String, String> entry : h.entrySet()) {
            if (entry.getValue() == null || entry.getKey() == null) {
                // skip over this
                continue;
            }

            // Always make headers lowercase
            headers.put(entry.getKey().toLowerCase(), entry.getValue());
        }
    }

    /*
     Write out the tile data as:

     Header:

     4 byte int header to signify this is a special file

        0xde 0xca 0xfb 0xad

     4 byte int declaring the number of headers

         Each header is written as:
         4 byte int declaring length of header name in UTF-8 bytes
         4 byte int declaring length of header value in raw bytes
         X bytes for the header
         Y bytes for the value

     4 bytes declaring content body length
     M bytes for content body

     */
    public byte[] asBytes() throws CharacterCodingException {
        ByteArrayBuffer buff = new ByteArrayBuffer(10);

        buff.append(FILE_HEADER, 0, FILE_HEADER.length);
        buff.append(intAsBytes(headers.size()), 0, 4);

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            byte[] keyBytes = entry.getKey().getBytes();
            byte[] valueBytes = entry.getValue().getBytes();
            buff.append(intAsBytes(keyBytes.length), 0, 4);
            buff.append(intAsBytes(valueBytes.length), 0, 4);
            buff.append(keyBytes, 0, keyBytes.length);
            buff.append(valueBytes, 0, valueBytes.length);
        }

        if (tData == null || tData.length == 0) {
            buff.append(intAsBytes(0), 0, 4);
        } else {
            buff.append(intAsBytes(tData.length), 0, 4);
            buff.append(tData, 0, tData.length);
        }

        return buff.toByteArray();
    }

    public boolean saveFile(File aFile) {
        try {
            myFile = aFile;
            // Always update cache-control on save
            setHeader("cache-control",
                    Long.toString(CACHE_TILE_MS + System.currentTimeMillis()));
            FileOutputStream fos = new FileOutputStream(aFile);
            fos.write(this.asBytes());
            fos.flush();
            fos.close();
            return true;
        } catch (IOException e) {
            ClientLog.w(LOG_TAG, "Error writing SerializableTile to disk");
            return false;
        }
    }

    /*
    This will try to save the file if a file object is already set.

    Generally only used to update a file that was previously loaded from disk.
     */
    public boolean saveFile() {
        if (myFile != null) {
            return saveFile(myFile);
        }
        return false;
    }

    public boolean fromFile(File file) throws FileNotFoundException {
        FileInputStream fis = new FileInputStream(file);
        byte[] arr = new byte[(int) file.length()];

        try {
            fis.read(arr);
        } catch (IOException e) {
            ClientLog.e(LOG_TAG, "Error reading file into array.", e);
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    // wont' be able to do anything here anyway
                }
            }
        }

        try {
            myFile = file.getAbsoluteFile();
            return fromBytes(arr);
        } catch (CharacterCodingException e) {
            ClientLog.e(LOG_TAG, "Error decoding strings from file", e);
            return false;
        }
    }

    protected boolean fromBytes(byte[] arr) throws CharacterCodingException {
        byte[] buffer = null;

        Charset charsetE = Charset.forName("UTF-8");
        CharsetDecoder decoder = charsetE.newDecoder();

        //  create a byte buffer and wrap the array
        ByteBuffer bb = ByteBuffer.wrap(arr);
        //  read your integers using ByteBuffer's getInt().
        //  four bytes converted into an integer!
        buffer = new byte[4];

        bb.get(buffer, 0, 4);
        if (!Arrays.equals(buffer, FILE_HEADER)) {
            ClientLog.w(LOG_TAG, "Unexpected header in tile file: [" + bytesToHex(buffer) + "]");
            return false;
        }

        // read # of headers
        buffer = new byte[4];
        bb.get(buffer, 0, 4);
        int headerCount = java.nio.ByteBuffer.wrap(buffer).getInt();

        headers.clear();

        for (int i = 0; i < headerCount; i++) {
            buffer = new byte[4];
            bb.get(buffer, 0, 4);
            int keyLength = java.nio.ByteBuffer.wrap(buffer).getInt();

            buffer = new byte[4];
            bb.get(buffer, 0, 4);
            int valueLength = java.nio.ByteBuffer.wrap(buffer).getInt();

            String key = null;
            String value = null;

            if (keyLength > 0) {
                buffer = new byte[keyLength];
                bb.get(buffer, 0, keyLength);
                key = new String(buffer);
            }

            if (valueLength > 0) {
                buffer = new byte[valueLength];
                bb.get(buffer, 0, valueLength);
                value = new String(buffer);
            }

            if (key != null && value != null) {
                headers.put(key, value);
            }
        }

        // Remaining bytes should equal the content length of our payload.
        buffer = new byte[4];
        bb.get(buffer, 0, 4);
        int contentLength = java.nio.ByteBuffer.wrap(buffer).getInt();
        if (bb.remaining() != contentLength) {
            ClientLog.w(LOG_TAG, "Remaining byte count does not match actual[" + bb.remaining() + "] vs expected[" + contentLength + "]");
            // Force data to be null on errors.
            tData = null;
            return false;
        }

        tData = new byte[contentLength];
        bb.get(tData, 0, contentLength);

        return true;
    }

    private byte[] intAsBytes(int integer) {
        return ByteBuffer.allocate(4).putInt(integer).array();
    }

    public long getCacheControl() {
        String cc = getHeader("cache-control");
        if (cc == null) {
            return 0;
        } else {
            return Long.parseLong(cc);
        }
    }

    public String getHeader(String k) {
        return headers.get(k.toLowerCase());
    }

    public void setHeader(String k, String v) {
        headers.put(k.toLowerCase(), v);
    }

    public String getEtag() {
        return getHeader("etag");
    }
}
