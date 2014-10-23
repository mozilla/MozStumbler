/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.mapview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.location.Location;

import org.mozilla.osmdroid.util.GeoPoint;
import org.mozilla.osmdroid.views.MapView;
import org.mozilla.osmdroid.views.Projection;
import org.mozilla.osmdroid.views.overlay.Overlay;

class AccuracyCircleOverlay extends Overlay {
    private GeoPoint mPoint;
    private float mAccuracy;
    private Paint mCircleFillPaint = new Paint();
    private Paint mCircleStrokePaint = new Paint();
    private Paint mCenterPaint = new Paint();
    private Paint mCenterStrokePaint = new Paint();
    private final DevicePixelConverter mConvertPx;

    AccuracyCircleOverlay(Context ctx, int color) {
        super(ctx);
        mConvertPx = new DevicePixelConverter(ctx);

        mCircleFillPaint.setARGB(40, 100, 100, 255);
        mCircleFillPaint.setStyle(Paint.Style.FILL);

        mCircleStrokePaint.setARGB(165, 100, 100, 255);
        mCircleStrokePaint.setStyle(Paint.Style.STROKE);

        mCenterPaint.setColor(color);
        mCenterPaint.setStyle(Paint.Style.FILL);

        mCenterStrokePaint.setARGB(255, 255, 255, 255);
        mCenterStrokePaint.setStyle(Paint.Style.STROKE);
        mCenterStrokePaint.setStrokeWidth(mConvertPx.pxToDp(2.5f));
    }

    protected void draw(Canvas c, MapView osmv, boolean shadow) {
        if (shadow || mPoint == null) {
            return;
        }
        Projection pj = osmv.getProjection();
        Point center = pj.toPixels(mPoint, null);
        final int radius = mConvertPx.pxToDp(pj.metersToEquatorPixels(mAccuracy));
        final int blueDotRadius = mConvertPx.pxToDp(7.5f);

        // Fill
        c.drawCircle(center.x, center.y, radius, mCircleFillPaint);

        // Border
        c.drawCircle(center.x, center.y, radius, mCircleStrokePaint);

        // Center
        c.drawCircle(center.x, center.y, blueDotRadius, mCenterPaint);
        c.drawCircle(center.x, center.y, blueDotRadius, mCenterStrokePaint);
    }

    public void setLocation(final Location location) {
        mAccuracy = location.getAccuracy();
        mPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
    }

    public GeoPoint getLocation() {
        return mPoint;
    }
}