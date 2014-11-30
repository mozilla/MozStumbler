package org.mozilla.osmdroid.views.overlay;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.core.logging.Log;
import org.mozilla.osmdroid.ResourceProxy;
import org.mozilla.osmdroid.tileprovider.MapTile;
import org.mozilla.osmdroid.tileprovider.MapTileProviderBase;
import org.mozilla.osmdroid.tileprovider.ReusableBitmapDrawable;
import org.mozilla.osmdroid.tileprovider.tilesource.ITileSource;
import org.mozilla.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.mozilla.osmdroid.util.TileLooper;
import org.mozilla.osmdroid.util.TileSystem;
import org.mozilla.osmdroid.views.MapView;
import org.mozilla.osmdroid.views.Projection;

/**
 * These objects are the principle consumer of map tiles.
 * <p/>
 * see {@link MapTile} for an overview of how tiles are acquired by this overlay.
 */

public class TilesOverlay extends Overlay implements IOverlayMenuProvider {

    private static final String LOG_TAG = AppGlobals.makeLogTag(TilesOverlay.class.getSimpleName());

    public static final int MENU_MAP_MODE = getSafeMenuId();
    public static final int MENU_TILE_SOURCE_STARTING_ID = getSafeMenuIdSequence(TileSourceFactory
            .getTileSources().size());
    public static final int MENU_OFFLINE = getSafeMenuId();

    /**
     * Current tile source
     */
    protected final MapTileProviderBase mTileProvider;

    /* to avoid allocations during draw */
    protected final Paint mDebugPaint = new Paint();
    private final Rect mTileRect = new Rect();
    private final Point mTilePoint = new Point();
    private final Rect mViewPort = new Rect();
    private Point mTopLeftMercator = new Point();
    private Point mBottomRightMercator = new Point();
    private Point mTilePointMercator = new Point();

    private Projection mProjection;

    private boolean mOptionsMenuEnabled = true;

    /**
     * A drawable loading tile *
     */
    private BitmapDrawable mLoadingTile = null;
    private int mLoadingBackgroundColor = Color.rgb(216, 208, 208);
    private int mLoadingLineColor = Color.rgb(200, 192, 192);

    /**
     * For overshooting the tile cache *
     */
    // @TODO vng: I bumped this number up while debugging the scaling
    // issue.  Caching in osmdroid is treated as reliable storage with
    // respect to loading of tiles.  The problem is that the caches
    // are specified as LRUCaches backed by a LinkedHashMap, so there
    // is no formal policy for when a tile is evicted from the LRU
    // cache.  The caches really need to just go away and we should
    // load directly from storage.
    public static final int OVERSHOOT_TILE_CACHE_SIZE = 0;

    public TilesOverlay(final MapTileProviderBase aTileProvider, final ResourceProxy pResourceProxy) {
        super(pResourceProxy);
        if (aTileProvider == null) {
            throw new IllegalArgumentException(
                    "You must pass a valid tile provider to the tiles overlay.");
        }
        this.mTileProvider = aTileProvider;
    }

    @Override
    public void onDetach(final MapView pMapView) {
        this.mTileProvider.detach();
    }

    public int getMinimumZoomLevel() {
        return mTileProvider.getMinimumZoomLevel();
    }

    public int getMaximumZoomLevel() {
        return mTileProvider.getMaximumZoomLevel();
    }

    /**
     * Whether to use the network connection if it's available.
     */
    public boolean useDataConnection() {
        return mTileProvider.useDataConnection();
    }

    /**
     * Set whether to use the network connection if it's available.
     *
     * @param aMode if true use the network connection if it's available. if false don't use the
     *              network connection even if it's available.
     */
    public void setUseDataConnection(final boolean aMode) {
        mTileProvider.setUseDataConnection(aMode);
    }

    @Override
    protected void draw(Canvas c, MapView osmv, boolean shadow) {

        if (DEBUGMODE) {
            Log.d(LOG_TAG, "onDraw(" + shadow + ")");
        }

        if (shadow) {
            return;
        }

        Projection projection = osmv.getProjection();

        // Get the area we are drawing to
        Rect screenRect = projection.getScreenRect();
        projection.toMercatorPixels(screenRect.left, screenRect.top, mTopLeftMercator);
        projection.toMercatorPixels(screenRect.right, screenRect.bottom, mBottomRightMercator);
        mViewPort.set(mTopLeftMercator.x, mTopLeftMercator.y, mBottomRightMercator.x,
                mBottomRightMercator.y);

        // Draw the tiles!
        drawTiles(c, projection, projection.getZoomLevel(), TileSystem.getTileSize(), mViewPort);
    }

