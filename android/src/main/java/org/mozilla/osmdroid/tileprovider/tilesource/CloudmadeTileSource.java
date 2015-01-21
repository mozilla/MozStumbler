package org.mozilla.osmdroid.tileprovider.tilesource;

import org.mozilla.mozstumbler.service.core.logging.ClientLog;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;
import org.mozilla.osmdroid.ResourceProxy;
import org.mozilla.osmdroid.tileprovider.MapTile;
import org.mozilla.osmdroid.tileprovider.util.CloudmadeUtil;

public class CloudmadeTileSource extends OnlineTileSourceBase implements IStyledTileSource<Integer> {

    private static final String LOG_TAG = LoggerUtil.makeLogTag(CloudmadeTileSource.class);

    private Integer mStyle = 1;

    public CloudmadeTileSource(final String pName, final ResourceProxy.string pResourceId,
                               final int pZoomMinLevel, final int pZoomMaxLevel, final int pTileSizePixels,
                               final String pImageFilenameEnding, final String[] pBaseUrl) {
        super(pName, pResourceId, pZoomMinLevel, pZoomMaxLevel, pTileSizePixels,
                pImageFilenameEnding, pBaseUrl);
    }

    @Override
    public String pathBase() {
        if (mStyle == null || mStyle <= 1) {
            return mName;
        } else {
            return mName + mStyle;
        }
    }

    @Override
    public String getTileURLString(final MapTile pTile) {
        final String key = CloudmadeUtil.getCloudmadeKey();
        if (key.length() == 0) {
            ClientLog.w(LOG_TAG, "CloudMade key is not set. You should enter it in the manifest and call CloudmadeUtil.retrieveCloudmadeKey()");
        }
        final String token = CloudmadeUtil.getCloudmadeToken();
        return String.format(getBaseUrl(), key, mStyle, getTileSizePixels(), pTile.getZoomLevel(),
                pTile.getX(), pTile.getY(), mImageFilenameEnding, token);
    }

    @Override
    public void setStyle(final Integer pStyle) {
        mStyle = pStyle;
    }

    @Override
    public void setStyle(final String pStyle) {
        try {
            mStyle = Integer.parseInt(pStyle);
        } catch (final NumberFormatException e) {
            ClientLog.w(LOG_TAG, "Error setting integer style: " + pStyle);
        }
    }

    @Override
    public Integer getStyle() {
        return mStyle;
    }
}
