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
        return new TileLoader();
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

    protected class TileLoader extends MapTileModuleProviderBase.TileLoader {

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
            SerializableTile serializableTile = new SerializableTile();


            final Drawable drawable;
            if (sTileFile.exists()) {

                boolean tileIsCurrent = false;
                try {
                    serializableTile.fromFile(sTileFile);
                } catch (FileNotFoundException e) {
                    Log.e(LOG_TAG, "TileFile was deleted by Android during tile load.", e);
                    throw new RuntimeException(e);
                }

                try {
                    tileIsCurrent = delegate.isTileCurrent(serializableTile, tileSource, tile);
                } catch (IOException ioEx) {
                    Log.e(LOG_TAG, "Error checking etag status", ioEx);
                    return null;
                }

                if (tileIsCurrent) {
                    // Use the on disk tile
                    try {
                        drawable = tileSource.getDrawable(serializableTile.getTileData());
                        return drawable;
                    } catch (final LowMemoryException e) {
                        // low memory so empty the queue
                        Log.w(LOG_TAG, "LowMemoryException downloading MapTile: " + tile + " : " + e);
                        throw new CantContinueException(e);
                    }
                }
            }

            // call the delegate and load a tile from the network
            if (delegate == null) {
                return null;
            }

            boolean writeOK = false;
            writeOK = delegate.downloadTile(tileSource, tile);

            // @TODO: the writeOK flag isn't always going to succeed
            // because of network failures - just ignore it for now
            // and test for file existence. The tile will get updated
            // anyway on the next redraw using conditional get.

            if (sTileFile.exists()) {
                try {
                    serializableTile.fromFile(sTileFile);
                    drawable = tileSource.getDrawable(serializableTile.getTileData());
                    return drawable;
                } catch (final LowMemoryException e) {
                    // low memory so empty the queue
                    Log.w(LOG_TAG, "LowMemoryException downloading MapTile: " + tile + " : " + e);
                    throw new CantContinueException(e);
                } catch (FileNotFoundException e) {
                    Log.e(LOG_TAG, "TileFile was deleted by Android during tile load.", e);
                    throw new RuntimeException(e);                }
            }

            // If we get here then there is no file stored on disk or the network.
            return null;
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
