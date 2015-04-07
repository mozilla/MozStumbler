package org.mozilla.osmdroid.tileprovider.modules;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
public class SerializableTileTest {

    @Test
    public void testSerializeTilesNoData() throws IOException {
        File temp = File.createTempFile("temp", ".txt");

        SerializableTile sTile = new SerializableTile(null, "abc");
        sTile.saveFile(temp);

        SerializableTile newTile = new SerializableTile(temp);

        assertEquals(2, newTile.getHeaders().size());
        assertEquals("abc", newTile.getHeaders().get("etag"));
        assertNotNull(newTile.getHeaders().get("cache-control"));
        assertEquals(0, newTile.getTileData().length);
    }

    @Test
    public void testSerializeTileToFile() throws IOException {
        File temp = File.createTempFile("temp", ".txt");

        byte[] tileData = {(byte) 0xde, (byte) 0xca, (byte) 0xfb, (byte) 0xad};

        SerializableTile sTile = new SerializableTile(tileData, "abc");
        sTile.saveFile(temp);
        SerializableTile newTile = new SerializableTile(temp);

        // There's going to be an etag header
        assertEquals(2, newTile.getHeaders().size());
        assertEquals("abc", newTile.getHeaders().get("etag"));
        assertNotNull(newTile.getHeaders().get("cache-control"));


        assertTrue(Arrays.equals(tileData, newTile.getTileData()));
    }
}
