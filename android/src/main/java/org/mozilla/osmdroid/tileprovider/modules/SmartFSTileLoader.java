/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.osmdroid.tileprovider.modules;

import android.graphics.drawable.Drawable;

import org.mozilla.mozstumbler.service.core.logging.ClientLog;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;
import org.mozilla.osmdroid.tileprovider.MapTile;
import org.mozilla.osmdroid.tileprovider.MapTileRequestState;
import org.mozilla.osmdroid.tileprovider.constants.OSMConstants;
import org.mozilla.osmdroid.tileprovider.tilesource.BitmapTileSourceBase;
import org.mozilla.osmdroid.tileprovider.tilesource.ITileSource;

import java.io.File;

class SmartFSTileLoader extends AbstractTileLoader {

    private final static String LOG_TAG = LoggerUtil.makeLogTag(SmartFSTileLoader.class);
    private SmartFSProvider smartFSProvider;

    public SmartFSTileLoader(SmartFSProvider mapTileModuleProviderBase) {
        super(mapTileModuleProviderBase);
        smartFSProvider = mapTileModuleProviderBase;
    }


    @Override
    public Drawable loadTile(final MapTileRequestState pState) throws CantContinueException {

        ITileSource tileSource = smartFSProvider.getTileSource();

        if (tileSource == null) {
            return null;
        }

        final MapTile tile = pState.getMapTile();

        // if there's no sdcard then don't do anything
        if (!smartFSProvider.getSdCardAvailable()) {
            if (OSMConstants.DEBUGMODE) {
                ClientLog.d(LOG_TAG, "No sdcard - do nothing for tile: " + tile);
            }
            return null;
        }

        File sTileFile  = new File(OSMConstants.TILE_PATH_BASE,
                tileSource.getTileRelativeFilenameString(tile) + OSMConstants.MERGED_FILE_EXT);

        final Drawable drawable;
        SerializableTile serializableTile = new SerializableTile(sTileFile);

        if (!smartFSProvider.hasDelegate()) {
            // If the delegate is null, we can't talk to the network.
            // Try to just check if the SerializableTile has any data in it
            // and try to use that instead.
            if (serializableTile.getTileData().length > 0) {
                try {
                    drawable = tileSource.getDrawable(serializableTile.getTileData());
                    return drawable;
                } catch (NullPointerException npe) {
                    ClientLog.e(LOG_TAG, "Something horrible happened.", npe);
                    return null;
                } catch (final BitmapTileSourceBase.LowMemoryException e) {
                    // low memory so empty the queue
                    ClientLog.w(LOG_TAG, "LowMemoryException fetching MapTile from disk: " + tile + " : " + e);
                    throw new CantContinueException(e);
                }
            }
            // Failed to load tile from disk when the network is down. Just give up then.
            return null;
        }

        try {
            return smartFSProvider.downloadTile(serializableTile, tileSource, tile);
        } catch (final BitmapTileSourceBase.LowMemoryException e) {
            // low memory so empty the queue
            ClientLog.w(LOG_TAG, "LowMemoryException downloading MapTile: " + tile + " : " + e);
            throw new CantContinueException(e);
        }
    }
}
