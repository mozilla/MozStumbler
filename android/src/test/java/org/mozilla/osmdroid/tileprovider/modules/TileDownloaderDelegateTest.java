/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.osmdroid.tileprovider.modules;

import junit.framework.Assert;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mozilla.mozstumbler.client.mapview.tiles.AbstractMapOverlay;
import org.mozilla.mozstumbler.service.core.http.IHttpUtil;
import org.mozilla.mozstumbler.service.core.http.IResponse;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.test.fixtures.FixtureLoader;
import org.mozilla.osmdroid.tileprovider.MapTile;
import org.mozilla.osmdroid.tileprovider.tilesource.BitmapTileSourceBase;
import org.mozilla.osmdroid.tileprovider.tilesource.ITileSource;
import org.mozilla.osmdroid.tileprovider.tilesource.XYTileSource;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
public class TileDownloaderDelegateTest {

    private static final String testUrl = "http://not.a.real.url/";

    @Test
    public void testSimpleHTTP200() throws BitmapTileSourceBase.LowMemoryException, IOException {
        /*
         This test case mocks out enough to get the TileDownloaderDelegate to run to completion
         and return real tile data.
         */

        INetworkAvailablityCheck netAvailabilityCheck = mock(INetworkAvailablityCheck.class);
        TileIOFacade ioFacade = mock(TileIOFacade.class);

        ITileSource mockTileSource =  spy(new XYTileSource("Stumbler-BaseMap-Tiles",
                null, 1, AbstractMapOverlay.MAX_ZOOM_LEVEL_OF_MAP,
                AbstractMapOverlay.TILE_PIXEL_SIZE,
                AbstractMapOverlay.FILE_TYPE_SUFFIX_PNG,
                new String[]{testUrl}));
        doReturn(testUrl).when(mockTileSource).getTileURLString((MapTile) anyObject());

        MapTile tile = mock(MapTile.class);

        TileDownloaderDelegate delegate = spy(new TileDownloaderDelegate(netAvailabilityCheck, ioFacade));

        doReturn(false).when(delegate).networkIsUnavailable();
        doReturn(false).when(delegate).urlIs404Cached(anyString());

        SerializableTile sTile = getSerializableTile();
        assertTrue(sTile.getTileData().length > 0);

        // Clobber the IHttpUtil class so that the response is just the bytes from fixture data
        IHttpUtil mockHttp = mock(IHttpUtil.class);
        IResponse http200 = spy(IResponse.class);

        // set the 200 status code and the content body
        doReturn(200).when(http200).httpStatusCode();
        doReturn(sTile.getTileData()).when(http200).bodyBytes();

        // Always return the mock 200 object we just cooked up
        doReturn(http200).when(mockHttp).get(anyString(),
                (Map<String, String>) anyObject());

        ServiceLocator.getInstance().putService(IHttpUtil.class, mockHttp);

        // TODO: we need a separate testcase to exercise the TileIOFacade to make sure
        // that tileBytes are properly saved to disk.
        doReturn(sTile).when(ioFacade).saveFile(any(ITileSource.class),
                any(MapTile.class),
                any(byte[].class),
                any(String.class));

        // We should have a valid Drawable instance here
        assertNotNull(delegate.downloadTile(sTile, mockTileSource, tile));
    }

    private SerializableTile getSerializableTile() throws IOException {
        // Check that we've actually downloaded the file
        File tmpFile = File.createTempFile("stile", "tmpfile");
        String absPath = tmpFile.getAbsolutePath();
        FileOutputStream stream = new FileOutputStream(tmpFile);
        try {
            byte[] bytes = FixtureLoader.loadResource("org/mozilla/mozstumbler/test/fixtures/2976.png.merged");
            assertTrue(bytes.length > 0);
            stream.write(bytes);
        } finally {
            stream.flush();
            stream.close();
        }

        return new SerializableTile(new File(absPath));
    }

}
