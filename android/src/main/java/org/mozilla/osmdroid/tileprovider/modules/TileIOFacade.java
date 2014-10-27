package org.mozilla.osmdroid.tileprovider.modules;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.core.logging.Log;
import org.mozilla.osmdroid.tileprovider.MapTile;
import org.mozilla.osmdroid.tileprovider.constants.OSMConstants;
import org.mozilla.osmdroid.tileprovider.tilesource.ITileSource;
import org.mozilla.osmdroid.tileprovider.util.StreamUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
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
public class TileIOFacade  {

    // ===========================================================
    // Constants
    // ===========================================================

    private static final String LOG_TAG = AppGlobals.LOG_PREFIX + TileIOFacade.class.getSimpleName();
    public static final String MERGED = ".merged";

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

    public long readCacheControl(final ITileSource pTileSource, final MapTile pTile) {
        File file;
        File cacheControlFile;
        BufferedInputStream inputStream;

        String tileFilename = pTileSource.getTileRelativeFilenameString(pTile);
        cacheControlFile = new File(OSMConstants.TILE_PATH_BASE,
                tileFilename + ".cache_control");

        FileInputStream fis = null;
        try {
            fis = new FileInputStream(cacheControlFile.getPath());
            inputStream = new BufferedInputStream(fis);
            byte[] contents = new byte[1024];

            int bytesRead = 0;
            String strFileContents = "";
            while ((bytesRead = inputStream.read(contents)) != -1) {
                strFileContents = new String(contents, 0, bytesRead);
            }
            return Long.parseLong(strFileContents);
        } catch (IOException ioEx) {
            Log.e(LOG_TAG, "Failed to read etag file: [" + cacheControlFile.getPath() + "]", ioEx);
        } finally {
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (IOException ioEx) {
                Log.e(LOG_TAG, "osmdroid: error closing etag inputstream", ioEx);
            }
        }
        return 0;
    }

    public String readEtag(final ITileSource pTileSource, final MapTile pTile) {
        File file;
        File etagFile;
        BufferedInputStream inputStream;

        String tileFilename = pTileSource.getTileRelativeFilenameString(pTile);
        etagFile = new File(OSMConstants.TILE_PATH_BASE,
                tileFilename + ".etag");

        FileInputStream fis = null;
        try {
            fis = new FileInputStream(etagFile.getPath());
            inputStream = new BufferedInputStream(fis);
            byte[] contents = new byte[1024];

            int bytesRead = 0;
            String strFileContents = "";
            while ((bytesRead = inputStream.read(contents)) != -1) {
                strFileContents = new String(contents, 0, bytesRead);
            }
            return strFileContents;
        } catch (IOException ioEx) {
            Log.e(LOG_TAG, "Failed to read etag file: [" + etagFile.getPath() + "]", ioEx);
        } finally {
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (IOException ioEx) {
                Log.e(LOG_TAG, "osmdroid: error closing etag inputstream", ioEx);
            }
        }
        return null;
    }

    private void saveCacheControl(final ITileSource pTileSource, final MapTile pTile) {
        String tileFilename = pTileSource.getTileRelativeFilenameString(pTile);
        File cacheControlFile = new File(OSMConstants.OSMDROID_PATH,
                tileFilename + ".cache_control");

        File parent = cacheControlFile.getParentFile();
        BufferedOutputStream outputStream;

        if (!parent.exists() && !createFolderAndCheckIfExists(parent)) {
            return;
        }

        try {
            FileOutputStream fos = new FileOutputStream(cacheControlFile.getPath());
            outputStream = new BufferedOutputStream(fos);

            // Just hardcode 300 seconds into the future for now
            String cache_value = Long.toString(System.currentTimeMillis() + (300 * 1000));
            outputStream.write(cache_value.getBytes(Charset.forName("UTF-8")));
            outputStream.flush();
            outputStream.close();
        } catch (IOException ioEx) {
            Log.e(LOG_TAG, "Failed to create cache control file: [" + cacheControlFile.getPath() + "]", ioEx);
        }
    }

