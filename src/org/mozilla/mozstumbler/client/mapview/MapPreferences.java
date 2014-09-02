/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.mapview;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.util.GeoPoint;

/**
 * Storage of preferences specific to the map view
 */
public final class MapPreferences {
    private static final String LOGTAG = MapPreferences.class.getName();
    private static final String PREFS_FILE = MapPreferences.class.getName();

    private static final String LAT_PREF = "lat";
    private static final String LON_PREF = "lon";

    private final SharedPreferences mSharedPrefs;

    public MapPreferences(Context context) {
        mSharedPrefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
    }

    /// Setters

    public void setLastMapCenter(IGeoPoint center) {
        SharedPreferences.Editor editor = mSharedPrefs.edit();
        editor.putFloat(LAT_PREF, (float) center.getLatitude());
        editor.putFloat(LON_PREF, (float) center.getLongitude());
        apply(editor);
    }

    /// Getters

    public GeoPoint getLastMapCenter() {
        final float lat = mSharedPrefs.getFloat(LAT_PREF, 0);
        final float lon = mSharedPrefs.getFloat(LON_PREF, 0);
        return new GeoPoint(lat, lon);
    }

    /// Helpers
    @TargetApi(9)
    private static void apply(SharedPreferences.Editor editor) {
        if (Build.VERSION.SDK_INT >= 9) {
            editor.apply();
        } else if (!editor.commit()) {
            Log.e(LOGTAG, "", new IllegalStateException("commit() failed"));
        }
    }
}
