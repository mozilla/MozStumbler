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

    public CoverageOverlay(final Context aContext, final String coverageUrl, MapView mapView) {
        super(aContext);
        final ITileSource coverageTileSource = new XYTileSource("Mozilla Location Service Coverage Map",
                null,
                12, 13, AbstractMapOverlay.TILE_PIXEL_SIZE,
                ".png",
                new String[] { coverageUrl });
        this.setLoadingBackgroundColor(Color.TRANSPARENT);
        mTileProvider.setTileRequestCompleteHandler(new SimpleInvalidationHandler(mapView));
        mTileProvider.setTileSource(coverageTileSource);
    }
}
