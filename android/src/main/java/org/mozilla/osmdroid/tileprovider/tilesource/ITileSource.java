package org.mozilla.osmdroid.tileprovider.tilesource;

import android.graphics.drawable.Drawable;

import org.mozilla.osmdroid.ResourceProxy;
import org.mozilla.osmdroid.tileprovider.MapTile;
import org.mozilla.osmdroid.tileprovider.tilesource.BitmapTileSourceBase.LowMemoryException;

import java.io.InputStream;

public interface ITileSource {

    /**
     * An ordinal identifier for this tile source
     *
     * @return the ordinal value
     */
    int ordinal();

    /**
     * A human-friendly name for this tile source
     *
     * @return the tile source name
     */
    String name();

    /**
     * A localized human-friendly name for this tile source
     *
     * @param proxy a resource proxy
     * @return the localized tile source name
     */
    String localizedName(ResourceProxy proxy);

    /**
     * Get a unique file path for the tile. This file path may be used to store the tile on a file
     * system and performance considerations should be taken into consideration. It can include
     * multiple paths. It should not begin with a leading path separator.
     *
     * @param aTile the tile
     * @return the unique file path
     */
    String getTileRelativeFilenameString(MapTile aTile);

    /**
     * Get a rendered Drawable from the specified file path.
     *
     * @param aFilePath a file path
     * @return the rendered Drawable
     */
    Drawable getDrawable(byte[] tileData) throws LowMemoryException;

    /**
     * Get the minimum zoom level this tile source can provide.
     *
     * @return the minimum zoom level
     */
    public int getMinimumZoomLevel();

    /**
     * Get the maximum zoom level this tile source can provide.
     *
     * @return the maximum zoom level
     */
    public int getMaximumZoomLevel();

    /**
     * Get the tile size in pixels this tile source provides.
     *
     * @return the tile size in pixels
     */
    public int getTileSizePixels();

    public String getTileURLString(MapTile tile);

}
