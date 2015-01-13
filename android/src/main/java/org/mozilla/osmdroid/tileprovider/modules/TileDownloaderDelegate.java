package org.mozilla.osmdroid.tileprovider.modules;

import org.apache.http.conn.HttpHostConnectException;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.core.http.IHttpUtil;
import org.mozilla.mozstumbler.service.core.http.IResponse;
import org.mozilla.mozstumbler.service.core.logging.Log;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.osmdroid.tileprovider.MapTile;
import org.mozilla.osmdroid.tileprovider.tilesource.ITileSource;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/*
 * This is a self contained tile downloader and writer to disk.
 *
 * Features that this has over the regular MapTileDownloader include:
 * - HTTP 404 filtering so that repeated requests that result in a 404 NotFound
 *   will be cached for one hour.
 * - ETag headers are written to disk in a .etag file so that
 *   condition-get can be implemented using an "If-None-Match" request header
 */
public class TileDownloaderDelegate {

    public static final String ETAG_MATCH_HEADER = "If-None-Match";

    public static final int ONE_HOUR_MS = 1000 * 60 * 60;

    private final INetworkAvailablityCheck networkAvailablityCheck;
    private final TileIOFacade tileIOFacade;

    // We use an LRU cache to track any URLs that give us a HTTP 404.
    private static final int HTTP404_CACHE_SIZE = 2000;
    Map<String, Long> HTTP404_CACHE = Collections.synchronizedMap(new LruCache<String, Long>(HTTP404_CACHE_SIZE));

    private static final String LOG_TAG = AppGlobals.makeLogTag(TileDownloaderDelegate.class.getSimpleName());

    public TileDownloaderDelegate(INetworkAvailablityCheck pNetworkAvailablityCheck,
                                  TileIOFacade tw) {
        tileIOFacade = tw;
        networkAvailablityCheck = pNetworkAvailablityCheck;
    }

    /*
     * Check if the tile is current by running a conditional get on
     * the URL
     */
    public boolean isTileCurrent(SerializableTile serializableTile, ITileSource tileSource, MapTile tile) throws HttpHostConnectException {
        if (tileSource == null) {
            return false;
        }

        if (networkIsUnavailable()) {
            return false;
        }

        final String tileURLString = tileSource.getTileURLString(tile);

        if (tileURLString == null || tileURLString.length() == 0) {
            return false;
        }

        if (urlIs404Cached(tileURLString)) {
            // Note that this behaviour is different than
            // actually downloading a tile.  If the URL is 404 cached,
            // we just assume that the tile is 'current'
            return true;
        }

        if (System.currentTimeMillis() < serializableTile.getCacheControl()) {
            return true;
        }

        // To optimize data usage, the etag is only checked when the map tile is to be downloaded.

        return false;
    }

    /*
     * Write a tile from network to disk.
     */
    public boolean downloadTile(SerializableTile serializableTile, ITileSource tileSource, MapTile tile) {
        if (tileSource == null) {
            Log.i(LOG_TAG, "tileSource is null");
            return false;
        }

        if (networkIsUnavailable()) {
            return false;
        }

        final String tileURLString = tileSource.getTileURLString(tile);

        if (tileURLString == null || tileURLString.length() == 0) {
            return false;
        }

        if (urlIs404Cached(tileURLString)) {
            return false;
        }

        // Always try remove the tileURL from the cache before we try
        // downloading again.
        HTTP404_CACHE.remove(tileURLString);

        IHttpUtil httpClient = (IHttpUtil) ServiceLocator.getInstance().getService(IHttpUtil.class);
        HashMap<String, String> headers = new HashMap<String, String>();
        String cachedEtag = serializableTile.getEtag();
        if (cachedEtag != null) {
            headers.put(ETAG_MATCH_HEADER, cachedEtag);
        }
        IResponse resp = httpClient.get(tileURLString, headers);

        if (resp == null) {
            return false;
        }

        if (resp.httpResponse() == 304) {
            // Resave the file - this will automatically update the cache-control value
            serializableTile.saveFile();
            return true;
        } else if (resp.httpResponse() != 200) {
            if (resp.httpResponse() == 404) {
                HTTP404_CACHE.put(tileURLString, System.currentTimeMillis() + ONE_HOUR_MS);
            } else {
                Log.w(LOG_TAG, "Unexpected response from tile server: [" + resp.httpResponse() + "]");
            }

            // @TODO vng: This is a hack so that we skip over anything  errors from the mozilla
            // cloudfront backed coverage tile server.
            if (tileURLString.contains("cloudfront.net")) {
                // A refactoring that would be useful is a callback mechanism so that we a TileProvider
                // can optionally provide handlers for each HTTP status code to hook logging or other
                // behavior.

                // Do nothing here for now.  We may as well generate an empty bitmap and return that
                // on the refactoring.
            } else {
                Log.w(LOG_TAG, "Error downloading [" + tileURLString + "] HTTP Response Code:" + resp.httpResponse());
            }

            return false;
        }
        byte[] tileBytes = resp.bodyBytes();
        String etag = resp.getFirstHeader("etag");

        // write the data using the TileIOFacade
        tileIOFacade.saveFile(tileSource, tile, tileBytes, etag);
        return true;
    }

    /*
     * If a networkAvailabilityCheck object exists, check if the
     * network is *unavailable* and return true.
     *
     * In all other cases, assume the network is available.
     */
    private boolean networkIsUnavailable() {
        if (networkAvailablityCheck != null && !networkAvailablityCheck.getNetworkAvailable()) {
            return true;
        }
        return false;
    }

    /*
     * Check if this URL is already known to 404 on us.
     */
    private boolean urlIs404Cached(String url) {
        Long cacheTs = HTTP404_CACHE.get(url);
        if (cacheTs != null) {
            if (cacheTs.longValue() > System.currentTimeMillis()) {
                return true;
            }
        }
        return false;
    }

}

