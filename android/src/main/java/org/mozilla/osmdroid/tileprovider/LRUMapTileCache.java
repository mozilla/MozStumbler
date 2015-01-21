package org.mozilla.osmdroid.tileprovider;

import android.graphics.drawable.Drawable;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.core.logging.ClientLog;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

import java.util.LinkedHashMap;

/*
 * This is an LRUCache to hold onto Drawable instances for TileOverlay instances.
 *
 * Note that when tiles are evicted from cache,  we attempt to push them back into
 * the BitmapPool if they are recyclable.
 *
 * It is important to note that this class is tightly coupled to the BitmapPool to cache
 * Bitmap instances.
 *
 * The ensureCapacity method grows the LRUMapTileCache to hold onto instances of Bitmaps, but
 * those bitmaps are recycled into the BitmapPool.
 *
 *  If the LRUMapTileCache evicts tiles very quickly, the eldest tiles are pushed immediately back
 *  into the BitmapPool.
 *
 *  The code in BitmapPool should really be merged into this class.
 *
 */
public class LRUMapTileCache {
    private int mCapacity = 0;
    private InnerLRUMapTileCache innerCache;
    private static final String LOG_TAG = LoggerUtil.makeLogTag(LRUMapTileCache.class);

    public LRUMapTileCache(int capacity) {
        ensureCapacity(capacity);
    }

    public synchronized void ensureCapacity(final int aCapacity) {
        if (aCapacity > mCapacity) {
            ClientLog.d(LOG_TAG, "Tile cache increased from " + mCapacity + " to " + aCapacity);
            mCapacity = aCapacity;
            innerCache  = new InnerLRUMapTileCache(mCapacity);
            System.gc();
        }
    }

    public void clear() {
        innerCache.clear();
    }

    public boolean containsKey(MapTile key) {
        return innerCache.containsKey(key);
    }

    public Drawable get(MapTile key) {
        return innerCache.get(key);
    }

    public void put(MapTile key, Drawable value) {
        innerCache.put(key, value);
    }

    private class InnerLRUMapTileCache extends LinkedHashMap<MapTile, Drawable> {

        // This load factor is the same as what is defined in java.util.HashMap
        final static float DEFAULT_LOAD_FACTOR = (float) 0.75;

        final static boolean LRU_USES_ACCESS_ORDER = true;

        private static final long serialVersionUID = -541142277575493335L;


        public InnerLRUMapTileCache(final int aCapacity) {
            // The default implementation of HashMap defines

            // Tile eviction happens at approximately 0.75 * total capacity,
            // so doubling the capacity of the underlying LinkedHashMap should hold onto tiles
            // long enough that we are not constantly constantly pushing tiles out into the
            // BitmapPool.
            super(aCapacity * 2, DEFAULT_LOAD_FACTOR, LRU_USES_ACCESS_ORDER);
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
                if (AppGlobals.isDebug) {
                    ClientLog.d(LOG_TAG, "Remove old tile: " + eldest);
                }
                remove(eldest);
                // don't return true because we've already removed it
            }
            return false;
        }

    }
}
