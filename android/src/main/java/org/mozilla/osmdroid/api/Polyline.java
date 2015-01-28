package org.mozilla.osmdroid.api;

import android.graphics.Color;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Polyline {

    /**
     * The color of the polyline. Defaults to black.
     */
    public int color = Color.BLACK;
    /**
     * The width of the polyline. Defaults to 2.
     */
    public float width = 2.0f;
    /**
     * The points of the polyline.
     */
    public List<IGeoPoint> points;

    public Polyline() {
        points = new ArrayList<IGeoPoint>();
    }

    /**
     * The color of the polyline. Defaults to black.
     * This method returns the polyline for convenient method chaining.
     */
    public Polyline color(final int aColor) {
        color = aColor;
        return this;
    }

    /**
     * The width of the polyline. Defaults to 2.
     * This method returns the polyline for convenient method chaining.
     */
    public Polyline width(final float aWidth) {
        width = aWidth;
        return this;
    }

    /**
     * The points of the polyline.
     * This method returns the polyline for convenient method chaining.
     */
    public Polyline points(final List<IGeoPoint> aPoints) {
        points = aPoints;
        return this;
    }

    /**
     * The points of the polyline.
     * This method returns the polyline for convenient method chaining.
     */
    public Polyline points(final IGeoPoint... aPoints) {
        return points(Arrays.asList(aPoints));
    }
}
