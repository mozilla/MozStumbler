package org.mozilla.osmdroid.tileprovider.modules;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.util.Arrays;
import java.util.HashMap;

import static junit.framework.Assert.fail;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
public class SerializableTileTest {

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void testSerializeTiles() {
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("abc", "abc");
        headers.put("12345", "12345");

        SerializableTile sTile = new SerializableTile();
        sTile.setHeaders(headers);
        byte[] tileData = {(byte) 0xde, (byte) 0xca, (byte) 0xfb, (byte) 0xad};
        sTile.setTileData(tileData);

        SerializableTile newTile = new SerializableTile();

        try {
            newTile.fromBytes(sTile.asBytes());
        } catch (CharacterCodingException e) {
            fail(e.toString());
        }

        assertEquals(2, newTile.getHeaders().size());
        assertEquals("abc", newTile.getHeaders().get("abc"));
        assertEquals("12345", newTile.getHeaders().get("12345"));
        assertTrue(Arrays.equals(tileData, newTile.getTileData()));
    }

    @Test
    public void testSerializeTilesNoData() {
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("abc", "abc");
        headers.put("12345", "12345");

        SerializableTile sTile = new SerializableTile();
        sTile.setHeaders(headers);
        byte[] tileData = {};
        sTile.setTileData(tileData);

        SerializableTile newTile = new SerializableTile();

        try {
            newTile.fromBytes(sTile.asBytes());
        } catch (CharacterCodingException e) {
            fail(e.toString());
        }

        assertEquals(2, newTile.getHeaders().size());
        assertEquals("abc", newTile.getHeaders().get("abc"));
        assertEquals("12345", newTile.getHeaders().get("12345"));
        assertTrue(Arrays.equals(tileData, newTile.getTileData()));
    }

    @Test
    public void testSerializeTileToFile() throws IOException {
        File temp = File.createTempFile("temp", ".txt");

        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("abc", "abc");
        headers.put("12345", "12345");
        SerializableTile sTile = new SerializableTile();
        sTile.setHeaders(headers);
        byte[] tileData = {(byte) 0xde, (byte) 0xca, (byte) 0xfb, (byte) 0xad};
        sTile.setTileData(tileData);

        SerializableTile newTile = new SerializableTile();
        sTile.saveFile(temp, 0);
        newTile.fromFile(temp);

        // There's going to be an etag header
        assertEquals(3, newTile.getHeaders().size());
        assertEquals("abc", newTile.getHeaders().get("abc"));
        assertEquals("12345", newTile.getHeaders().get("12345"));

        assertTrue(Arrays.equals(tileData, newTile.getTileData()));
    }

}
