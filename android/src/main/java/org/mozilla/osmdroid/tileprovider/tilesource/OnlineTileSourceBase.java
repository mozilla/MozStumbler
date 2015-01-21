package org.mozilla.osmdroid.tileprovider.tilesource;

import org.mozilla.osmdroid.ResourceProxy.string;
import org.mozilla.osmdroid.tileprovider.MapTile;

public abstract class OnlineTileSourceBase extends BitmapTileSourceBase {

    public String mBaseUrls[];
    private long mCacheTime = 12 * 60 * 60 * 1000;

    /**
     * Constructor
     *
     * @param aName                a human-friendly name for this tile source
     * @param aResourceId          resource id used to get the localized name of this tile source
     * @param aZoomMinLevel        the minimum zoom level this tile source can provide
     * @param aZoomMaxLevel        the maximum zoom level this tile source can provide
     * @param aTileSizePixels      the tile size in pixels this tile source provides
     * @param aImageFilenameEnding the file name extension used when constructing the filename
     * @param aBaseUrl             the base url(s) of the tile server used when constructing the url to download the tiles
     */
    public OnlineTileSourceBase(final String aName, final string aResourceId,
                                final int aZoomMinLevel, final int aZoomMaxLevel, final int aTileSizePixels,
                                final String aImageFilenameEnding, final String[] aBaseUrl) {
        super(aName, aResourceId, aZoomMinLevel, aZoomMaxLevel, aTileSizePixels,
                aImageFilenameEnding);
        mBaseUrls = aBaseUrl;
    }

    public abstract String getTileURLString(MapTile aTile);

    /**
     * Get the base url, which will be a random one if there are more than one.
     */
    protected String getBaseUrl() {
        return mBaseUrls[random.nextInt(mBaseUrls.length)];
    }

    @Override
    public long getCacheTime() {
        return mCacheTime;
    }

    @Override
    public void setCacheTime(long aCacheTime) {
        mCacheTime = aCacheTime;
    }
}
