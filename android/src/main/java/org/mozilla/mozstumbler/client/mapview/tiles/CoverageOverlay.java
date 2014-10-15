/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.mapview.tiles;

import android.content.Context;
import android.graphics.Color;

import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.tileprovider.util.SimpleInvalidationHandler;
import org.osmdroid.views.MapView;

/**
 * This class provides the Mozilla Coverage overlay
 */
public class CoverageOverlay extends AbstractMapOverlay {
    public static final int HIGH_ZOOM = AbstractMapOverlay.MIN_ZOOM_LEVEL_OF_MAP;
    // Use a lower zoom than the LowResMapOverlay, the coverage can be very low detail and still look ok
    public static final int LOW_ZOOM = 10;

    public CoverageOverlay(int zoomLevel, final Context aContext, final String coverageUrl, MapView mapView) {
        super(aContext);
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
