/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.mapview.tiles;

import android.content.Context;
import android.graphics.Color;

import org.mozilla.osmdroid.tileprovider.tilesource.ITileSource;
import org.mozilla.osmdroid.tileprovider.tilesource.XYTileSource;
import org.mozilla.osmdroid.tileprovider.util.SimpleInvalidationHandler;
import org.mozilla.osmdroid.views.MapView;

/**
 * This class provides the Mozilla Coverage overlay
 */
public class CoverageOverlay extends AbstractMapOverlay {
    // Use a lower zoom than the LowResMapOverlay, the coverage can be very low detail and still look ok
    private static final int LOW_ZOOM_LEVEL = 10;

    public CoverageOverlay(LowResType type, final Context aContext, final String coverageUrl, MapView mapView) {
        super(aContext);

        final int zoomLevel = (type == LowResType.HIGHER_ZOOM)?
                AbstractMapOverlay.getDisplaySizeBasedMinZoomLevel() : LOW_ZOOM_LEVEL;

        final ITileSource coverageTileSource = new XYTileSource("Mozilla Location Service Coverage Map",
                null,
                zoomLevel, zoomLevel,
                AbstractMapOverlay.TILE_PIXEL_SIZE,
                ".png",
                new String[] { coverageUrl });
        this.setLoadingBackgroundColor(Color.TRANSPARENT);
        mTileProvider.setTileRequestCompleteHandler(new SimpleInvalidationHandler(mapView));
        mTileProvider.setTileSource(coverageTileSource);
    }
}
