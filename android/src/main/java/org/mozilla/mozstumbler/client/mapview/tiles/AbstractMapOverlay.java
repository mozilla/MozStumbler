/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.mapview.tiles;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import org.mozilla.osmdroid.DefaultResourceProxyImpl;
import org.mozilla.osmdroid.tileprovider.MapTile;
import org.mozilla.osmdroid.tileprovider.MapTileProviderBasic;
import org.mozilla.osmdroid.tileprovider.ReusableBitmapDrawable;
import org.mozilla.osmdroid.util.TileLooper;
import org.mozilla.osmdroid.views.Projection;
import org.mozilla.osmdroid.views.overlay.TilesOverlay;

import java.util.HashSet;
import java.util.Set;

public abstract class AbstractMapOverlay extends TilesOverlay {
    // We want the map to zoom to level 20, even if tiles have less zoom available
    public static final int MAX_ZOOM_LEVEL_OF_MAP = 20;
    public static final int MIN_ZOOM_LEVEL_OF_MAP = 13;

    public static final int TILE_PIXEL_SIZE = 256;
    // Use png32 which is a 32-color indexed image, the tiles are ~30% smaller
    public static String FILE_TYPE_SUFFIX_PNG = ".png32";
    private final Rect mTileRect = new Rect();
    private final Point mTilePoint = new Point();
    private final Point mTilePointMercator = new Point();
    private final Set<MapTile> mDrawnSet = new HashSet<MapTile>();
    private Projection mProjection;

    public AbstractMapOverlay(final Context aContext) {
        super(new MapTileProviderBasic(aContext), new DefaultResourceProxyImpl(aContext));
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

    protected final TileLooper mCoverageTileLooper = new TileLooper() {
        @Override
        public void initialiseLoop(int pZoomLevel, int pTileSizePx) {
            // make sure the cache is big enough for all the tiles
            final int numNeeded = (mLowerRight.y - mUpperLeft.y + 1) * (mLowerRight.x - mUpperLeft.x + 1);
            mTileProvider.ensureCapacity(numNeeded + getOvershootTileCache());
        }

        @Override
        public void finaliseLoop() {}

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

    private void onTileReadyToDraw2(final Canvas c, final Drawable currentMapTile,
                                     final Rect tileRect) {
        mProjection.toPixelsFromMercator(tileRect.left, tileRect.top, mTilePointMercator);
        tileRect.offsetTo(mTilePointMercator.x, mTilePointMercator.y);
        currentMapTile.setBounds(tileRect);
        currentMapTile.draw(c);
    }
}
