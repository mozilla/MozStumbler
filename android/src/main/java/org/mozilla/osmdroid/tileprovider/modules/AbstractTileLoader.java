/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.osmdroid.tileprovider.modules;

import android.graphics.drawable.Drawable;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.core.logging.Log;
import org.mozilla.osmdroid.tileprovider.ExpirableBitmapDrawable;
import org.mozilla.osmdroid.tileprovider.MapTile;
import org.mozilla.osmdroid.tileprovider.MapTileRequestState;
import org.mozilla.osmdroid.tileprovider.constants.OSMConstants;

import java.util.Iterator;

/**
 * Load the requested tile. An abstract internal class whose objects are used by worker threads
 * to acquire tiles from servers. It processes tiles from the 'pending' set to the 'working' set
 * as they become available. The key unimplemented method is 'loadTile'.
 */
public abstract class AbstractTileLoader implements Runnable {

    private static final String LOG_TAG = AppGlobals.makeLogTag(AbstractTileLoader.class);

    private MapTileModuleProviderBase mapTileModuleProviderBase;

    public AbstractTileLoader(MapTileModuleProviderBase mapTileModuleProviderBase) {
        this.mapTileModuleProviderBase = mapTileModuleProviderBase;
    }

    /**
     * Load the requested tile.
     *
     * @param pState
     * @return the tile if it was loaded successfully, or null if failed to
     * load and other tile providers need to be called
     * @throws org.mozilla.osmdroid.tileprovider.modules.MapTileModuleProviderBase.CantContinueException
     */
    protected abstract Drawable loadTile(MapTileRequestState pState)
            throws MapTileModuleProviderBase.CantContinueException;

    protected void onTileLoaderInit() {
        // Do nothing by default
    }

    protected void onTileLoaderShutdown() {
        // Do nothing by default
    }

    protected MapTileRequestState nextTile() {

        synchronized (mapTileModuleProviderBase.mQueueLockObject) {
            MapTile result = null;

            // get the most recently accessed tile
            // - the last item in the iterator that's not already being
            // processed
            Iterator<MapTile> iterator = mapTileModuleProviderBase.mPending.keySet().iterator();

            // TODO this iterates the whole list, make this faster...
            while (iterator.hasNext()) {
                final MapTile tile = iterator.next();
                if (!mapTileModuleProviderBase.mWorking.containsKey(tile)) {
                    if (OSMConstants.DEBUG_TILE_PROVIDERS) {
                        Log.d(LOG_TAG, "TileLoader.nextTile() on provider: " + mapTileModuleProviderBase.getName()
                                + " found tile in working queue: " + tile);
                    }
                    result = tile;
                }
            }

            if (result != null) {
                if (OSMConstants.DEBUG_TILE_PROVIDERS) {
                    Log.d(LOG_TAG, "TileLoader.nextTile() on provider: " + mapTileModuleProviderBase.getName()
                            + " adding tile to working queue: " + result);
                }
                mapTileModuleProviderBase.mWorking.put(result, mapTileModuleProviderBase.mPending.get(result));
            }

            return (result != null ? mapTileModuleProviderBase.mPending.get(result) : null);
        }
    }

    /**
     * A tile has loaded.
     */
    protected void tileLoaded(final MapTileRequestState pState, final Drawable pDrawable) {
        if (OSMConstants.DEBUG_TILE_PROVIDERS) {
            Log.d(LOG_TAG, "TileLoader.tileLoaded() on provider: " + mapTileModuleProviderBase.getName() + " with tile: "
                    + pState.getMapTile());
        }
        mapTileModuleProviderBase.removeTileFromQueues(pState.getMapTile());
        pState.getCallback().mapTileRequestCompleted(pState, pDrawable);
    }

    /**
     * A tile has loaded but it's expired.
     * Return it <b>and</b> send request to next provider.
     */
    protected void tileLoadedExpired(final MapTileRequestState pState, final Drawable pDrawable) {
        if (OSMConstants.DEBUG_TILE_PROVIDERS) {
            Log.d(LOG_TAG, "TileLoader.tileLoadedExpired() on provider: " + mapTileModuleProviderBase.getName()
                    + " with tile: " + pState.getMapTile());
        }
        mapTileModuleProviderBase.removeTileFromQueues(pState.getMapTile());
        pState.getCallback().mapTileRequestExpiredTile(pState, pDrawable);
    }

    protected void tileLoadedFailed(final MapTileRequestState pState) {
        if (OSMConstants.DEBUG_TILE_PROVIDERS) {
            Log.d(LOG_TAG, "TileLoader.tileLoadedFailed() on provider: " + mapTileModuleProviderBase.getName()
                    + " with tile: " + pState.getMapTile());
        }
        mapTileModuleProviderBase.removeTileFromQueues(pState.getMapTile());
        pState.getCallback().mapTileRequestFailed(pState);
    }

    /**
     * This is a functor class of type Runnable. The run method is the encapsulated function.
     */
    @Override
    final public void run() {

        onTileLoaderInit();

        MapTileRequestState state;
        Drawable result = null;
        while ((state = nextTile()) != null) {
            if (OSMConstants.DEBUG_TILE_PROVIDERS) {
                Log.d(LOG_TAG, "TileLoader.run() processing next tile: " + state.getMapTile());
            }
            try {
                result = null;
                result = loadTile(state);
            } catch (final MapTileModuleProviderBase.CantContinueException e) {
                Log.e(LOG_TAG, "Tile loader can't continue: " + state.getMapTile(), e);
                mapTileModuleProviderBase.clearQueue();
            } catch (final Throwable e) {
                Log.e(LOG_TAG, "Error downloading tile: " + state.getMapTile(), e);
            }

            if (result == null) {
                tileLoadedFailed(state);
            } else if (ExpirableBitmapDrawable.isDrawableExpired(result)) {
                tileLoadedExpired(state, result);
            } else {
                tileLoaded(state, result);
            }
        }

        onTileLoaderShutdown();
    }
}
