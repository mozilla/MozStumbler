package org.mozilla.osmdroid.tileprovider;

import android.content.Context;

import org.mozilla.osmdroid.tileprovider.modules.INetworkAvailablityCheck;
import org.mozilla.osmdroid.tileprovider.modules.MapTileDownloader;
import org.mozilla.osmdroid.tileprovider.modules.MapTileFileArchiveProvider;
import org.mozilla.osmdroid.tileprovider.modules.MapTileFilesystemProvider;
import org.mozilla.osmdroid.tileprovider.modules.NetworkAvailabliltyCheck;
import org.mozilla.osmdroid.tileprovider.modules.TileWriter;
import org.mozilla.osmdroid.tileprovider.tilesource.ITileSource;
import org.mozilla.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.mozilla.osmdroid.tileprovider.util.SimpleRegisterReceiver;

/**
 * This top-level tile provider implements a basic tile request chain which includes a
 * {@link MapTileFilesystemProvider} (a file-system cache), a {@link MapTileFileArchiveProvider}
 * (archive provider), and a {@link MapTileDownloader} (downloads map tiles via tile source).
 *
 * @author Marc Kurtz
 */
public class MapTileProviderBasic extends MapTileProviderArray implements IMapTileProviderCallback {

    // private static final Logger logger = LoggerFactory.getLogger(MapTileProviderBasic.class);

    /**
     * Creates a {@link MapTileProviderBasic}.
     */
    public MapTileProviderBasic(final Context pContext) {
        this(pContext, TileSourceFactory.DEFAULT_TILE_SOURCE);
    }

    /**
     * Creates a {@link MapTileProviderBasic}.
     */
    public MapTileProviderBasic(final Context pContext, final ITileSource pTileSource) {
        this(new SimpleRegisterReceiver(pContext), new NetworkAvailabliltyCheck(pContext),
                pTileSource);
    }

    /**
     * Creates a {@link MapTileProviderBasic}.
     */
    public MapTileProviderBasic(final IRegisterReceiver pRegisterReceiver,
                                final INetworkAvailablityCheck aNetworkAvailablityCheck, final ITileSource pTileSource) {
        super(pTileSource, pRegisterReceiver);

        final TileWriter tileWriter = new TileWriter();

        final MapTileFilesystemProvider fileSystemProvider = new MapTileFilesystemProvider(
                pRegisterReceiver, pTileSource);
        mTileProviderList.add(fileSystemProvider);

        final MapTileFileArchiveProvider archiveProvider = new MapTileFileArchiveProvider(
                pRegisterReceiver, pTileSource);
        mTileProviderList.add(archiveProvider);

        final MapTileDownloader downloaderProvider = new MapTileDownloader(pTileSource, tileWriter,
                aNetworkAvailablityCheck);
        mTileProviderList.add(downloaderProvider);
    }
}
