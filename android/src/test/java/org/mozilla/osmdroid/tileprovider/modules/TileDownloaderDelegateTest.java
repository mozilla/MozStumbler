/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.osmdroid.tileprovider.modules;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mozilla.mozstumbler.client.mapview.tiles.AbstractMapOverlay;
import org.mozilla.mozstumbler.service.core.http.IHttpUtil;
import org.mozilla.mozstumbler.service.core.http.IResponse;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;
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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
@SuppressWarnings("unchecked")
public class TileDownloaderDelegateTest {

    private static final String testUrl = "http://not.a.real.url/";
    private final String LOG_TAG = LoggerUtil.makeLogTag(TileDownloaderDelegateTest.class);

    @Test
    public void testSimpleHTTP200() throws BitmapTileSourceBase.LowMemoryException, IOException {
        /*
         This test case mocks out enough to get the TileDownloaderDelegate to run to completion
         and return real tile data.
         */

        INetworkAvailablityCheck netAvailabilityCheck = mock(INetworkAvailablityCheck.class);
        TileIOFacade ioFacade = mock(TileIOFacade.class);

        ITileSource mockTileSource = spy(new XYTileSource("Stumbler-BaseMap-Tiles",
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

        // that tileBytes are properly saved to disk.
        doReturn(sTile).when(ioFacade).saveFile(any(ITileSource.class),
                any(MapTile.class),
                any(byte[].class),
                any(String.class));

        // We should have a valid Drawable instance here
        assertNotNull(delegate.downloadTile(sTile, mockTileSource, tile));
    }

    @Test
    public void testHTTP304Caching() throws BitmapTileSourceBase.LowMemoryException, IOException {
        /*
         This test case verifies that an HTTP 304 will properly re-use tile data from disk.
         */

        INetworkAvailablityCheck netAvailabilityCheck = mock(INetworkAvailablityCheck.class);
        TileIOFacade ioFacade = mock(TileIOFacade.class);

        ITileSource mockTileSource = spy(new XYTileSource("Stumbler-BaseMap-Tiles",
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
        String expectedEtag = sTile.getEtag();
        assertTrue(sTile.getTileData().length > 0);

        // Clobber the IHttpUtil class so that the response is just the bytes from fixture data
        IHttpUtil mockHttp = mock(IHttpUtil.class);
        IResponse http304 = spy(IResponse.class);

        // set the 200 status code and the content body
        doReturn(304).when(http304).httpStatusCode();
        doReturn(new byte[0]).when(http304).bodyBytes();

        // Always return the mock 200 object we just cooked up
        ArgumentCaptor<Map<String, String>> argument = ArgumentCaptor.forClass((Class) HashMap.class);

        doReturn(http304).when(mockHttp).get(anyString(), argument.capture());

        ServiceLocator.getInstance().putService(IHttpUtil.class, mockHttp);

        // We should have a valid Drawable instance here
        assertNotNull(delegate.downloadTile(sTile, mockTileSource, tile));

        // We can only check the capture *after* the delegate's downloadTile method has been called.
        // verify that the etag was passed into the hashmap
        assertTrue(argument.getValue().containsKey("If-None-Match"));
        String actualEtag = argument.getValue().get("If-None-Match");
        assertEquals(actualEtag, expectedEtag);
    }


    @Test
    public void testHTTP304CachingCorruptTile() throws BitmapTileSourceBase.LowMemoryException, IOException {
        /*
         This test case verifies that an HTTP 304 will return NULL and clear the etag on the serializable
         tile in the event that a conditional get succeeds, but the ondisk tile is corrupt.
         */

        INetworkAvailablityCheck netAvailabilityCheck = mock(INetworkAvailablityCheck.class);
        TileIOFacade ioFacade = mock(TileIOFacade.class);

        ITileSource mockTileSource = spy(new XYTileSource("Stumbler-BaseMap-Tiles",
                null, 1, AbstractMapOverlay.MAX_ZOOM_LEVEL_OF_MAP,
                AbstractMapOverlay.TILE_PIXEL_SIZE,
                AbstractMapOverlay.FILE_TYPE_SUFFIX_PNG,
                new String[]{testUrl}));
        doReturn(testUrl).when(mockTileSource).getTileURLString((MapTile) anyObject());

        MapTile tile = mock(MapTile.class);

        TileDownloaderDelegate delegate = spy(new TileDownloaderDelegate(netAvailabilityCheck, ioFacade));

        doReturn(false).when(delegate).networkIsUnavailable();
        doReturn(false).when(delegate).urlIs404Cached(anyString());

        SerializableTile sTile = getEmptySerializableTile("66aa0fd89644ec8814559dcecbd47490");

        String expectedEtag = sTile.getEtag();
        assertEquals(0, sTile.getTileData().length);

        // Clobber the IHttpUtil class so that the response is just the bytes from fixture data
        IHttpUtil mockHttp = mock(IHttpUtil.class);
        IResponse http304 = spy(IResponse.class);

        // set the 200 status code and the content body
        doReturn(304).when(http304).httpStatusCode();

        // Return an empty array to simulate the file has been truncated.
        doReturn(new byte[0]).when(http304).bodyBytes();

        // Always return the mock 200 object we just cooked up
        ArgumentCaptor<Map<String, String>> argument = ArgumentCaptor.forClass((Class) HashMap.class);

        doReturn(http304).when(mockHttp).get(anyString(), argument.capture());

        ServiceLocator.getInstance().putService(IHttpUtil.class, mockHttp);

        // We should *not* have a valid Drawable instance here
        assertNull(delegate.downloadTile(sTile, mockTileSource, tile));

        // We can only check the capture *after* the delegate's downloadTile method has been called.
        // verify that the etag was passed into the hashmap
        assertTrue(argument.getValue().containsKey("If-None-Match"));
        String actualEtag = argument.getValue().get("If-None-Match");
        assertEquals(actualEtag, expectedEtag);

        // The etag on the SerializableTile should have been cleared.
        assertEquals("", sTile.getEtag());
    }

    @Test
    public void testNetworkIsDownDiskHasTile() throws BitmapTileSourceBase.LowMemoryException, IOException {
        /*
         This test case mocks out enough to get the TileDownloaderDelegate to run to completion
         and return real tile data.
         */

        INetworkAvailablityCheck netAvailabilityCheck = mock(INetworkAvailablityCheck.class);
        TileIOFacade ioFacade = mock(TileIOFacade.class);

        ITileSource mockTileSource = spy(new XYTileSource("Stumbler-BaseMap-Tiles",
                null, 1, AbstractMapOverlay.MAX_ZOOM_LEVEL_OF_MAP,
                AbstractMapOverlay.TILE_PIXEL_SIZE,
                AbstractMapOverlay.FILE_TYPE_SUFFIX_PNG,
                new String[]{testUrl}));
        doReturn(testUrl).when(mockTileSource).getTileURLString((MapTile) anyObject());

        MapTile tile = mock(MapTile.class);

        TileDownloaderDelegate delegate = spy(new TileDownloaderDelegate(netAvailabilityCheck, ioFacade));

        doReturn(true).when(delegate).networkIsUnavailable();

        SerializableTile sTile = getSerializableTile();
        assertTrue(sTile.getTileData().length > 0);

        // We should have a valid Drawable instance here
        assertNotNull(delegate.downloadTile(sTile, mockTileSource, tile));
    }

    @Test
    public void testNetworkIsDownNoData() throws BitmapTileSourceBase.LowMemoryException, IOException {
        /*
         This test case mocks out enough to get the TileDownloaderDelegate to run to completion
         and return real tile data.
         */

        INetworkAvailablityCheck netAvailabilityCheck = mock(INetworkAvailablityCheck.class);
        TileIOFacade ioFacade = mock(TileIOFacade.class);

        ITileSource mockTileSource = spy(new XYTileSource("Stumbler-BaseMap-Tiles",
                null, 1, AbstractMapOverlay.MAX_ZOOM_LEVEL_OF_MAP,
                AbstractMapOverlay.TILE_PIXEL_SIZE,
                AbstractMapOverlay.FILE_TYPE_SUFFIX_PNG,
                new String[]{testUrl}));
        doReturn(testUrl).when(mockTileSource).getTileURLString((MapTile) anyObject());

        MapTile tile = mock(MapTile.class);

        TileDownloaderDelegate delegate = spy(new TileDownloaderDelegate(netAvailabilityCheck, ioFacade));

        doReturn(true).when(delegate).networkIsUnavailable();

        SerializableTile sTile = getEmptySerializableTile("blank_etag");
        assertTrue(sTile.getTileData().length == 0);

        // We should have null here
        assertNull(delegate.downloadTile(sTile, mockTileSource, tile));
    }

    @Test
    public void testHttp598SocketError() throws BitmapTileSourceBase.LowMemoryException, IOException {
        INetworkAvailablityCheck netAvailabilityCheck = mock(INetworkAvailablityCheck.class);
        TileIOFacade ioFacade = mock(TileIOFacade.class);

        ITileSource mockTileSource = spy(new XYTileSource("Stumbler-BaseMap-Tiles",
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
        IResponse http598 = spy(IResponse.class);

        // set the 598 status code and the content body
        doReturn(598).when(http598).httpStatusCode();

        // Always return the mock 598 object we just cooked up
        doReturn(http598).when(mockHttp).get(anyString(),
                (Map<String, String>) anyObject());

        ServiceLocator.getInstance().putService(IHttpUtil.class, mockHttp);

        // No data should have come back
        assertNull(delegate.downloadTile(sTile, mockTileSource, tile));

        // Make sure that the Http Client was actually called, there are multiple exit points
        // in the TileDownloaderDelegate
        verify(mockHttp, times(1)).get((String)anyObject(),(Map<String, String>)anyObject());
    }


    private SerializableTile getEmptySerializableTile(String etag) throws IOException {
        // Check that we've actually downloaded the file
        return new SerializableTile(new byte[0], etag);
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
