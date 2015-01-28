package org.mozilla.osmdroid.tileprovider.modules;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.core.logging.ClientLog;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;
import org.mozilla.osmdroid.tileprovider.MapTile;
import org.mozilla.osmdroid.tileprovider.constants.OSMConstants;
import org.mozilla.osmdroid.tileprovider.tilesource.ITileSource;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;


/*
 This is also not a cache at all. It's the only place in osmdroid that
 handles disk writes.

 Reads are generally handled by the BitmapTileSourceBase class.

 The class has been extended to do read/write of etag and cache-control.

 */
public class TileIOFacade {

    // ===========================================================
    // Constants
    // ===========================================================

    private static final String LOG_TAG = LoggerUtil.makeLogTag(TileIOFacade.class);

    // ===========================================================
    // Fields
    // ===========================================================

    /**
     * amount of disk space used by tile cache *
     */
    private static long mUsedCacheSpace;

    // ===========================================================
    // Constructors
    // ===========================================================

    public TileIOFacade() {
        shrinkCacheInBackground();
    }

    /**
     * Get the amount of disk space used by the tile cache. This will initially be zero since the
     * used space is calculated in the background.
     *
     * @return size in bytes
     */
    public static long getUsedCacheSpace() {
        return mUsedCacheSpace;
    }

    // ===========================================================
    // Getter & Setter
    // ===========================================================

    private void shrinkCacheInBackground() {
        // @TODO: vng put a static synchronized guard here so that the
        // background shrink can only happen in 1 background thread at
        // a time.
        //
        // We can then invoke the shrink method as much as we want
        // without worrying about spinning up too many background
        // threads.
        final Thread t = new Thread() {
            @Override
            public void run() {
                mUsedCacheSpace = 0; // because it's static
                calculateDirectorySize(OSMConstants.TILE_PATH_BASE);
                if (mUsedCacheSpace > OSMConstants.TILE_MAX_CACHE_SIZE_BYTES) {
                    cutCurrentCache();
                }
            }
        };
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    // ===========================================================
    // Methods from SuperClass/Interfaces
    // ===========================================================

    // @TODO vng: this should really just take in a header defined as
    // Map<String, String> instead of the single etag header
    public SerializableTile saveFile(final ITileSource pTileSource, final MapTile pTile,
                                     final byte[] tileBytes, String etag) {
        File parent;

        File sTileFile = new File(OSMConstants.TILE_PATH_BASE,
                pTileSource.getTileRelativeFilenameString(pTile) + OSMConstants.MERGED_FILE_EXT);
        parent = sTileFile.getParentFile();

        if (!parent.exists() && !createFolderAndCheckIfExists(parent)) {
            ClientLog.w(LOG_TAG, "Can't create parent folder for actual serializable tile. parent [" + parent + "]");
            return null;
        }

        SerializableTile serializableTile = new SerializableTile(tileBytes, etag);
        serializableTile.saveFile(sTileFile);
        mUsedCacheSpace += tileBytes.length;
        if (mUsedCacheSpace > OSMConstants.TILE_MAX_CACHE_SIZE_BYTES) {
            cutCurrentCache(); // TODO perhaps we should do this in the background
        }
        return serializableTile;
    }

    // ===========================================================
    // Methods
    // ===========================================================

    private boolean createFolderAndCheckIfExists(final File pFile) {
        if (pFile.mkdirs()) {
            return true;
        }
        if (AppGlobals.isDebug) {
            ClientLog.d(LOG_TAG, "Failed to create " + pFile + " - wait and check again");
        }

        // if create failed, wait a bit in case another thread created it
        try {
            Thread.sleep(500);
        } catch (final InterruptedException ignore) {
        }
        // and then check again
        if (pFile.exists()) {
            if (AppGlobals.isDebug) {
                ClientLog.d(LOG_TAG, "Seems like another thread created " + pFile);
            }
            return true;
        } else {
            if (AppGlobals.isDebug) {
                ClientLog.d(LOG_TAG, "File still doesn't exist: " + pFile);
            }
            return false;
        }
    }

    private void calculateDirectorySize(final File pDirectory) {
        final File[] z = pDirectory.listFiles();
        if (z != null) {
            for (final File file : z) {
                if (file.isFile()) {
                    mUsedCacheSpace += file.length();
                }
                if (file.isDirectory() && !isSymbolicDirectoryLink(pDirectory, file)) {
                    calculateDirectorySize(file); // *** recurse ***
                }
            }
        }
    }

    /**
     * Checks to see if it appears that a directory is a symbolic link. It does this by comparing
     * the canonical path of the parent directory and the parent directory of the directory's
     * canonical path. If they are equal, then they come from the same true parent. If not, then
     * pDirectory is a symbolic link. If we get an exception, we err on the side of caution and
     * return "true" expecting the calculateDirectorySize to now skip further processing since
     * something went goofy.
     */
    private boolean isSymbolicDirectoryLink(final File pParentDirectory, final File pDirectory) {
        try {
            final String canonicalParentPath1 = pParentDirectory.getCanonicalPath();
            final String canonicalParentPath2 = pDirectory.getCanonicalFile().getParent();
            return !canonicalParentPath1.equals(canonicalParentPath2);
        } catch (final IOException e) {
            return true;
        } catch (final NoSuchElementException e) {
            // See: http://code.google.com/p/android/issues/detail?id=4961
            // See: http://code.google.com/p/android/issues/detail?id=5807
            return true;
        }
    }

    private List<File> getDirectoryFileList(final File aDirectory) {
        final List<File> files = new ArrayList<File>();

        final File[] z = aDirectory.listFiles();
        if (z != null) {
            for (final File file : z) {
                if (file.isFile()) {
                    files.add(file);
                }
                if (file.isDirectory()) {
                    files.addAll(getDirectoryFileList(file));
                }
            }
        }

        return files;
    }

    /**
     * If the cache size is greater than the max then trim it down to the trim level. This method is
     * synchronized so that only one thread can run it at a time.
     */
    private void cutCurrentCache() {

        synchronized (OSMConstants.TILE_PATH_BASE) {

            if (mUsedCacheSpace > OSMConstants.TILE_TRIM_CACHE_SIZE_BYTES) {

                ClientLog.i(LOG_TAG, "Trimming tile cache from " + mUsedCacheSpace + " to "
                        + OSMConstants.TILE_TRIM_CACHE_SIZE_BYTES);

                final List<File> z = getDirectoryFileList(OSMConstants.TILE_PATH_BASE);

                // order list by files day created from old to new
                final File[] files = z.toArray(new File[0]);
                Arrays.sort(files, new Comparator<File>() {
                    @Override
                    public int compare(final File f1, final File f2) {
                        return Long.valueOf(f1.lastModified()).compareTo(f2.lastModified());
                    }
                });

                for (final File file : files) {
                    if (mUsedCacheSpace <= OSMConstants.TILE_TRIM_CACHE_SIZE_BYTES) {
                        break;
                    }

                    final long length = file.length();
                    if (file.delete()) {
                        mUsedCacheSpace -= length;
                    }
                }

                ClientLog.i(LOG_TAG, "Finished trimming tile cache");
            }
        }
    }
}
