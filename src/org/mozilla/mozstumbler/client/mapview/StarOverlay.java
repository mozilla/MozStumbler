package org.mozilla.mozstumbler.client.mapview;

import android.content.Context;
import android.graphics.Point;
import android.graphics.PointF;
import android.location.Location;
import android.view.MotionEvent;

import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.overlay.ItemizedOverlay;
import com.mapbox.mapboxsdk.overlay.Marker;
import com.mapbox.mapboxsdk.views.MapView;
import com.mapbox.mapboxsdk.views.util.Projection;

import org.mozilla.mozstumbler.R;

import java.util.Random;

/**
 * Created by JeremyChiang on 2014-08-07.
 */
public class StarOverlay extends ItemizedOverlay {

    public interface StarOverlaySelectedListener {
        public void starOverlaySelected(StarOverlay selectedStarOverlay);
    }

    private Marker starMarker;
    private StarOverlaySelectedListener starOverlaySelectedListener;

    public StarOverlay(Context context, MapView mapView, LatLng originToRadiateFrom) {
        Random rand = new Random();
        int randomLatCoefficient = rand.nextInt((10 - -10) + 1) + -10;
        int randomLonCoefficient = rand.nextInt((10 - -10) + 1) + -10;

        LatLng randomLatLng =
                new LatLng(
                        originToRadiateFrom.getLatitude() + randomLatCoefficient * 0.0005,
                        originToRadiateFrom.getLongitude() + randomLonCoefficient * 0.0005);

        starMarker = new Marker(mapView, null, null, randomLatLng);
        starMarker.setMarker(context.getResources().getDrawable(R.drawable.star_marker_map));
        starMarker.setHotspot(Marker.HotspotPlace.CENTER);

        populate();
    }

    @Override
    protected Marker createItem(int i) {
        return starMarker;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public boolean onSnapToItem(int i, int i2, Point point, MapView mapView) {
        return false;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e, MapView mapView) {
        Projection projection = new Projection(mapView);
        PointF positionOnScreen = starMarker.getPositionOnScreen(projection, null);

        float xDiff = Math.abs(e.getX() - positionOnScreen.x);
        float yDiff = Math.abs(e.getY() - positionOnScreen.y);

        if (xDiff <= 50 && yDiff <= 50) {
            starOverlaySelectedListener.starOverlaySelected(this);
        }

        return super.onSingleTapConfirmed(e, mapView);
    }

    public void setStarOverlaySelectedListener(StarOverlaySelectedListener starOverlaySelectedListener) {
        this.starOverlaySelectedListener = starOverlaySelectedListener;
    }

    public LatLng getLatLng() {
        return starMarker.getPoint();
    }
}
