package org.mozilla.mozstumbler.client.mapview;

import android.content.Context;
import android.graphics.Color;

import org.osmdroid.DefaultResourceProxyImpl;
import org.osmdroid.tileprovider.MapTileProviderBasic;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.views.overlay.TilesOverlay;

/**
 * This class provides the Mozilla Coverage overlay
 */
public class CoverageOverlay extends TilesOverlay {

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
}
