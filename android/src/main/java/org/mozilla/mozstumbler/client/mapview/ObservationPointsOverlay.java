/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.mapview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;

import org.mozilla.mozstumbler.client.ObservedLocationsReceiver;
import org.mozilla.mozstumbler.service.core.logging.Log;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.Overlay;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedList;


class ObservationPointsOverlay extends Overlay {
    private static final String LOG_TAG = ObservationPointsOverlay.class.getSimpleName();
    private final Paint mRedPaint = new Paint();
    private final Paint mGreenPaint = new Paint();
    private final Paint mCellPaint = new Paint();
    private final Paint mWifiPaint = new Paint();
    private final Paint mBlackStrokePaint = new Paint();
    private final Paint mBlackStrokePaintThin = new Paint();
    private final Paint mBlackMLSLinePaint = new Paint();

    final DevicePixelConverter mConvertPx;

    private static final long DRAW_TIME_MILLIS = 30; // Abort drawing after this time
    private static final int TIME_CHECK_MULTIPLE = 100; // Check the time after drawing this many

    public boolean mOnMapShowMLS;

    ObservationPointsOverlay(Context ctx, MapView mapView) {
        super(ctx);
        mConvertPx = new DevicePixelConverter(ctx);

        mGreenPaint.setColor(Color.GREEN);
        mGreenPaint.setStyle(Paint.Style.FILL);

        mRedPaint.setColor(Color.RED);
        mRedPaint.setStyle(Paint.Style.FILL);

        mBlackStrokePaint.setColor(Color.BLACK);
        mBlackStrokePaint.setStyle(Paint.Style.STROKE);
        mBlackStrokePaint.setStrokeWidth(mConvertPx.pxToDp(2));

        mBlackMLSLinePaint.setARGB(160, 0, 0, 0);
        mBlackMLSLinePaint.setStyle(Paint.Style.STROKE);
        mBlackMLSLinePaint.setStrokeWidth(mConvertPx.pxToDp(1));

        mBlackStrokePaintThin.setColor(Color.BLACK);
        mBlackStrokePaintThin.setStyle(Paint.Style.STROKE);

        mCellPaint.setColor(Color.BLUE);
        mCellPaint.setStyle(Paint.Style.STROKE);
        mCellPaint.setStrokeWidth(mConvertPx.pxToDp(2.5f));

        mWifiPaint.setARGB(255, 160, 0, 180);
        mWifiPaint.setStyle(Paint.Style.STROKE);
        mWifiPaint.setStrokeWidth(mConvertPx.pxToDp(2.5f));
    }

    void update(GeoPoint p, MapView mapView) {
        final Projection pj = mapView.getProjection();
        final Point point = pj.toPixels(p, null);
        final int size = mConvertPx.pxToDp(6);
        final Rect dirty = new Rect(point.x - size, point.y - size, point.x + size, point.y + size);
        dirty.offset(mapView.getScrollX(), mapView.getScrollY());
        mapView.postInvalidate(dirty.left, dirty.top, dirty.right, dirty.bottom);
    }

    private void drawDot(Canvas c, Point p, float radiusInnerRing, Paint fillPaint, Paint strokePaint) {
        c.drawCircle(p.x, p.y, radiusInnerRing, fillPaint);
        c.drawCircle(p.x, p.y, radiusInnerRing, strokePaint);
    }

    private void drawCellScan(Canvas c, Point p) {
        final int size = mConvertPx.pxToDp(3f);
        RectF r = new RectF(p.x - size, p.y - size, p.x + size, p.y + size);
        c.drawRoundRect(r, 1f, 1f, mCellPaint);
    }

    private void drawWifiScan(Canvas c, Point p) {
        final int size = mConvertPx.pxToDp(3f);
        c.drawCircle(p.x, p.y, size, mWifiPaint);
    }

    protected void draw(Canvas c, MapView osmv, boolean shadow) {
        final long endTime = SystemClock.uptimeMillis() + DRAW_TIME_MILLIS;
        LinkedList<ObservationPoint> points = ObservedLocationsReceiver.getInstance().getObservationPoints();
        if (shadow || points.size() < 1) {
            return;
        }

        final Projection pj = osmv.getProjection();
        final float radiusInnerRing = mConvertPx.pxToDp(3);

        int count = 0;
        // The overlay occupies the entire screen, so this returns the screen (0,0,w,h).
        Rect clip = c.getClipBounds();

        // iterate newest to oldest
        Iterator<ObservationPoint> i = points.descendingIterator();
        ObservationPoint point;
        final Point gps = new Point();
        while (i.hasNext()) {
            point = i.next();
            pj.toPixels(point.pointGPS, gps);
            if (!clip.contains(gps.x, gps.y)) {
                continue;
            }

            boolean hasWifiScan = point.mWifiCount > 0;
            boolean hasCellScan = point.mCellCount > 0;

            if (hasWifiScan && !hasCellScan) {
                drawWifiScan(c, gps);
            } else if (hasCellScan && !hasWifiScan) {
                drawCellScan(c, gps);
            } else {
                drawDot(c, gps, radiusInnerRing, mGreenPaint, mBlackStrokePaint);
            }

            if ((++count % TIME_CHECK_MULTIPLE == 0) && (SystemClock.uptimeMillis() > endTime)) {
                Log.i(LOG_TAG, "timed out");
                break;
            }
        }

        if (!mOnMapShowMLS) {
            return;
        }

        // Draw as a 2nd layer over the observation points
        i = points.descendingIterator();
        final Point mls = new Point();
        while (i.hasNext()) {
            point = i.next();
            if (point.pointMLS != null) {
                pj.toPixels(point.pointGPS, gps);
                pj.toPixels(point.pointMLS, mls);
                drawDot(c, mls, radiusInnerRing - 1, mRedPaint, mBlackStrokePaintThin);
                c.drawLine(gps.x, gps.y, mls.x, mls.y, mBlackMLSLinePaint);
            }

            if ((++count % TIME_CHECK_MULTIPLE == 0) && (SystemClock.uptimeMillis() > endTime)) {
                break;
            }
        }
    }
}