    // @TODO vng: this should really just take in a header defined as
    // Map<String, String> instead of the single etag header
    public boolean saveFile(final ITileSource pTileSource, final MapTile pTile,
                            final InputStream is, String etag) {

        byte[] tileBytes = inputStreamToByteArray(is);

        // Copy the byte array so that we don't clobber each other
        InputStream pStream = new ByteArrayInputStream(tileBytes.clone());

        File file;
        File etagFile;
        BufferedOutputStream outputStream;

        File parent;

        if (etag != null) {
            String tileFilename = pTileSource.getTileRelativeFilenameString(pTile);
            etagFile = new File(OSMConstants.TILE_PATH_BASE,
                    tileFilename + ".etag");

            parent = etagFile.getParentFile();

            if (!parent.exists() && !createFolderAndCheckIfExists(parent)) {
                return false;
            }

            try {
                FileOutputStream fos = new FileOutputStream(etagFile.getPath());
                outputStream = new BufferedOutputStream(fos);
                outputStream.write(etag.getBytes(Charset.forName("UTF-8")));
                outputStream.flush();
                outputStream.close();
            } catch (IOException ioEx) {
                Log.e(LOG_TAG, "Failed to create etag file: [" + etagFile.getPath() + "]", ioEx);
            }
        }

        saveCacheControl(pTileSource, pTile);

        file = new File(OSMConstants.TILE_PATH_BASE,
                pTileSource.getTileRelativeFilenameString(pTile) + OSMConstants.TILE_PATH_EXTENSION);


        parent = file.getParentFile();

        if (!parent.exists() && !createFolderAndCheckIfExists(parent)) {
            Log.w(LOG_TAG, "Can't create parent folder for actual PNG. parent [" + parent + "]");
            return false;
        }

        outputStream = null;
        try {
            File sTileFile  = new File(OSMConstants.TILE_PATH_BASE,
                    pTileSource.getTileRelativeFilenameString(pTile) + MERGED);
            SerializableTile serializableTile = new SerializableTile();
            serializableTile.setTileData(tileBytes);
            serializableTile.setHeader("etag", etag);
            serializableTile.setHeader("cache-control",
                    Long.toString((300 * 1000) + System.currentTimeMillis()));
            serializableTile.saveFile(sTileFile);

            outputStream = new BufferedOutputStream(new FileOutputStream(file.getPath()),
                    StreamUtils.IO_BUFFER_SIZE);
            final long length = StreamUtils.copy(pStream, outputStream);
            outputStream.flush();
            outputStream.close();

            mUsedCacheSpace += length;
            if (mUsedCacheSpace > OSMConstants.TILE_MAX_CACHE_SIZE_BYTES) {
                cutCurrentCache(); // TODO perhaps we should do this in the background
            }
        } catch (final IOException e) {
            Log.e(LOG_TAG, "TileIOFacade: IOException while writing tile: ", e);
            return false;
        } finally {
            if (outputStream != null) {
                StreamUtils.closeStream(outputStream);
            }
        }
        return true;
    }


    private byte[] inputStreamToByteArray(InputStream is)
    {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int nRead;
        byte[] data = new byte[16384];

        try {
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
        } catch (IOException e) {
            Log.w(LOG_TAG, "Error reading from input stream.");
            return null;
        }

        try {
            buffer.flush();
        } catch (IOException e) {
            Log.w(LOG_TAG, "Error writing to ByteArrayBuffer.");
            return null;
        }

        return buffer.toByteArray();
    }


    // ===========================================================
    // Methods
    // ===========================================================

    private boolean createFolderAndCheckIfExists(final File pFile) {
        if (pFile.mkdirs()) {
            return true;
        }
        if (AppGlobals.isDebug) {
            Log.d(LOG_TAG, "Failed to create " + pFile + " - wait and check again");
        }

        // if create failed, wait a bit in case another thread created it
        try {
            Thread.sleep(500);
        } catch (final InterruptedException ignore) {
        }
        // and then check again
        if (pFile.exists()) {
            if (AppGlobals.isDebug) {
                Log.d(LOG_TAG, "Seems like another thread created " + pFile);
            }
            return true;
        } else {
            if (AppGlobals.isDebug) {
                Log.d(LOG_TAG, "File still doesn't exist: " + pFile);
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

                Log.i(LOG_TAG, "Trimming tile cache from " + mUsedCacheSpace + " to "
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

                Log.i(LOG_TAG, "Finished trimming tile cache");
            }
        }
    }

}
