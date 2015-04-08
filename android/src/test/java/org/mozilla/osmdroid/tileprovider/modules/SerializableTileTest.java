package org.mozilla.osmdroid.tileprovider.modules;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
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

    @Test
    public void testLoadTileMissingFile() throws IOException {
        File temp = File.createTempFile("temp", ".txt");
        String path = temp.getAbsolutePath();
        temp.delete();

        SerializableTile newTile = new SerializableTile(new File(path));

        // Invalid tile paths should return 0 bytes
        assertEquals(0, newTile.getTileData().length);
    }

    @Test
    public void testLoadTileValidHeaderNoData() throws IOException {
        // This is a file with a valid header, but no actual data
        File temp = File.createTempFile("temp", ".txt");
        FileOutputStream fos = new FileOutputStream(temp);

        byte[] tileData = {(byte) 0xde, (byte) 0xca, (byte) 0xfb, (byte) 0xad};

        fos.write(tileData);
        fos.flush();
        fos.close();

        assertTrue(temp.exists());
        SerializableTile newTile = new SerializableTile(temp);

        // Invalid tiles should return 0 bytes
        assertEquals(0, newTile.getTileData().length);

        // Corrupt tiles should also be automatically wiped off the disk
        assertFalse(temp.exists());
    }


    @Test
    public void testLoadTileInvalidPayload() throws IOException {
        // This is a file with a valid header, and invalid payload
        File temp = File.createTempFile("temp", ".txt");
        FileOutputStream fos = new FileOutputStream(temp);

        byte[] tileData = {(byte) 0xde, (byte) 0xca, (byte) 0xfb, (byte) 0xad,
                // These bytes are garbage
               (byte) 0xaf, (byte) 0xad, (byte) 0xaf, (byte) 0xad,
               (byte) 0xaf, (byte) 0xad, (byte) 0xaf, (byte) 0xad,
               (byte) 0xaf, (byte) 0xad
        };

        fos.write(tileData);
        fos.flush();
        fos.close();

        assertTrue(temp.exists());
        SerializableTile newTile = new SerializableTile(temp);

        // Invalid tiles should return 0 bytes
        assertEquals(0, newTile.getTileData().length);

        // Corrupt tiles should also be automatically wiped off the disk
        assertFalse(temp.exists());
    }

}
