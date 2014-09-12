/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.mapview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.Overlay;

import java.lang.ref.WeakReference;
import java.util.LinkedList;

class ObservationPointsOverlay extends Overlay {
    private final Paint mRedPaint = new Paint();
    private final Paint mGreenPaint = new Paint();
    private final Paint mBlackStrokePaint = new Paint();
    private final Paint mBlackStrokePaintThin = new Paint();
    private final Paint mBlackLinePaint = new Paint();

    final DevicePixelConverter mConvertPx;
    private final WeakReference<MapView> mMapView;

    private LinkedList<ObservationPoint> mPoints = new LinkedList<ObservationPoint>();

    ObservationPointsOverlay(Context ctx, MapView mapView) {
        super(ctx);
        mMapView = new WeakReference<MapView>(mapView);
        mConvertPx = new DevicePixelConverter(ctx);
        mGreenPaint.setColor(Color.GREEN);
        mGreenPaint.setStyle(Paint.Style.FILL);
        mRedPaint.setColor(Color.RED);
        mRedPaint.setStyle(Paint.Style.FILL);
        mBlackStrokePaint.setARGB(255, 0, 0, 0);
        mBlackStrokePaint.setStyle(Paint.Style.STROKE);
        mBlackStrokePaint.setStrokeWidth(mConvertPx.pxToDp(2));
        mBlackLinePaint.setARGB(160, 0, 0, 0);
        mBlackLinePaint.setStyle(Paint.Style.STROKE);
        mBlackLinePaint.setStrokeWidth(mConvertPx.pxToDp(2));
        mBlackStrokePaintThin.set(mBlackLinePaint);
        mBlackStrokePaintThin.setStrokeWidth(mConvertPx.pxToDp(1));
        mBlackLinePaint.setStrokeCap(Paint.Cap.ROUND);
    }

    void add(ObservationPoint point) {
        mPoints.addFirst(point);
    }

    boolean mIsDirty = false;
    void update() {
        if (mIsDirty) {
            return;
        }
        mIsDirty = true;
        mMapView.get().postInvalidate();
    }

    private void drawDot(Canvas c, Point p, float radiusInnerRing, Paint fillPaint, Paint strokePaint) {
        c.drawCircle(p.x, p.y, radiusInnerRing, fillPaint);
        c.drawCircle(p.x, p.y, radiusInnerRing, strokePaint);
    }

    protected void draw(Canvas c, MapView osmv, boolean shadow) {
        mIsDirty = false;

        if (shadow || mPoints.size() < 1) {
            return;
        }

        final Projection pj = osmv.getProjection();
        final float radiusInnerRing = mConvertPx.pxToDp(3);

        for (ObservationPoint point : mPoints) {
            final Point gps = pj.toPixels(point.pointGPS, null);
            drawDot(c, gps, radiusInnerRing, mGreenPaint ,mBlackStrokePaint);
        }

        // Draw as a 2nd layer over the observation points
        for (ObservationPoint point : mPoints) {
            if (point.pointMLS != null) {
                final Point gps = pj.toPixels(point.pointGPS, null);
                final Point mls = pj.toPixels(point.pointMLS, null);
                drawDot(c, mls, radiusInnerRing - 1, mRedPaint, mBlackStrokePaintThin);
                c.drawLine(gps.x, gps.y, mls.x, mls.y, mBlackLinePaint);
            }
        }
    }


}