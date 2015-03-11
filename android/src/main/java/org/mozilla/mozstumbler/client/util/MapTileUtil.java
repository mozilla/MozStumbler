package org.mozilla.mozstumbler.client.util;

import android.location.Location;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.osmdroid.util.BoundingBoxE6;
import org.mozilla.osmdroid.views.MapView;

import java.util.ArrayList;

// see http://wiki.openstreetmap.org/wiki/Slippy_map_tilenames
public class MapTileUtil {
    public static class TileId {
        protected final int x;
        protected final int y;

        public TileId(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public String toString() {
            return x + "/" + y;
        }

        public int x() {
            return x;
        }

        public int y() {
            return y;
        }
    }

    public static TileId degreesToGridNumber(Location location, int zoomLevel) {
        double lat = location.getLatitude();
        double lon = location.getLongitude();
        return getTileNumber(lat, lon, zoomLevel);
    }

    public static TileId getTileNumber(final double lat, final double lon, final int zoom) {
        int xtile = (int)Math.floor( (lon + 180) / 360 * (1<<zoom) ) ;
        int ytile = (int)Math.floor( (1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1<<zoom) ) ;
        if (xtile < 0)
            xtile=0;
        if (xtile >= (1<<zoom))
            xtile=((1<<zoom)-1);
        if (ytile < 0)
            ytile=0;
        if (ytile >= (1<<zoom))
            ytile=((1<<zoom)-1);
        return new TileId(xtile, ytile);
    }

    public static  double tile2lon(int x, int z) {
        return x / Math.pow(2.0, z) * 360.0 - 180;
    }

    public static  double tile2lat(int y, int z) {
        double n = Math.PI - (2.0 * Math.PI * y) / Math.pow(2.0, z);
        return Math.toDegrees(Math.atan(Math.sinh(n)));
    }

    public static Location gridNumberToDegrees(String id, int zoom) {
        String[] split = id.split("/");
        assert(split.length == 2);
        int x = Integer.valueOf(split[0]);
        int y = Integer.valueOf(split[1]);
        return  gridNumberToDegrees(new TileId(x, y), zoom);
    }

    public static Location gridNumberToDegrees(TileId grid, int zoom) {
        Location location = new Location(AppGlobals.LOCATION_ORIGIN_INTERNAL);
        location.setLatitude(tile2lat(grid.y, zoom));
        location.setLongitude(tile2lon(grid.x, zoom));
        return location;
    }

    public static boolean boundingBox(MapView mapView, Location outMin, Location outMax) {
        BoundingBoxE6 bbox = mapView.getBoundingBox();
        if (bbox.getLonWestE6() == -180 * 1E6) {
            // this is an initialized state from what I can tell
            return false;
        }
        double lonw = bbox.getLonWestE6() / 1E6;
        double lone = bbox.getLonEastE6() / 1E6;
        double latn = bbox.getLatNorthE6() / 1E6;
        double lats = bbox.getLatSouthE6() / 1E6;

        outMin.setLatitude(lats);
        outMin.setLongitude(lonw);
        outMax.setLatitude(latn);
        outMax.setLongitude(lone);
        return true;
    }

    public static  ArrayList<TileId> generateGrids(TileId low, TileId high) {
        int minx = Math.min(low.x, high.x);
        int miny = Math.min(low.y, high.y);
        int maxx = Math.max(low.x, high.x);
        int maxy = Math.max(low.y, high.y);

        ArrayList<TileId> result = new ArrayList<TileId>();
        for (int i = minx; i <= maxx; i++) {
            for (int j = miny; j <= maxy; j++) {
                result.add(new TileId(i, j));
            }
        }
        return result;
    }
}
