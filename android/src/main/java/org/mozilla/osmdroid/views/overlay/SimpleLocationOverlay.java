// Created by plusminus on 22:01:11 - 29.09.2008
package org.mozilla.osmdroid.views.overlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;

import org.mozilla.osmdroid.DefaultResourceProxyImpl;
import org.mozilla.osmdroid.ResourceProxy;
import org.mozilla.osmdroid.util.GeoPoint;
import org.mozilla.osmdroid.views.MapView;
import org.mozilla.osmdroid.views.Projection;

/**
 * @author Nicolas Gramlich
 */
public class SimpleLocationOverlay extends Overlay {
    // ===========================================================
    // Constants
    // ===========================================================

    // ===========================================================
    // Fields
    // ===========================================================

    protected final Paint mPaint = new Paint();

    protected final Bitmap PERSON_ICON;
    /**
     * Coordinates the feet of the person are located.
     */
    protected final android.graphics.Point PERSON_HOTSPOT = new android.graphics.Point(24, 39);
    private final Point screenCoords = new Point();
    protected GeoPoint mLocation;

    // ===========================================================
    // Constructors
    // ===========================================================

    public SimpleLocationOverlay(final Context ctx) {
        this(ctx, new DefaultResourceProxyImpl(ctx));
    }

    public SimpleLocationOverlay(final Context ctx,
                                 final ResourceProxy pResourceProxy) {
        super(pResourceProxy);
        this.PERSON_ICON = mResourceProxy.getBitmap(ResourceProxy.bitmap.person);
    }

    // ===========================================================
    // Getter & Setter
    // ===========================================================

    public void setLocation(final GeoPoint mp) {
        this.mLocation = mp;
    }

    public GeoPoint getMyLocation() {
        return this.mLocation;
    }

    // ===========================================================
    // Methods from SuperClass/Interfaces
    // ===========================================================

    @Override
    public void draw(final Canvas c, final MapView osmv, final boolean shadow) {
        if (!shadow && this.mLocation != null) {
            final Projection pj = osmv.getProjection();
            pj.toPixels(this.mLocation, screenCoords);

            c.drawBitmap(PERSON_ICON, screenCoords.x - PERSON_HOTSPOT.x, screenCoords.y
                    - PERSON_HOTSPOT.y, this.mPaint);
        }
    }

    // ===========================================================
    // Methods
    // ===========================================================

    // ===========================================================
    // Inner and Anonymous Classes
    // ===========================================================
}
