package org.mozilla.osmdroid.tileprovider.modules;

import org.mozilla.mozstumbler.service.core.logging.ClientLog;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;
import org.mozilla.osmdroid.tileprovider.MapTile;
import org.mozilla.osmdroid.tileprovider.MapTileRequestState;
import org.mozilla.osmdroid.tileprovider.constants.OSMConstants;
import org.mozilla.osmdroid.tileprovider.tilesource.ITileSource;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * An abstract base class for modular tile providers
 *
 * @author Marc Kurtz
 * @author Neil Boyd
 */
public abstract class MapTileModuleProviderBase implements OSMConstants {

    private static final String LOG_TAG = LoggerUtil.makeLogTag(MapTileModuleProviderBase.class);
    protected final Object mQueueLockObject = new Object();
    protected final HashMap<MapTile, MapTileRequestState> mWorking;
    protected final LinkedHashMap<MapTile, MapTileRequestState> mPending;
    private final ExecutorService mExecutor;

    public MapTileModuleProviderBase(int pThreadPoolSize, final int pPendingQueueSize) {
        if (pPendingQueueSize < pThreadPoolSize) {
            ClientLog.w(LOG_TAG, "The pending queue size is smaller than the thread pool size. Automatically reducing the thread pool size.");
            pThreadPoolSize = pPendingQueueSize;
        }
        mExecutor = Executors.newFixedThreadPool(pThreadPoolSize,
                new ConfigurablePriorityThreadFactory(Thread.NORM_PRIORITY, getThreadGroupName()));

        mWorking = new HashMap<MapTile, MapTileRequestState>();
        mPending = new LinkedHashMap<MapTile, MapTileRequestState>(pPendingQueueSize + 2, 0.1f,
                true) {

            private static final long serialVersionUID = 6455337315681858866L;

            @Override
            protected boolean removeEldestEntry(
                    final Map.Entry<MapTile, MapTileRequestState> pEldest) {
                if (size() > pPendingQueueSize) {
                    MapTile result = null;

                    // get the oldest tile that isn't in the mWorking queue
                    Iterator<MapTile> iterator = mPending.keySet().iterator();

                    while (result == null && iterator.hasNext()) {
                        final MapTile tile = iterator.next();
                        if (!mWorking.containsKey(tile)) {
                            result = tile;
                        }
                    }

                    if (result != null) {
                        MapTileRequestState state = mPending.get(result);
                        removeTileFromQueues(result);
                        state.getCallback().mapTileRequestFailed(state);
                    }
                }
                return false;
            }
        };
    }

    /**
     * Gets the human-friendly name assigned to this tile provider.
     *
     * @return the thread name
     */
    protected abstract String getName();

    /**
     * Gets the name assigned to the thread for this provider.
     *
     * @return the thread name
     */
    protected abstract String getThreadGroupName();

    /**
     * It is expected that the implementation will construct an internal member which internally
     * implements a {@link AbstractTileLoader}. This method is expected to return a that internal member to
     * methods of the parent methods.
     *
     * @return the internal member of this tile provider.
     */
    protected abstract Runnable getTileLoader();

    /**
     * Returns true if implementation uses a data connection, false otherwise. This value is used to
     * determine if this provider should be skipped if there is no data connection.
     *
     * @return true if implementation uses a data connection, false otherwise
     */
    public abstract boolean getUsesDataConnection();

    /**
     * Gets the minimum zoom level this tile provider can provide
     *
     * @return the minimum zoom level
     */
    public abstract int getMinimumZoomLevel();

    /**
     * Gets the maximum zoom level this tile provider can provide
     *
     * @return the maximum zoom level
     */
    public abstract int getMaximumZoomLevel();

    /**
     * Sets the tile source for this tile provider.
     *
     * @param tileSource the tile source
     */
    public abstract void setTileSource(ITileSource tileSource);

    public void loadMapTileAsync(final MapTileRequestState pState) {
        synchronized (mQueueLockObject) {
            if (DEBUG_TILE_PROVIDERS) {
                ClientLog.d(LOG_TAG, "MapTileModuleProviderBase.loadMaptileAsync() on provider: "
                        + getName() + " for tile: " + pState.getMapTile());
                if (mPending.containsKey(pState.getMapTile()))
                    ClientLog.d(LOG_TAG, "MapTileModuleProviderBase.loadMaptileAsync() tile already exists in request queue for modular provider. Moving to front of queue.");
                else
                    ClientLog.d(LOG_TAG, "MapTileModuleProviderBase.loadMaptileAsync() adding tile to request queue for modular provider.");
            }

            // this will put the tile in the queue, or move it to the front of
            // the queue if it's already present
            mPending.put(pState.getMapTile(), pState);
        }
        try {
            mExecutor.execute(getTileLoader());
        } catch (final RejectedExecutionException e) {
            ClientLog.e(LOG_TAG, "RejectedExecutionException", e);
        }
    }

    void clearQueue() {
        synchronized (mQueueLockObject) {
            mPending.clear();
            mWorking.clear();
        }
    }

    /**
     * Detach, we're shutting down - Stops all workers.
     */
    public void detach() {
        this.clearQueue();
        this.mExecutor.shutdown();
    }

    void removeTileFromQueues(final MapTile mapTile) {
        synchronized (mQueueLockObject) {
            if (DEBUG_TILE_PROVIDERS) {
                ClientLog.d(LOG_TAG, "MapTileModuleProviderBase.removeTileFromQueues() on provider: "
                        + getName() + " for tile: " + mapTile);
            }
            mPending.remove(mapTile);
            mWorking.remove(mapTile);
        }
    }
}
