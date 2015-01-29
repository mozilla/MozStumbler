package org.mozilla.osmdroid.tileprovider.modules;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.os.Environment;

import org.mozilla.mozstumbler.service.core.logging.ClientLog;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;
import org.mozilla.osmdroid.tileprovider.IRegisterReceiver;
import org.mozilla.osmdroid.tileprovider.MapTile;
import org.mozilla.osmdroid.tileprovider.tilesource.BitmapTileSourceBase;
import org.mozilla.osmdroid.tileprovider.tilesource.ITileSource;
import org.mozilla.osmdroid.tileprovider.tilesource.TileSourceFactory;

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

    private static final String LOG_TAG = LoggerUtil.makeLogTag(SmartFSProvider.class);
    private final IRegisterReceiver mRegisterReceiver;
    private final AtomicReference<ITileSource> mTileSource = new AtomicReference<ITileSource>();
    /**
     * whether the sdcard is mounted read/write
     */
    private boolean mSdCardAvailable = true;
    // ===========================================================
    // Fields
    // ===========================================================
    private MyBroadcastReceiver mBroadcastReceiver;
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

    public boolean hasDelegate() {
        return delegate != null;
    }

    public Drawable downloadTile(SerializableTile serializableTile, ITileSource tileSource, MapTile tile) throws BitmapTileSourceBase.LowMemoryException {
        return delegate.downloadTile(serializableTile, tileSource, tile);
    }

    public void configureDelegate(TileDownloaderDelegate d) {
        delegate = d;
    }

    public ITileSource getTileSource() {
        return mTileSource.get();
    }

    // ===========================================================
    // Methods from SuperClass/Interfaces
    // ===========================================================

    @Override
    public void setTileSource(final ITileSource pTileSource) {
        mTileSource.set(pTileSource);
    }

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
        return new SmartFSTileLoader(this);
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

    // ===========================================================
    // Inner and Anonymous Classes
    // ===========================================================

    // Stuff from superclass
    void checkSdCard() {
        final String state = Environment.getExternalStorageState();
        ClientLog.d(LOG_TAG, "sdcard state: " + state);
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

    @Override
    public void detach() {
        if (mBroadcastReceiver != null) {
            mRegisterReceiver.unregisterReceiver(mBroadcastReceiver);
            mBroadcastReceiver = null;
        }
        super.detach();
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
}
