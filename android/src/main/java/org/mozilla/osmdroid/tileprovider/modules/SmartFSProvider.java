package org.mozilla.osmdroid.tileprovider.modules;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.os.Environment;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.core.logging.Log;
import org.mozilla.osmdroid.tileprovider.IRegisterReceiver;
import org.mozilla.osmdroid.tileprovider.MapTile;
import org.mozilla.osmdroid.tileprovider.MapTileRequestState;
import org.mozilla.osmdroid.tileprovider.constants.OSMConstants;
import org.mozilla.osmdroid.tileprovider.tilesource.BitmapTileSourceBase.LowMemoryException;
import org.mozilla.osmdroid.tileprovider.tilesource.ITileSource;
import org.mozilla.osmdroid.tileprovider.tilesource.TileSourceFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This is a fork of the  MapTileFilesystemProvider which provides
 * etag support as well as honoring Cache-Control headers.
 * <p/>
 * Note that you will not see concurrency code in here as an
 * ExecutorService is implemented in a superclass
 * MapTileModuleProviderBase.
 *
 * @author Victor Ng
 * @author Marc Kurtz
 * @author Nicolas Gramlich
 */
public class SmartFSProvider extends MapTileModuleProviderBase {

    // ===========================================================
    // Constants
    // ===========================================================

    private static final String LOG_TAG = AppGlobals.makeLogTag(SmartFSProvider.class.getSimpleName());

    /**
     * whether the sdcard is mounted read/write
     */
    private boolean mSdCardAvailable = true;

    private final IRegisterReceiver mRegisterReceiver;
    private MyBroadcastReceiver mBroadcastReceiver;
    // ===========================================================
    // Fields
    // ===========================================================

    private final AtomicReference<ITileSource> mTileSource = new AtomicReference<ITileSource>();
    private TileDownloaderDelegate delegate;

    // ===========================================================
    // Constructors
    // ===========================================================

    public SmartFSProvider(final IRegisterReceiver pRegisterReceiver) {
        this(pRegisterReceiver, TileSourceFactory.DEFAULT_TILE_SOURCE);
    }

    public SmartFSProvider(final IRegisterReceiver pRegisterReceiver,
                           final ITileSource pTileSource) {

        this(pRegisterReceiver,
                pTileSource,
                NUMBER_OF_IO_THREADS,
                TILE_FILESYSTEM_MAXIMUM_QUEUE_SIZE);

    }

    /**
     * Provides a file system based cache tile provider. Other providers can register and store data
     * in the cache.
     *
     * @param pRegisterReceiver
     */
    public SmartFSProvider(final IRegisterReceiver pRegisterReceiver,
                           final ITileSource pTileSource,
                           int pThreadPoolSize,
                           int pPendingQueueSize) {
        super(pThreadPoolSize, pPendingQueueSize);

        checkSdCard();

        mRegisterReceiver = pRegisterReceiver;
        mBroadcastReceiver = new MyBroadcastReceiver();

        final IntentFilter mediaFilter = new IntentFilter();
        mediaFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        mediaFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        mediaFilter.addDataScheme("file");
        pRegisterReceiver.registerReceiver(mBroadcastReceiver, mediaFilter);
        setTileSource(pTileSource);
    }
    // ===========================================================
    // Getter & Setter
    // ===========================================================

    public void configureDelegate(TileDownloaderDelegate d) {
        delegate = d;
    }
    // ===========================================================
    // Methods from SuperClass/Interfaces
    // ===========================================================

    @Override
    public boolean getUsesDataConnection() {
        return true;
    }

    @Override
    protected String getName() {
        return "SmartFSProvider";
    }

    @Override
    protected String getThreadGroupName() {
        return "smartFsProvider";
    }

    @Override
    protected Runnable getTileLoader() {
        // @TODO vng: this is called far up the inheritance chain in super class
        // MapTileModuleProviderBase::loadMapTileAsync(...).  This
        // whole class hierarchy should be squashed down.
        return new SmartFSAbstractTileLoader(this);
    }

