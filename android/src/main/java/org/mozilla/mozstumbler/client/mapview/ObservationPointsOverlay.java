/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.mapview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.os.SystemClock;

import org.mozilla.mozstumbler.client.ObservedLocationsReceiver;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.Overlay;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedList;


class ObservationPointsOverlay extends Overlay {
    private final Paint mRedPaint = new Paint();
    private final Paint mGreenPaint = new Paint();
    private final Paint mTrianglePaint = new Paint();
    private final Paint mSquarePaint = new Paint();
    private final Paint mBlackStrokePaint = new Paint();
    private final Paint mBlackStrokePaintThin = new Paint();
    private final Paint mBlackMLSLinePaint = new Paint();

    final DevicePixelConverter mConvertPx;
    private final WeakReference<MapView> mMapView;

    //private LinkedList<ObservationPoint> mPoints = new LinkedList<ObservationPoint>();

    private static final long DRAW_TIME_MILLIS = 150; // Abort drawing after 150 ms
    private static final int TIME_CHECK_MULTIPLE = 10; // Check the time after drawing 10 points

    public boolean mOnMapShowMLS;
    public boolean mDrawObservationsWithShape;

    ObservationPointsOverlay(Context ctx, MapView mapView) {
        super(ctx);
        mMapView = new WeakReference<MapView>(mapView);
        mConvertPx = new DevicePixelConverter(ctx);

        mGreenPaint.setColor(Color.GREEN);
        mGreenPaint.setStyle(Paint.Style.FILL);

        mSquarePaint.setARGB(255, 0, 170, 0);
        mSquarePaint.setStyle(Paint.Style.FILL);

        mTrianglePaint.setARGB(255, 190, 225, 0);
        mTrianglePaint.setStyle(Paint.Style.FILL);

        mRedPaint.setColor(Color.RED);
        mRedPaint.setStyle(Paint.Style.FILL);

        mBlackStrokePaint.setColor(Color.BLACK);
        mBlackStrokePaint.setStyle(Paint.Style.STROKE);
        mBlackStrokePaint.setStrokeWidth(mConvertPx.pxToDp(2));

        mBlackMLSLinePaint.setARGB(160, 0, 0, 0);
        mBlackMLSLinePaint.setStyle(Paint.Style.STROKE);
        mBlackMLSLinePaint.setStrokeWidth(mConvertPx.pxToDp(2));
        mBlackMLSLinePaint.setStrokeCap(Paint.Cap.ROUND);

        mBlackStrokePaintThin.setColor(Color.BLACK);
        mBlackStrokePaintThin.setStyle(Paint.Style.STROKE);
        mBlackMLSLinePaint.setStrokeWidth(mConvertPx.pxToDp(1));
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

    private void drawRect(Canvas c, Point p, Paint fillPaint) {
        final int size = mConvertPx.pxToDp(4f);
        c.drawRect(p.x - size, p.y - size, p.x + size, p.y + size, fillPaint);
        c.drawRect(p.x - size, p.y - size, p.x + size, p.y + size, mBlackStrokePaintThin);
    }

    private void drawTriangle(Canvas c, Point p, Paint fillPaint) {
        final int size = mConvertPx.pxToDp(5f);

        Point p1 = new Point(p);
        Point p2 = new Point(p);
        Point p3 = new Point(p);
        p1.offset(size, size);
        p2.offset(-size, size);
        p3.offset(0, -size);

        Path path = new Path();
        path.setFillType(Path.FillType.EVEN_ODD);
        path.moveTo(p1.x, p1.y);
        path.lineTo(p2.x, p2.y);
        path.lineTo(p3.x, p3.y);
        path.lineTo(p1.x, p1.y);
        path.close();

        c.drawPath(path, fillPaint);
        c.drawPath(path, mBlackStrokePaintThin);
    }

    protected void draw(Canvas c, MapView osmv, boolean shadow) {
        final long endTime = SystemClock.uptimeMillis() + DRAW_TIME_MILLIS;

        mIsDirty = false;

        LinkedList<ObservationPoint> points = ObservedLocationsReceiver.getInstance().getObservationPoints();
        if (shadow || points.size() < 1) {
            return;
        }

        final Projection pj = osmv.getProjection();
        final float radiusInnerRing = mConvertPx.pxToDp(3);

        int count = 0;

        // iterate newest to oldest
        Iterator<ObservationPoint> i = points.descendingIterator();
        while (i.hasNext()) {
            ObservationPoint point = i.next();
            final Point gps = pj.toPixels(point.pointGPS, null);

            if (mDrawObservationsWithShape && point.mHasWifiScan && !point.mHasCellScan) {
                drawRect(c, gps, mSquarePaint);
            } else if (mDrawObservationsWithShape && point.mHasCellScan && !point.mHasWifiScan) {
                drawTriangle(c, gps, mTrianglePaint);
            } else {
                drawDot(c, gps, radiusInnerRing, mGreenPaint, mBlackStrokePaint);
            }

            if ((++count % TIME_CHECK_MULTIPLE == 0) && (SystemClock.uptimeMillis() > endTime)) {
                break;
            }
        }

        if (!mOnMapShowMLS) {
            return;
        }

        // Draw as a 2nd layer over the observation points
        i = points.descendingIterator();
        while (i.hasNext()) {
            ObservationPoint point = i.next();
            if (point.pointMLS != null) {
                final Point gps = pj.toPixels(point.pointGPS, null);
                final Point mls = pj.toPixels(point.pointMLS, null);
                drawDot(c, mls, radiusInnerRing - 1, mRedPaint, mBlackStrokePaintThin);
                c.drawLine(gps.x, gps.y, mls.x, mls.y, mBlackMLSLinePaint);
            }

            if ((++count % TIME_CHECK_MULTIPLE == 0) && (SystemClock.uptimeMillis() > endTime)) {
                break;
            }
        }
    }


}