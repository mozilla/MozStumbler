/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.mapview.tiles;

import android.content.Context;
import android.graphics.Color;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.osmdroid.tileprovider.tilesource.ITileSource;
import org.mozilla.osmdroid.tileprovider.tilesource.XYTileSource;
import org.mozilla.osmdroid.tileprovider.util.SimpleInvalidationHandler;
import org.mozilla.osmdroid.views.MapView;

import org.mozilla.osmdroid.tileprovider.MapTile;

public class LowResMapOverlay extends AbstractMapOverlay {
    public static final int LOW_ZOOM_LEVEL = 11;

    public LowResMapOverlay(TileResType type, final Context aContext, MapView mapView) {
        super(aContext);

        final int zoomLevel = (type == TileResType.HIGHER_ZOOM) ?
                AbstractMapOverlay.getDisplaySizeBasedMinZoomLevel() : LOW_ZOOM_LEVEL;


        ITileSource mapTileSource = new XYTileSource(MLS_MAP_TILE_BASE_NAME, null,
                zoomLevel, zoomLevel,
                AbstractMapOverlay.TILE_PIXEL_SIZE,
                AbstractMapOverlay.FILE_TYPE_SUFFIX_PNG,
                AppGlobals.MAPBOX_TILE_URLS) {
            public String getTileURLString(MapTile aTile) {
                return super.getTileURLString(aTile) + "?access_token=" + AppGlobals.MAPBOX_ACCESS_CODE;
            }
        };

        this.setLoadingBackgroundColor(Color.TRANSPARENT);
        mTileProvider.setTileRequestCompleteHandler(new SimpleInvalidationHandler(mapView));
        mTileProvider.setTileSource(mapTileSource);
    }
}