    /**
     * This is meant to be a "pure" tile drawing function that doesn't take into account
     * osmdroid-specific characteristics (like osmdroid's canvas's having 0,0 as the center rather
     * than the upper-left corner). Once the tile is ready to be drawn, it is passed to
     * onTileReadyToDraw where custom manipulations can be made before drawing the tile.
     */
    public void drawTiles(final Canvas c, final Projection projection, final int zoomLevel,
                          final int tileSizePx, final Rect viewPort) {

        mProjection = projection;
        mTileLooper.loop(c, zoomLevel, tileSizePx, viewPort);

        // draw a cross at center in debug mode
        if (DEBUGMODE) {
            // final GeoPoint center = osmv.getMapCenter();
            final Point centerPoint = new Point(viewPort.centerX(), viewPort.centerY());
            c.drawLine(centerPoint.x, centerPoint.y - 9, centerPoint.x, centerPoint.y + 9, mDebugPaint);
            c.drawLine(centerPoint.x - 9, centerPoint.y, centerPoint.x + 9, centerPoint.y, mDebugPaint);
        }

    }

    private final TileLooper mTileLooper = new TileLooper() {
        @Override
        public void initialiseLoop(final int pZoomLevel, final int pTileSizePx) {
            // make sure the cache is big enough for all the tiles
            final int numNeeded = (mLowerRight.y - mUpperLeft.y + 1) * (mLowerRight.x - mUpperLeft.x + 1);
            mTileProvider.ensureCapacity(numNeeded + OVERSHOOT_TILE_CACHE_SIZE);
        }

        @Override
        public void handleTile(final Canvas pCanvas, final int pTileSizePx, final MapTile pTile, final int pX, final int pY) {
            Drawable currentMapTile = mTileProvider.getMapTile(pTile);
            boolean isReusable = currentMapTile instanceof ReusableBitmapDrawable;
            final ReusableBitmapDrawable reusableBitmapDrawable =
                    isReusable ? (ReusableBitmapDrawable) currentMapTile : null;
            if (currentMapTile == null) {
                currentMapTile = getLoadingTile();
            }

            if (currentMapTile != null) {
                mTilePoint.set(pX * pTileSizePx, pY * pTileSizePx);
                mTileRect.set(mTilePoint.x, mTilePoint.y, mTilePoint.x + pTileSizePx, mTilePoint.y
                        + pTileSizePx);
                if (isReusable) {
                    reusableBitmapDrawable.beginUsingDrawable();
                }
                try {
                    if (isReusable && !((ReusableBitmapDrawable) currentMapTile).isBitmapValid()) {
                        currentMapTile = getLoadingTile();
                        isReusable = false;
                    }
                    onTileReadyToDraw(pCanvas, currentMapTile, mTileRect);
                } finally {
                    if (isReusable) {
                        reusableBitmapDrawable.finishUsingDrawable();
                    }
                }
            }

            if (DEBUGMODE) {
                mTileRect.set(pX * pTileSizePx, pY * pTileSizePx, pX * pTileSizePx + pTileSizePx, pY
                        * pTileSizePx + pTileSizePx);
                pCanvas.drawText(pTile.toString(), mTileRect.left + 1,
                        mTileRect.top + mDebugPaint.getTextSize(), mDebugPaint);
                pCanvas.drawLine(mTileRect.left, mTileRect.top, mTileRect.right, mTileRect.top,
                        mDebugPaint);
                pCanvas.drawLine(mTileRect.left, mTileRect.top, mTileRect.left, mTileRect.bottom,
                        mDebugPaint);
            }
        }

        @Override
        public void finaliseLoop() {
        }
    };

    protected void onTileReadyToDraw(final Canvas c, final Drawable currentMapTile,
                                     final Rect tileRect) {
        mProjection.toPixelsFromMercator(tileRect.left, tileRect.top, mTilePointMercator);
        tileRect.offsetTo(mTilePointMercator.x, mTilePointMercator.y);
        currentMapTile.setBounds(tileRect);
        currentMapTile.draw(c);
    }

    @Override
    public void setOptionsMenuEnabled(final boolean pOptionsMenuEnabled) {
        this.mOptionsMenuEnabled = pOptionsMenuEnabled;
    }

