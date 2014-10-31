package org.mozilla.osmdroid.tileprovider.tilesource;

import org.mozilla.osmdroid.ResourceProxy.string;
import org.mozilla.osmdroid.tileprovider.MapTile;

/**
 * An implementation of {@link org.mozilla.osmdroid.tileprovider.tilesource.OnlineTileSourceBase}
 */
public class XYTileSource extends OnlineTileSourceBase implements ITileSource {

    public XYTileSource(final String aName, final string aResourceId, final int aZoomMinLevel,
                        final int aZoomMaxLevel, final int aTileSizePixels, final String aImageFilenameEnding,
                        final String[] aBaseUrl) {
        super(aName, aResourceId, aZoomMinLevel, aZoomMaxLevel, aTileSizePixels,
                aImageFilenameEnding, aBaseUrl);
    }

    @Override
    public String getTileURLString(final MapTile aTile) {
        return getBaseUrl() + aTile.getZoomLevel() + "/" + aTile.getX() + "/" + aTile.getY()
                + mImageFilenameEnding;
    }
}
