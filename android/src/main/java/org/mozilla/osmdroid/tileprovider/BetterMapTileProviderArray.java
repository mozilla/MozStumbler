package org.mozilla.osmdroid.tileprovider;

import android.graphics.drawable.Drawable;

import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;
import org.mozilla.osmdroid.tileprovider.modules.MapTileModuleProviderBase;
import org.mozilla.osmdroid.tileprovider.tilesource.ITileSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This top-level tile provider allows a consumer to provide an array of modular asynchronous tile
 * providers to be used to obtain map tiles. When a tile is requested, the
 * {@link BetterMapTileProviderArray} first checks the {@link MapTileCache} (synchronously) and returns
 * the tile if available. If not, then the {@link BetterMapTileProviderArray} returns null and sends the
 * tile request through the asynchronous tile request chain. Each asynchronous tile provider returns
 * success/failure to the {@link BetterMapTileProviderArray}. If successful, the
 * {@link BetterMapTileProviderArray} passes the result to the base class. If failed, then the next
 * asynchronous tile provider is called in the chain. If there are no more asynchronous tile
 * providers in the chain, then the failure result is passed to the base class. The
 * {@link BetterMapTileProviderArray} provides a mechanism so that only one unique tile-request can be in
 * the map tile request chain at a time.
 *
 * @author Marc Kurtz
 */
public class BetterMapTileProviderArray extends MapTileProviderBase {

    private static final String LOG_TAG = LoggerUtil.makeLogTag(BetterMapTileProviderArray.class);
    protected final Map<MapTile, MapTileRequestState> mWorking = Collections.synchronizedMap(new HashMap<MapTile, MapTileRequestState>());
    protected final List<MapTileModuleProviderBase> mTileProviderList;

    /**
     * Creates an {@link BetterMapTileProviderArray} with no tile providers.
     *
     * @param pRegisterReceiver a {@link IRegisterReceiver}
     */
    protected BetterMapTileProviderArray(final ITileSource pTileSource,
                                         final IRegisterReceiver pRegisterReceiver) {
        this(pTileSource, pRegisterReceiver, new MapTileModuleProviderBase[0]);
    }

    /**
     * Creates an {@link BetterMapTileProviderArray} with the specified tile providers.
     *
     * @param aRegisterReceiver  a {@link IRegisterReceiver}
     * @param pTileProviderArray an array of {@link MapTileModuleProviderBase}
     */
    public BetterMapTileProviderArray(final ITileSource pTileSource,
                                      final IRegisterReceiver aRegisterReceiver,
                                      final MapTileModuleProviderBase[] pTileProviderArray) {
        super(pTileSource);

        mTileProviderList = new ArrayList<MapTileModuleProviderBase>();
        Collections.addAll(mTileProviderList, pTileProviderArray);
    }

    @Override
    public synchronized void detach() {
        for (final MapTileModuleProviderBase tileProvider : mTileProviderList) {
            tileProvider.detach();
        }
        mWorking.clear();
    }

    @Override
    public synchronized Drawable getMapTile(final MapTile pTile) {
        final Drawable tile = mTileCache.getMapTile(pTile);
        if (tile != null) {
            return tile;
        }

        // @TODO: vng - rewrite this whole thing, we need to create a
        // MapTileRequest and pass it through the chain of providers.
        // We need to manage a synchronized set of requests which are
        // in a request state.
        //
        // Any call to getMapTile that misses the cache, or is a
        // duplicate request for a tile that is already enqued will
        // yield a null.

        boolean alreadyInProgress = false;
        alreadyInProgress = mWorking.containsKey(pTile);

        if (!alreadyInProgress) {
            final MapTileModuleProviderBase[] providerArray = new MapTileModuleProviderBase[mTileProviderList.size()];

            // Creat a MapTileRequestState that has a pointer to
            // an array of providers
            final MapTileRequestState state = new MapTileRequestState(pTile, mTileProviderList.toArray(providerArray), this);
            mWorking.put(pTile, state);
            //long ts = System.currentTimeMillis();
            //Log.i(LOG_TAG, "This: ["+this+"] " + ts + " mWorking Put: ["+pTile+"]");

            final MapTileModuleProviderBase provider = findNextAppropriateProvider(state);
            if (provider != null) {
                // @TODO: vng loadMapTileAsync spawns a thread to download
                // the tile, I think this is racy as multiple async calls seem
                // to be happening on the same URL
                provider.loadMapTileAsync(state);
            } else {
                mapTileRequestFailed(state);
            }
        } else {
            //long ts = System.currentTimeMillis();
            //Log.i(LOG_TAG, ts +" mWorking is processing: ["+pTile+"]");
        }
        return tile;
    }