    @Override
    public boolean isOptionsMenuEnabled() {
        return this.mOptionsMenuEnabled;
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu pMenu, final int pMenuIdOffset,
                                       final MapView pMapView) {
        final SubMenu mapMenu = pMenu.addSubMenu(0, MENU_MAP_MODE + pMenuIdOffset, Menu.NONE,
                mResourceProxy.getString(ResourceProxy.string.map_mode)).setIcon(
                mResourceProxy.getDrawable(ResourceProxy.bitmap.ic_menu_mapmode));

        for (int a = 0; a < TileSourceFactory.getTileSources().size(); a++) {
            final ITileSource tileSource = TileSourceFactory.getTileSources().get(a);
            mapMenu.add(MENU_MAP_MODE + pMenuIdOffset, MENU_TILE_SOURCE_STARTING_ID + a
                    + pMenuIdOffset, Menu.NONE, tileSource.localizedName(mResourceProxy));
        }
        mapMenu.setGroupCheckable(MENU_MAP_MODE + pMenuIdOffset, true, true);

        final String title = pMapView.getResourceProxy().getString(
                pMapView.useDataConnection() ? ResourceProxy.string.offline_mode
                        : ResourceProxy.string.online_mode);
        final Drawable icon = pMapView.getResourceProxy().getDrawable(
                ResourceProxy.bitmap.ic_menu_offline);
        pMenu.add(0, MENU_OFFLINE + pMenuIdOffset, Menu.NONE, title).setIcon(icon);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu pMenu, final int pMenuIdOffset,
                                        final MapView pMapView) {
        final int index = TileSourceFactory.getTileSources().indexOf(
                pMapView.getTileProvider().getTileSource());
        if (index >= 0) {
            pMenu.findItem(MENU_TILE_SOURCE_STARTING_ID + index + pMenuIdOffset).setChecked(true);
        }

        pMenu.findItem(MENU_OFFLINE + pMenuIdOffset).setTitle(
                pMapView.getResourceProxy().getString(
                        pMapView.useDataConnection() ? ResourceProxy.string.offline_mode
                                : ResourceProxy.string.online_mode));

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem pItem, final int pMenuIdOffset,
                                         final MapView pMapView) {

        final int menuId = pItem.getItemId() - pMenuIdOffset;
        if ((menuId >= MENU_TILE_SOURCE_STARTING_ID)
                && (menuId < MENU_TILE_SOURCE_STARTING_ID
                + TileSourceFactory.getTileSources().size())) {
            pMapView.setTileSource(TileSourceFactory.getTileSources().get(
                    menuId - MENU_TILE_SOURCE_STARTING_ID));
            return true;
        } else if (menuId == MENU_OFFLINE) {
            final boolean useDataConnection = !pMapView.useDataConnection();
            pMapView.setUseDataConnection(useDataConnection);
            return true;
        } else {
            return false;
        }
    }

    public int getLoadingBackgroundColor() {
        return mLoadingBackgroundColor;
    }

    /**
     * Set the color to use to draw the background while we're waiting for the tile to load.
     *
     * @param pLoadingBackgroundColor the color to use. If the value is {@link Color#TRANSPARENT} then there will be no
     *                                loading tile.
     */
    public void setLoadingBackgroundColor(final int pLoadingBackgroundColor) {
        if (mLoadingBackgroundColor != pLoadingBackgroundColor) {
            mLoadingBackgroundColor = pLoadingBackgroundColor;
            clearLoadingTile();
        }
    }

    public int getLoadingLineColor() {
        return mLoadingLineColor;
    }

    public void setLoadingLineColor(final int pLoadingLineColor) {
        if (mLoadingLineColor != pLoadingLineColor) {
            mLoadingLineColor = pLoadingLineColor;
            clearLoadingTile();
        }
    }

    // @TODO vng - this can be refactored and pushed down into the
    // TileProvider.  All the details about the tile size are already
    // in the tileprovider anyway.
    private Drawable getLoadingTile() {
        if (mLoadingTile == null && mLoadingBackgroundColor != Color.TRANSPARENT) {
            try {
                final int tileSize = mTileProvider.getTileSource() != null ? mTileProvider
                        .getTileSource().getTileSizePixels() : 256;
                final Bitmap bitmap = Bitmap.createBitmap(tileSize, tileSize,
                        Bitmap.Config.ARGB_8888);
                final Canvas canvas = new Canvas(bitmap);
                final Paint paint = new Paint();
                canvas.drawColor(mLoadingBackgroundColor);
                paint.setColor(mLoadingLineColor);
                paint.setStrokeWidth(0);
                final int lineSize = tileSize / 16;
                for (int a = 0; a < tileSize; a += lineSize) {
                    canvas.drawLine(0, a, tileSize, a, paint);
                    canvas.drawLine(a, 0, a, tileSize, paint);
                }
                mLoadingTile = new BitmapDrawable(bitmap);
            } catch (final OutOfMemoryError e) {
                // OOM is 'normal' for bitmap operations on Android
                Log.e(LOG_TAG, "OutOfMemoryError getting loading tile", e);
                System.gc();
            }
        }
        return mLoadingTile;
    }

    private void clearLoadingTile() {
        final BitmapDrawable bitmapDrawable = mLoadingTile;
        mLoadingTile = null;
        // Only recycle if we are running on a project less than 2.3.3 Gingerbread.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD) {
            if (bitmapDrawable != null) {
                bitmapDrawable.getBitmap().recycle();
            }
        }
    }

}
