package org.mozilla.osmdroid.tileprovider.tilesource;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

import org.mozilla.mozstumbler.BuildConfig;
import org.mozilla.mozstumbler.service.core.logging.ClientLog;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;
import org.mozilla.osmdroid.ResourceProxy;
import org.mozilla.osmdroid.ResourceProxy.string;
import org.mozilla.osmdroid.tileprovider.BitmapPool;
import org.mozilla.osmdroid.tileprovider.MapTile;
import org.mozilla.osmdroid.tileprovider.ReusableBitmapDrawable;
import org.mozilla.osmdroid.tileprovider.constants.OSMConstants;

import java.util.Random;

public abstract class BitmapTileSourceBase
        implements ITileSource, OSMConstants {

    private static final String LOG_TAG = LoggerUtil.makeLogTag(BitmapTileSourceBase.class);

    private static int globalOrdinal = 0;
    protected final String mName;
    protected final String mImageFilenameEnding;
    protected final Random random = new Random();
    private final int mMinimumZoomLevel;
    private final int mMaximumZoomLevel;
    private final int mOrdinal;
    private final int mTileSizePixels;

    private final string mResourceId;

    /**
     * Constructor
     *
     * @param aName                a human-friendly name for this tile source
     * @param aResourceId          resource id used to get the localized name of this tile source
     * @param aZoomMinLevel        the minimum zoom level this tile source can provide
     * @param aZoomMaxLevel        the maximum zoom level this tile source can provide
     * @param aTileSizePixels      the tile size in pixels this tile source provides
     * @param aImageFilenameEnding the file name extension used when constructing the filename
     */
    public BitmapTileSourceBase(final String aName, final string aResourceId,
                                final int aZoomMinLevel, final int aZoomMaxLevel, final int aTileSizePixels,
                                final String aImageFilenameEnding) {
        mResourceId = aResourceId;
        mOrdinal = globalOrdinal++;
        mName = aName;
        mMinimumZoomLevel = aZoomMinLevel;
        mMaximumZoomLevel = aZoomMaxLevel;
        mTileSizePixels = aTileSizePixels;
        mImageFilenameEnding = aImageFilenameEnding;
    }

    @Override
    public int ordinal() {
        return mOrdinal;
    }

    @Override
    public String name() {
        return mName;
    }

    public String pathBase() {
        return mName;
    }

    public String imageFilenameEnding() {
        return mImageFilenameEnding;
    }

    @Override
    public int getMinimumZoomLevel() {
        return mMinimumZoomLevel;
    }

    @Override
    public int getMaximumZoomLevel() {
        return mMaximumZoomLevel;
    }

    @Override
    public int getTileSizePixels() {
        return mTileSizePixels;
    }

    @Override
    public String localizedName(final ResourceProxy proxy) {
        return proxy.getString(mResourceId);
    }

    @Override
    public Drawable getDrawable(final byte[] tileBytes) {
        try {
            // default implementation will load the file as a bitmap and create
            // a BitmapDrawable from it
            BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
            BitmapPool.getInstance().applyReusableOptions(bitmapOptions);
            final Bitmap bitmap = BitmapFactory.decodeByteArray(tileBytes, 0, tileBytes.length, bitmapOptions);
            if (bitmap != null) {

                if (BuildConfig.LABEL_MAP_TILES) {
                    // Write the tile name directly onto the bitmap
                    int w = bitmap.getWidth();
                    int h = bitmap.getHeight();

                    Paint bgPaint = new Paint();
                    bgPaint.setColor(Color.BLACK);  //transparent black,change opacity by changing hex value "AA" between "00" and "FF"
                    bgPaint.setAlpha(128);
                    Canvas canvas = new Canvas(bitmap);
                    Paint paint = new Paint();
                    paint.setColor(Color.WHITE);
                    paint.setTextSize(24);
                    paint.setAntiAlias(true);
                    paint.setUnderlineText(false);

                    // should draw background first,order is important
                    int border = (int) ((int) (w * 0.01) + 0.5);
                    int left = border;
                    int right = w - border;
                    int bottom = h - border;
                    int top = border;
                    canvas.drawRect(left, top, right, bottom, bgPaint);

                    /*
                    File f = new File(aFilePath);
                    File parentDir = f.getParentFile();
                    File zoomDir = parentDir.getParentFile();
                    try {
                        if (((OnlineTileSourceBase) this).mBaseUrls[0].contains("cloudfront")) {
                            canvas.drawText("Cover: " + zoomDir.getName() + "/" + parentDir.getName() + "/" + f.getName(), 10, h - 15, paint);
                        } else {
                            canvas.drawText("Map: " + zoomDir.getName() + "/" + parentDir.getName() + "/" + f.getName(), 10, h - 45, paint);
                        }
                    } catch (ClassCastException ccex) {
                        Log.e(LOG_TAG, "Casting error", ccex);
                    }
                    */
                }

                return new ReusableBitmapDrawable(bitmap);
            }
        } catch (final OutOfMemoryError e) {
            ClientLog.e(LOG_TAG, "OutOfMemoryError loading bitmap", e);
            System.gc();
        }
        return null;
    }

    @Override
    public String getTileRelativeFilenameString(final MapTile tile) {
        final StringBuilder sb = new StringBuilder();
        sb.append(pathBase());
        sb.append('/');
        sb.append(tile.getZoomLevel());
        sb.append('/');
        sb.append(tile.getX());
        sb.append('/');
        sb.append(tile.getY());
        sb.append(imageFilenameEnding());
        return sb.toString();
    }

    public final class LowMemoryException extends Exception {
        private static final long serialVersionUID = 146526524087765134L;

        public LowMemoryException(final String pDetailMessage) {
            super(pDetailMessage);
        }

        public LowMemoryException(final Throwable pThrowable) {
            super(pThrowable);
        }
    }
}
