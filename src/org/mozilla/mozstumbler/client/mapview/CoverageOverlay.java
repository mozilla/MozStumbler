package org.mozilla.mozstumbler.client.mapview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import org.osmdroid.DefaultResourceProxyImpl;
import org.osmdroid.tileprovider.MapTile;
import org.osmdroid.tileprovider.MapTileProviderBasic;
import org.osmdroid.tileprovider.ReusableBitmapDrawable;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.TileLooper;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.TilesOverlay;

import java.util.HashSet;
import java.util.Set;

/**
 * This class provides the Mozilla Coverage overlay
 */
public class CoverageOverlay extends TilesOverlay {

    private final Rect mTileRect = new Rect();
    private final Point mTilePoint = new Point();
    private final Point mTilePointMercator = new Point();
    private final Set<MapTile> mDrawnSet = new HashSet<MapTile>();
    private Projection mProjection;

    public CoverageOverlay(final Context aContext, final String coverageUrl) {
        super(new MapTileProviderBasic(aContext), new DefaultResourceProxyImpl(aContext));
        final ITileSource coverageTileSource = new XYTileSource("Mozilla Location Service Coverage Map",
                null,
                1, 13, 256,
                ".png",
                new String[] { coverageUrl });
        this.setLoadingBackgroundColor(Color.TRANSPARENT);
        mTileProvider.setTileSource(coverageTileSource);
    }

    // Though the tile provider can only provide up to 13, this overlay will display higher.
    @Override
    public int getMaximumZoomLevel() {
        return 18;
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

    private final TileLooper mCoverageTileLooper = new TileLooper() {
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
