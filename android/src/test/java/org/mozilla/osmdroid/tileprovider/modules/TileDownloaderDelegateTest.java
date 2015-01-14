/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.osmdroid.tileprovider.modules;

import junit.framework.Assert;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mozilla.mozstumbler.client.mapview.tiles.AbstractMapOverlay;
import org.mozilla.osmdroid.tileprovider.MapTile;
import org.mozilla.osmdroid.tileprovider.tilesource.BitmapTileSourceBase;
import org.mozilla.osmdroid.tileprovider.tilesource.ITileSource;
import org.mozilla.osmdroid.tileprovider.tilesource.XYTileSource;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
public class TileDownloaderDelegateTest {

    private static final String testUrl = "https://d17pt8qph6ncyq.cloudfront.net/tiles/13/2286/2976.png";

    @Test
    public void testSimpleDownloadTile() throws BitmapTileSourceBase.LowMemoryException, IOException {
        /*
         This test case mocks out enough to get the
         */

        INetworkAvailablityCheck netAvailabilityCheck = mock(INetworkAvailablityCheck.class);
        TileIOFacade ioFacade = mock(TileIOFacade.class);
        ITileSource mockTileSource =  spy(new XYTileSource("Stumbler-BaseMap-Tiles",
                null, 1, AbstractMapOverlay.MAX_ZOOM_LEVEL_OF_MAP,
                AbstractMapOverlay.TILE_PIXEL_SIZE,
                AbstractMapOverlay.FILE_TYPE_SUFFIX_PNG,
                new String[]{"http://not.a.real.url/"}));

        MapTile tile = mock(MapTile.class);

        TileDownloaderDelegate delegate = spy(new TileDownloaderDelegate(netAvailabilityCheck, ioFacade));

        doReturn(false).when(delegate).networkIsUnavailable();
        doReturn(false).when(delegate).urlIs404Cached(anyString());
        doReturn(testUrl).when(mockTileSource).getTileURLString((MapTile) anyObject());

        
        // Check that we've actually downloaded the file
        File tmpFile = new File("/Users/victorng/dev/MozStumbler/android/src/test/java/org/mozilla/osmdroid/tileprovider/modules/fixtures/tiles/Mozilla Location Service Coverage Map/13/2287/2976.png.merged");
        SerializableTile sTile = new SerializableTile(tmpFile);
        assertTrue(sTile.getTileData().length > 0);

        // TODO: we need a separate testcase to exercise the TileIOFacade to make sure
        // that tileBytes are properly saved to disk.
        doReturn(sTile).when(ioFacade).saveFile(any(ITileSource.class),
                any(MapTile.class),
                any(byte[].class),
                any(String.class));

        // We should have a valid Drawable instance here
        assertNotNull(delegate.downloadTile(sTile, mockTileSource, tile));
    }

}
