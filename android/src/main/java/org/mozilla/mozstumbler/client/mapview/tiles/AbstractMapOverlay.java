/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.mapview.tiles;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import org.mozilla.osmdroid.DefaultResourceProxyImpl;
import org.mozilla.osmdroid.tileprovider.BetterTileProvider;
import org.mozilla.osmdroid.tileprovider.MapTile;
import org.mozilla.osmdroid.tileprovider.ReusableBitmapDrawable;
import org.mozilla.osmdroid.util.TileLooper;
import org.mozilla.osmdroid.views.Projection;
import org.mozilla.osmdroid.views.overlay.TilesOverlay;

import java.util.HashSet;
import java.util.Set;

public abstract class AbstractMapOverlay extends TilesOverlay {
    public static final String MLS_MAP_TILE_BASE_NAME = "Stumbler-BaseMap-Tiles";
    // We want the map to zoom to level 20, even if tiles have less zoom available
    public static final int MAX_ZOOM_LEVEL_OF_MAP = 20;
    public static final int TILE_PIXEL_SIZE = 256;
    // TODO make this a single value configurable in developer settings
    private static final int SMALL_SCREEN_MIN_ZOOM = 11;
    private static final int MEDIUM_SCREEN_MIN_ZOOM = 12;
    protected static final int LARGE_SCREEN_MIN_ZOOM = 13;
    // Use png32 which is a 32-color indexed image, the tiles are ~30% smaller
    public static String FILE_TYPE_SUFFIX_PNG = ".png32";
    private static int sMinZoomLevelOfMapDisplaySizeBased;
    private final Rect mTileRect = new Rect();
    protected final TileLooper mCoverageTileLooper = new TileLooper() {
        @Override
        public void initialiseLoop(int pZoomLevel, int pTileSizePx) {
            // make sure the cache is big enough for all the tiles
            final int numNeeded = (mLowerRight.y - mUpperLeft.y + 1) * (mLowerRight.x - mUpperLeft.x + 1);
            mTileProvider.ensureCapacity(numNeeded + OVERSHOOT_TILE_CACHE_SIZE);
        }

        @Override
        public void finaliseLoop() {
        }

        @Override
        public void handleTile(Canvas pCanvas, int pTileSizePx, MapTile pTile, int pX, int pY) {
            int zoomLevel = Math.min(pTile.getZoomLevel(), mTileProvider.getMaximumZoomLevel());
            final int zoomDifference = pTile.getZoomLevel() - zoomLevel;
            final int scaleDiff = 1 << zoomDifference;

            pTileSizePx *= scaleDiff;
            pX /= scaleDiff;
            pY /= scaleDiff;

            final MapTile tile = new MapTile(zoomLevel, pTile.getX() >> zoomDifference, pTile.getY() >> zoomDifference);
            if (mDrawnSet.contains(tile)) {
                return;
            }
            mDrawnSet.add(tile);

            Drawable currentMapTile = mTileProvider.getMapTile(tile);

            boolean isReusable = currentMapTile instanceof ReusableBitmapDrawable;
            final ReusableBitmapDrawable reusableBitmapDrawable =
                    isReusable ? (ReusableBitmapDrawable) currentMapTile : null;

            if (currentMapTile != null) {
                mTilePoint.set(pX * pTileSizePx, pY * pTileSizePx);
                mTileRect.set(mTilePoint.x, mTilePoint.y, mTilePoint.x + pTileSizePx, mTilePoint.y
                        + pTileSizePx);
                if (isReusable) {
                    reusableBitmapDrawable.beginUsingDrawable();
                }
                try {
                    onTileReadyToDraw2(pCanvas, currentMapTile, mTileRect);
                } finally {
                    if (isReusable)
                        reusableBitmapDrawable.finishUsingDrawable();
                }
            }
        }
    };
    private final Point mTilePoint = new Point();
    private final Point mTilePointMercator = new Point();
    private final Set<MapTile> mDrawnSet = new HashSet<MapTile>();
    private Projection mProjection;

    public AbstractMapOverlay(final Context context) {
        super(new BetterTileProvider(context), new DefaultResourceProxyImpl(context));
    }

    public static void setDisplayBasedMinimumZoomLevel(Context c) {
        WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        DisplayMetrics outMetrics = new DisplayMetrics();
        display.getMetrics(outMetrics);

        if (outMetrics.widthPixels > 700) {
            sMinZoomLevelOfMapDisplaySizeBased = LARGE_SCREEN_MIN_ZOOM;
        } else if (outMetrics.widthPixels < 500) {
            sMinZoomLevelOfMapDisplaySizeBased = SMALL_SCREEN_MIN_ZOOM;
        } else {
            sMinZoomLevelOfMapDisplaySizeBased = MEDIUM_SCREEN_MIN_ZOOM;
        }
    }

    public static int getDisplaySizeBasedMinZoomLevel() {
        return sMinZoomLevelOfMapDisplaySizeBased;
    }

    // Though the tile provider can only provide up to 13, this overlay will display higher.
    @Override
    public int getMaximumZoomLevel() {
        return MAX_ZOOM_LEVEL_OF_MAP;
    }

    @Override
    public void drawTiles(final Canvas c, final Projection projection, final int zoomLevel,
                          final int tileSizePx, final Rect viewPort) {
        if (zoomLevel <= mTileProvider.getMaximumZoomLevel()) {
            super.drawTiles(c, projection, zoomLevel, tileSizePx, viewPort);
        } else {
            mProjection = projection;
            mDrawnSet.clear();
            mCoverageTileLooper.loop(c, zoomLevel, tileSizePx, viewPort);
        }
    }

    private void onTileReadyToDraw2(final Canvas c, final Drawable currentMapTile,
                                    final Rect tileRect) {
        mProjection.toPixelsFromMercator(tileRect.left, tileRect.top, mTilePointMercator);
        tileRect.offsetTo(mTilePointMercator.x, mTilePointMercator.y);
        currentMapTile.setBounds(tileRect);
        currentMapTile.draw(c);
    }

    public enum TileResType {
        HIGHER_ZOOM, LOWER_ZOOM, ORIGINAL_ZOOM
    }
}
