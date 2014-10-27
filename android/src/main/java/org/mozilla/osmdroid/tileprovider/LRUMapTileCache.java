package org.mozilla.osmdroid.tileprovider;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.core.logging.Log;
import org.mozilla.osmdroid.tileprovider.constants.OpenStreetMapTileProviderConstants;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/*
 * @TODO vng: This class should probably just go away.  It's incorrect in so many
 * ways that it's almost certainly not doing what we really want.
 *
 * We probably want to just replace this with android.util.LruCache in the support
 * library.
 *
 * This should be done with caution as I've tried 'fixing' this LRU to actually behave in
 * LRU fashion completely breaks the map and tiles are
 * continually evicted from the view and reloaded causing a continuous checkerboard reloading
 * pattern.
 *
 */
public class LRUMapTileCache {
    private int mCapacity = 0;
    private InnerLRUMapTileCache innerCache;
    private static final String LOG_TAG = AppGlobals.LOG_PREFIX + LRUMapTileCache.class.getSimpleName();

    public LRUMapTileCache(int capacity) {
        ensureCapacity(capacity);
    }

    public synchronized void ensureCapacity(final int aCapacity) {
        /*
         * @TODO vng: this method is just wrong.
         * mCapacity is unknown to the base LinkedHashmap so the LRU properties
         * will not apply to this new size.
         *
         * What *will* happen though is that tile eviction will happen in a bizarre way
         * as removeEldestEntry is also misimplemented and will only evict tiles
         * if the mCapacity number has been met.
         *
         * The combination of the two means that the buckets in the LinkedHashMap have a
         * tendency to grow to too long and grow to be suboptimal lengths.
         */

        if (aCapacity > mCapacity) {
            Log.d(LOG_TAG, "Tile cache increased from " + mCapacity + " to " + aCapacity);
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


        private static final long serialVersionUID = -541142277575493335L;


        public InnerLRUMapTileCache(final int aCapacity) {
            super(aCapacity * 2, (float)0.75, true);
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
        /*
         * @TODO vng: this method is totally wrong.  You *must* evict
         * when this method is called as the hashmap has exhausted the
         * bucket size.
         */
            if (size() > mCapacity) {
                final MapTile eldest = aEldest.getKey();
                if (AppGlobals.isDebug) {
                    Log.d(LOG_TAG, "Remove old tile: " + eldest);
                }
                remove(eldest);
                // don't return true because we've already removed it
            }
            return false;
        }

    }
}