    @Override
    public synchronized void mapTileRequestCompleted(final MapTileRequestState aState, final Drawable aDrawable) {
        mWorking.remove(aState.getMapTile());
        //Log.i(LOG_TAG, "This: ["+this+"] mWorking Remove: ["+aState.getMapTile()+"]");
        super.mapTileRequestCompleted(aState, aDrawable);
    }

    @Override
    public synchronized void mapTileRequestFailed(final MapTileRequestState aState) {
        final MapTileModuleProviderBase nextProvider = findNextAppropriateProvider(aState);
        if (nextProvider != null) {
            nextProvider.loadMapTileAsync(aState);
        } else {
            mWorking.remove(aState.getMapTile());
            //Log.i(LOG_TAG, "This: ["+this+"] mWorking Remove: ["+aState.getMapTile()+"]");
            super.mapTileRequestFailed(aState);
        }
    }

    @Override
    public synchronized void mapTileRequestExpiredTile(MapTileRequestState aState, Drawable aDrawable) {
        // Call through to the super first so aState.getCurrentProvider() still contains the proper
        // provider.
        super.mapTileRequestExpiredTile(aState, aDrawable);

        // Continue through the provider chain
        final MapTileModuleProviderBase nextProvider = findNextAppropriateProvider(aState);
        if (nextProvider != null) {
            nextProvider.loadMapTileAsync(aState);
        } else {
            mWorking.remove(aState.getMapTile());
            //Log.i(LOG_TAG, "This: ["+this+"] mWorking Remove: ["+aState.getMapTile()+"]");
        }
    }

    /**
     * We want to not use a provider that doesn't exist anymore in the chain, and we want to not use
     * a provider that requires a data connection when one is not available.
     */
    private MapTileModuleProviderBase findNextAppropriateProvider(final MapTileRequestState aState) {
        MapTileModuleProviderBase provider = null;
        boolean providerDoesntExist = false, providerCantGetDataConnection = false, providerCantServiceZoomlevel = false;
        // The logic of the while statement is
        // "Keep looping until you get null, or a provider that still exists
        // and has a data connection if it needs one and can service the zoom level,"
        do {
            provider = aState.getNextProvider();
            // Perform some checks to see if we can use this provider
            // If any of these are true, then that disqualifies the provider for this tile request.
            if (provider != null) {
                providerDoesntExist = !this.getProviderExists(provider);
                providerCantGetDataConnection = !useDataConnection()
                        && provider.getUsesDataConnection();
                int zoomLevel = aState.getMapTile().getZoomLevel();
                providerCantServiceZoomlevel = zoomLevel > provider.getMaximumZoomLevel()
                        || zoomLevel < provider.getMinimumZoomLevel();
            }
        } while ((provider != null)
                && (providerDoesntExist || providerCantGetDataConnection || providerCantServiceZoomlevel));
        return provider;
    }

    public synchronized boolean getProviderExists(final MapTileModuleProviderBase provider) {
        return mTileProviderList.contains(provider);
    }

    @Override
    public synchronized int getMinimumZoomLevel() {
        int result = MAXIMUM_ZOOMLEVEL;
        for (final MapTileModuleProviderBase tileProvider : mTileProviderList) {
            if (tileProvider.getMinimumZoomLevel() < result) {
                result = tileProvider.getMinimumZoomLevel();
            }
        }
        return result;
    }

    @Override
    public synchronized int getMaximumZoomLevel() {
        int result = MINIMUM_ZOOMLEVEL;
        for (final MapTileModuleProviderBase tileProvider : mTileProviderList) {
            if (tileProvider.getMaximumZoomLevel() > result) {
                result = tileProvider.getMaximumZoomLevel();
            }
        }
        return result;
    }

    @Override
    public synchronized void setTileSource(final ITileSource aTileSource) {
        super.setTileSource(aTileSource);
        for (final MapTileModuleProviderBase tileProvider : mTileProviderList) {
            tileProvider.setTileSource(aTileSource);
            clearTileCache();
        }
    }
}
