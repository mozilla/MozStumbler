/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.mapview.tiles;

import android.content.Context;
import android.graphics.Color;

import org.mozilla.mozstumbler.client.mapview.MapFragment;
import org.mozilla.osmdroid.tileprovider.tilesource.ITileSource;
import org.mozilla.osmdroid.tileprovider.tilesource.XYTileSource;
import org.mozilla.osmdroid.tileprovider.util.SimpleInvalidationHandler;
import org.mozilla.osmdroid.views.MapView;

/**
 * This class provides the Mozilla Coverage overlay
 */
public class CoverageOverlay extends AbstractMapOverlay {
    public static final String MLS_MAP_TILE_COVERAGE_NAME = "Mozilla Location Service Coverage Map";
    // Use a lower zoom than the LowResMapOverlay, the coverage can be very low detail and still look ok
    public static final int LOW_ZOOM_LEVEL = 10;

    public CoverageOverlay(TileResType type, final Context aContext, final String coverageUrl, MapView mapView) {
        super(aContext);

        if (type == TileResType.ORIGINAL_ZOOM) {
            setup(MapFragment.LOWEST_UNLIMITED_ZOOM, LOW_ZOOM_LEVEL - 1, coverageUrl, mapView);
            return;
        }

        final int zoomLevel = (type == TileResType.HIGHER_ZOOM) ?
                AbstractMapOverlay.getDisplaySizeBasedMinZoomLevel() : LOW_ZOOM_LEVEL;
        setup(zoomLevel, zoomLevel, coverageUrl, mapView);
    }

    private void setup(int zoomMinLevel, int zoomMaxLevel, final String coverageUrl, MapView mapView) {
        final ITileSource coverageTileSource = new XYTileSource(MLS_MAP_TILE_COVERAGE_NAME,
                null,
                zoomMinLevel, zoomMaxLevel,
                AbstractMapOverlay.TILE_PIXEL_SIZE,
                ".png",
                new String[]{coverageUrl});
        this.setLoadingBackgroundColor(Color.TRANSPARENT);
        mTileProvider.setTileRequestCompleteHandler(new SimpleInvalidationHandler(mapView));
        mTileProvider.setTileSource(coverageTileSource);
    }
}
