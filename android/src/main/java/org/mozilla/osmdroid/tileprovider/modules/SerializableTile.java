package org.mozilla.osmdroid.tileprovider.modules;

import org.apache.http.util.ByteArrayBuffer;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.core.logging.Log;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by victorng on 14-10-24.
 *
 * This class represents a serializable Tile.
 */
public class SerializableTile {

    private static final String LOG_TAG =
            AppGlobals.LOG_PREFIX + SerializableTile.class.getSimpleName();

    final byte[] FILE_HEADER = {(byte) 0xde, (byte) 0xca, (byte) 0xfb, (byte) 0xad};

    byte[] tData;
    Map<String, String> headers;

    public SerializableTile() {
        headers = new HashMap<String, String>();
    }


    public void setTileData(byte[] tileData) {
        tData = tileData;
    }

    public byte[] getTileData() {
        return tData;
    }

    public void setHeaders(Map<String, String> h) {
        headers = new HashMap<String, String>();
        for (Map.Entry<String, String> entry : h.entrySet()) {
            if (entry.getValue() == null || entry.getKey() == null) {
                // skip over this
                continue;
            }

            // Always make headers lowercase
            headers.put(entry.getKey().toLowerCase(), entry.getValue());
        }
    }

    public void clearHeaders() {
        headers.clear();
    }

    public Map<String, String> getHeaders() {
        return headers;
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

        Charset charsetE = Charset.forName("UTF-8");
        CharsetEncoder encoder = charsetE.newEncoder();


        ByteArrayBuffer buff = new ByteArrayBuffer(10);
        buff.append(FILE_HEADER, 0, FILE_HEADER.length);

        buff.append(intAsBytes(headers.size()), 0, 4);

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            byte[] keyBytes = encoder.encode(CharBuffer.wrap(entry.getKey())).array();
            byte[] valueBytes = encoder.encode(CharBuffer.wrap(entry.getValue())).array();

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
            FileOutputStream fos = new FileOutputStream(aFile);
            fos.write(this.asBytes());
            fos.flush();
            fos.close();
            return true;
        } catch (IOException e) {
            Log.w(LOG_TAG, "Error writing SerializableTile to disk");
            return false;
        }

    }

    public boolean fromFile(File file) throws FileNotFoundException {
        FileInputStream fis = new FileInputStream(file);
        byte[] arr = new byte[(int) file.length()];

        try {
            fis.read(arr);
        } catch (IOException e) {
            Log.w(LOG_TAG, "Error reading file into array.");
        }

        try {
            return fromBytes(arr);
        } catch (CharacterCodingException e) {
            Log.e(LOG_TAG, "Error decoding strings from file", e);
            return false;
        }
    }

    public boolean fromBytes(byte[] arr) throws CharacterCodingException  {
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
            Log.w(LOG_TAG, "Unexpected header in tile file: ["+bytesToHex(buffer)+"]");
            return false;
        }

        // read # of headers
        buffer = new byte[4];
        bb.get(buffer, 0, 4);
        int headerCount = java.nio.ByteBuffer.wrap(buffer).getInt();

        headers.clear();

        for (int i=0; i < headerCount; i++) {
            buffer = new byte[4];
            bb.get(buffer, 0, 4);
            int keyLength = java.nio.ByteBuffer.wrap(buffer).getInt();

            buffer = new byte[4];
            bb.get(buffer, 0, 4);
            int valueLength = java.nio.ByteBuffer.wrap(buffer).getInt();

            buffer = new byte[keyLength];
            bb.get(buffer, 0, valueLength);
            String key = new String(buffer);

            buffer = new byte[valueLength];
            bb.get(buffer, 0, valueLength);
            String value = new String(buffer);
            headers.put(key, value);
        }

        // Remaining bytes should equal the content length of our payload.
        buffer = new byte[4];
        bb.get(buffer, 0, 4);
        int contentLength = java.nio.ByteBuffer.wrap(buffer).getInt();
        if (bb.remaining() != contentLength) {
            Log.w(LOG_TAG, "Remaining byte count does not match actual["+bb.remaining()+"] vs expected["+contentLength+"]");
            return false;
        }

        tData = new byte[contentLength];
        bb.get(tData, 0, contentLength);

        return true;
    }

    private byte[] intAsBytes(int integer) {
        return ByteBuffer.allocate(4).putInt(integer).array();
    }

    final protected static char[] hexArray = "0123456789abcdef".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public String getHeader(String k){
        return headers.get(k.toLowerCase());
    }

    public void setHeader(String k, String v) {
        headers.put(k.toLowerCase(), v);
    }

}
