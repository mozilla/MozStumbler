// Created by plusminus on 21:46:22 - 25.09.2008
package org.mozilla.osmdroid.tileprovider;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.core.logging.Log;
import org.mozilla.osmdroid.tileprovider.constants.OpenStreetMapTileProviderConstants;
import org.mozilla.osmdroid.tileprovider.modules.MapTileModuleProviderBase;
import org.mozilla.osmdroid.tileprovider.tilesource.ITileSource;
import org.mozilla.osmdroid.util.TileLooper;
import org.mozilla.osmdroid.views.Projection;

import java.util.HashMap;

/**
 * This is an abstract class. The tile provider is responsible for:
 * <ul>
 * <li>determining if a map tile is available,</li>
 * <li>notifying the client, via a callback handler</li>
 * </ul>
 * see {@link MapTile} for an overview of how tiles are served by this provider.
 *
 * @author Marc Kurtz
 * @author Nicolas Gramlich
 *
 */
public abstract class MapTileProviderBase implements IMapTileProviderCallback,
		OpenStreetMapTileProviderConstants {

	private static final String LOG_TAG = AppGlobals.LOG_PREFIX + MapTileProviderBase.class.getSimpleName();

	protected final MapTileCache mTileCache;
	protected Handler mTileRequestCompleteHandler;
	protected boolean mUseDataConnection = true;

	private ITileSource mTileSource;

	/**
	 * Attempts to get a Drawable that represents a {@link MapTile}. If the tile is not immediately
	 * available this will return null and attempt to get the tile from known tile sources for
	 * subsequent future requests. Note that this may return a {@link ReusableBitmapDrawable} in
	 * which case you should follow proper handling procedures for using that Drawable or it may
	 * reused while you are working with it.
	 *
	 * @see ReusableBitmapDrawable
	 */
	public abstract Drawable getMapTile(MapTile pTile);

	public abstract void detach();

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
	 * @param pTileSource
	 *            the tile source
	 */
	public void setTileSource(final ITileSource pTileSource) {
		mTileSource = pTileSource;
		clearTileCache();
	}

	/**
	 * Gets the tile source for this tile provider.
	 *
	 * @return the tile source
	 */
	public ITileSource getTileSource() {
		return mTileSource;
	}

	/**
	 * Creates a {@link MapTileCache} to be used to cache tiles in memory.
	 */
	public MapTileCache createTileCache() {
		return new MapTileCache();
	}

	public MapTileProviderBase(final ITileSource pTileSource) {
		this(pTileSource, null);
	}

	public MapTileProviderBase(final ITileSource pTileSource,
			final Handler pDownloadFinishedListener) {
		mTileCache = this.createTileCache();
		mTileRequestCompleteHandler = pDownloadFinishedListener;
		mTileSource = pTileSource;
	}

	/**
	 * Called by implementation class methods indicating that they have completed the request as
	 * best it can. The tile is added to the cache, and a MAPTILE_SUCCESS_ID message is sent.
	 *
	 * @param pState
	 *            the map tile request state object
	 * @param pDrawable
	 *            the Drawable of the map tile
	 */
	@Override
	public void mapTileRequestCompleted(final MapTileRequestState pState, final Drawable pDrawable) {
		// put the tile in the cache
		putTileIntoCache(pState, pDrawable);

		// tell our caller we've finished and it should update its view
		if (mTileRequestCompleteHandler != null) {
			mTileRequestCompleteHandler.sendEmptyMessage(MapTile.MAPTILE_SUCCESS_ID);
		}

		if (DEBUG_TILE_PROVIDERS) {
			Log.d(LOG_TAG, "MapTileProviderBase.mapTileRequestCompleted(): " + pState.getMapTile());
		}
	}

	/**
	 * Called by implementation class methods indicating that they have failed to retrieve the
	 * requested map tile. a MAPTILE_FAIL_ID message is sent.
	 *
	 * @param pState
	 *            the map tile request state object
	 */
	@Override
	public void mapTileRequestFailed(final MapTileRequestState pState) {
		if (mTileRequestCompleteHandler != null) {
			mTileRequestCompleteHandler.sendEmptyMessage(MapTile.MAPTILE_FAIL_ID);
		}

		if (DEBUG_TILE_PROVIDERS) {
			Log.d(LOG_TAG, "MapTileProviderBase.mapTileRequestFailed(): " + pState.getMapTile());
		}
	}

	/**
	 * Called by implementation class methods indicating that they have produced an expired result
	 * that can be used but better results may be delivered later. The tile is added to the cache,
	 * and a MAPTILE_SUCCESS_ID message is sent.
	 *
	 * @param pState
	 *            the map tile request state object
	 * @param pDrawable
	 *            the Drawable of the map tile
	 */
	@Override
	public void mapTileRequestExpiredTile(MapTileRequestState pState, Drawable pDrawable) {
		// Put the expired tile into the cache
		putExpiredTileIntoCache(pState, pDrawable);

		// tell our caller we've finished and it should update its view
		if (mTileRequestCompleteHandler != null) {
			mTileRequestCompleteHandler.sendEmptyMessage(MapTile.MAPTILE_SUCCESS_ID);
		}

		if (DEBUG_TILE_PROVIDERS) {
			Log.d(LOG_TAG, "MapTileProviderBase.mapTileRequestExpiredTile(): " + pState.getMapTile());
		}
	}

	protected void putTileIntoCache(MapTileRequestState pState, Drawable pDrawable) {
		final MapTile tile = pState.getMapTile();
		if (pDrawable != null) {
			mTileCache.putTile(tile, pDrawable);
		}
	}

	protected void putExpiredTileIntoCache(MapTileRequestState pState, Drawable pDrawable) {
		final MapTile tile = pState.getMapTile();
		if (pDrawable != null && !mTileCache.containsTile(tile)) {
			mTileCache.putTile(tile, pDrawable);
		}
	}

	public void setTileRequestCompleteHandler(final Handler handler) {
		mTileRequestCompleteHandler = handler;
	}

	public void ensureCapacity(final int pCapacity) {
		mTileCache.ensureCapacity(pCapacity);
	}

	public void clearTileCache() {
		mTileCache.clear();
	}

	/**
	 * Whether to use the network connection if it's available.
	 */
	@Override
	public boolean useDataConnection() {
		return mUseDataConnection;
	}

	/**
	 * Set whether to use the network connection if it's available.
	 *
	 * @param pMode
	 *            if true use the network connection if it's available. if false don't use the
	 *            network connection even if it's available.
	 */
	public void setUseDataConnection(final boolean pMode) {
		mUseDataConnection = pMode;
	}

	/**
	 * Recreate the cache using scaled versions of the tiles currently in it
	 * @param pNewZoomLevel the zoom level that we need now
	 * @param pOldZoomLevel the previous zoom level that we should get the tiles to rescale
	 * @param pViewPort the view port we need tiles for
	 */
	public void rescaleCache(final Projection pProjection, final int pNewZoomLevel,
			final int pOldZoomLevel, final Rect pViewPort) {

		if (pNewZoomLevel == pOldZoomLevel) {
			return;
		}

		final long startMs = System.currentTimeMillis();

		Log.i(LOG_TAG, "rescale tile cache from "+ pOldZoomLevel + " to " + pNewZoomLevel);

		final int tileSize = getTileSource().getTileSizePixels();

		Point topLeftMercator = pProjection.toMercatorPixels(pViewPort.left, pViewPort.top, null);
		Point bottomRightMercator = pProjection.toMercatorPixels(pViewPort.right, pViewPort.bottom,
				null);
		final Rect viewPort = new Rect(topLeftMercator.x, topLeftMercator.y, bottomRightMercator.x,
				bottomRightMercator.y);

		final ScaleTileLooper tileLooper = pNewZoomLevel > pOldZoomLevel
				? new ZoomInTileLooper(pOldZoomLevel)
				: new ZoomOutTileLooper(pOldZoomLevel);
		tileLooper.loop(null, pNewZoomLevel, tileSize, viewPort);

		final long endMs = System.currentTimeMillis();
		Log.i(LOG_TAG, "Finished rescale in " + (endMs - startMs) + "ms");
	}

	private abstract class ScaleTileLooper extends TileLooper {

		/** new (scaled) tiles to add to cache
		  * NB first generate all and then put all in cache,
		  * otherwise the ones we need will be pushed out */
		protected final HashMap<MapTile, Bitmap> mNewTiles;

		protected final int mOldZoomLevel;
		protected int mDiff;
		protected int mTileSize_2;
		protected Rect mSrcRect;
		protected Rect mDestRect;
		protected Paint mDebugPaint;

		public ScaleTileLooper(final int pOldZoomLevel) {
			mOldZoomLevel = pOldZoomLevel;
			mNewTiles = new HashMap<MapTile, Bitmap>();
			mSrcRect = new Rect();
			mDestRect = new Rect();
			mDebugPaint = new Paint();
		}

		@Override
		public void initialiseLoop(final int pZoomLevel, final int pTileSizePx) {
			mDiff = Math.abs(pZoomLevel - mOldZoomLevel);
			mTileSize_2 = pTileSizePx >> mDiff;
		}

		@Override
		public void handleTile(final Canvas pCanvas, final int pTileSizePx, final MapTile pTile, final int pX, final int pY) {
		}

		@Override
		public void finaliseLoop() {
			// now add the new ones, pushing out the old ones
			while (!mNewTiles.isEmpty()) {
				final MapTile tile = mNewTiles.keySet().iterator().next();
				final Bitmap bitmap = mNewTiles.remove(tile);
				final ExpirableBitmapDrawable drawable = new ReusableBitmapDrawable(bitmap);
				drawable.setState(new int[] { ExpirableBitmapDrawable.EXPIRED });
				final Drawable existingTile = mTileCache.getMapTile(tile);
				if (existingTile == null || ExpirableBitmapDrawable.isDrawableExpired(existingTile))
					putExpiredTileIntoCache(new MapTileRequestState(tile,
							new MapTileModuleProviderBase[0], null), drawable);
			}
		}

		protected abstract void handleTile(int pTileSizePx, MapTile pTile, int pX, int pY);
	}

	private class ZoomInTileLooper extends ScaleTileLooper {
		public ZoomInTileLooper(final int pOldZoomLevel) {
			super(pOldZoomLevel);
		}
		@Override
		public void handleTile(final int pTileSizePx, final MapTile pTile, final int pX, final int pY) {

		}
	}

	private class ZoomOutTileLooper extends ScaleTileLooper {
		private static final int MAX_ZOOM_OUT_DIFF = 4;
		public ZoomOutTileLooper(final int pOldZoomLevel) {
			super(pOldZoomLevel);
		}
		@Override
		protected void handleTile(final int pTileSizePx, final MapTile pTile, final int pX, final int pY) {

		}
	}

}