    @Override
    public int getMinimumZoomLevel() {
        ITileSource tileSource = mTileSource.get();
        return tileSource != null ? tileSource.getMinimumZoomLevel() : MINIMUM_ZOOMLEVEL;
    }

    @Override
    public int getMaximumZoomLevel() {
        ITileSource tileSource = mTileSource.get();
        return tileSource != null ? tileSource.getMaximumZoomLevel() : MAXIMUM_ZOOMLEVEL;
    }

    @Override
    public void setTileSource(final ITileSource pTileSource) {
        mTileSource.set(pTileSource);
    }

    // ===========================================================
    // Inner and Anonymous Classes
    // ===========================================================

    protected class SmartFSAbstractTileLoader extends AbstractTileLoader {

        public SmartFSAbstractTileLoader(MapTileModuleProviderBase mapTileModuleProviderBase) {
            super(mapTileModuleProviderBase);
        }

        @Override
        public Drawable loadTile(final MapTileRequestState pState) throws CantContinueException {

            ITileSource tileSource = mTileSource.get();
            if (tileSource == null) {
                return null;
            }

            final MapTile tile = pState.getMapTile();

            // if there's no sdcard then don't do anything
            if (!getSdCardAvailable()) {
                if (DEBUGMODE) {
                    Log.d(LOG_TAG, "No sdcard - do nothing for tile: " + tile);
                }
                return null;
            }

            File sTileFile  = new File(OSMConstants.TILE_PATH_BASE,
                    tileSource.getTileRelativeFilenameString(tile) + OSMConstants.MERGED_FILE_EXT);

            final Drawable drawable;
            SerializableTile serializableTile = null;
            serializableTile = new SerializableTile(sTileFile);

            if (delegate == null) {
                // If the delegate is null, we can't talk to the network.
                // Try to just check if the SerializableTile has any data in it
                // and try to use that instead.
                if (serializableTile.getTileData().length > 0) {
                    try {
                        drawable = tileSource.getDrawable(serializableTile.getTileData());
                        return drawable;
                    } catch (NullPointerException npe) {
                        Log.e(LOG_TAG, "Something horrible happened.", npe);
                        return null;
                    } catch (final LowMemoryException e) {
                        // low memory so empty the queue
                        Log.w(LOG_TAG, "LowMemoryException fetching MapTile from disk: " + tile + " : " + e);
                        throw new CantContinueException(e);
                    }
                }
                // Failed to load tile from disk when the network is down. Just give up then.
                return null;
            }

            try {
                return delegate.downloadTile(serializableTile, tileSource, tile);
            } catch (final LowMemoryException e) {
                // low memory so empty the queue
                Log.w(LOG_TAG, "LowMemoryException downloading MapTile: " + tile + " : " + e);
                throw new CantContinueException(e);
            }
        }
    }

    // Stuff from superclass
    void checkSdCard() {
        final String state = Environment.getExternalStorageState();
        Log.d(LOG_TAG, "sdcard state: " + state);
        mSdCardAvailable = Environment.MEDIA_MOUNTED.equals(state);
    }

    protected boolean getSdCardAvailable() {
        return mSdCardAvailable;
    }

    protected void onMediaMounted() {
        // Do nothing by default. Override to handle.
    }

    protected void onMediaUnmounted() {
        // Do nothing by default. Override to handle.
    }

    /**
     * This broadcast receiver will recheck the sd card when the mount/unmount messages happen
     */
    private class MyBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(final Context aContext, final Intent aIntent) {

            final String action = aIntent.getAction();

            checkSdCard();

            if (Intent.ACTION_MEDIA_MOUNTED.equals(action)) {
                onMediaMounted();
            } else if (Intent.ACTION_MEDIA_UNMOUNTED.equals(action)) {
                onMediaUnmounted();
            }
        }
    }

    @Override
    public void detach() {
        if (mBroadcastReceiver != null) {
            mRegisterReceiver.unregisterReceiver(mBroadcastReceiver);
            mBroadcastReceiver = null;
        }
        super.detach();
    }
}
