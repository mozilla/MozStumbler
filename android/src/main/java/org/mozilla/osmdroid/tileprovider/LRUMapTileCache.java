package org.mozilla.osmdroid.tileprovider;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.core.logging.Log;
import org.mozilla.osmdroid.tileprovider.constants.OpenStreetMapTileProviderConstants;

import java.util.LinkedHashMap;

public class LRUMapTileCache extends LinkedHashMap<MapTile, Drawable>
        implements OpenStreetMapTileProviderConstants {

    private static final String LOG_TAG = AppGlobals.LOG_PREFIX + LRUMapTileCache.class.getSimpleName();

    private static final long serialVersionUID = -541142277575493335L;

    private int mCapacity;

    public LRUMapTileCache(final int aCapacity) {
        super(aCapacity + 2, 0.1f, true);
        mCapacity = aCapacity;
    }

    public void ensureCapacity(final int aCapacity) {
        if (aCapacity > mCapacity) {
            Log.d(LOG_TAG, "Tile cache increased from " + mCapacity + " to " + aCapacity);
            mCapacity = aCapacity;
        }
    }

    @Override
    public Drawable remove(final Object aKey) {
        final Drawable drawable = super.remove(aKey);

        // @TODO: vng is there ever a case where this is not true?
        // BitmapTileSourceBase seems like the only place where drawables are created.
        // This seems like a case where all the interfaces pass around the super class of Drawable,
        // but in reality, we're always actually using ReusableBitmapDrawable instances.
        if (drawable instanceof ReusableBitmapDrawable) {
            BitmapPool.getInstance().returnDrawableToPool((ReusableBitmapDrawable) drawable);
        }
        return drawable;
    }

    @Override
    public void clear() {
        // remove them all individually so that they get recycled
        while (!isEmpty()) {
            remove(keySet().iterator().next());
        }

        // and then clear
        super.clear();
    }

    @Override
    protected boolean removeEldestEntry(final java.util.Map.Entry<MapTile, Drawable> aEldest) {
        if (size() > mCapacity) {
            final MapTile eldest = aEldest.getKey();
            if (DEBUGMODE) {
                Log.d(LOG_TAG, "Remove old tile: " + eldest);
            }
            remove(eldest);
            // don't return true because we've already removed it
        }
        return false;
    }

}
