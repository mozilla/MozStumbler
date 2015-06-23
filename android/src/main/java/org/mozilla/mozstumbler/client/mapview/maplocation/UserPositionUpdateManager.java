/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.mapview.maplocation;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.widget.Toast;
import org.mozilla.mozstumbler.client.MainApp;
import org.mozilla.mozstumbler.client.mapview.MapFragment;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.core.logging.ClientLog;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;


public class UserPositionUpdateManager {
    private static final String LOG_TAG = LoggerUtil.makeLogTag(UserPositionUpdateManager.class);
    private LocationManager mLocationManager;
    private final MultiSourceLocationListener mMultiSourceLocationListener;

    public UserPositionUpdateManager(MapFragment mapFragment, boolean passive) {
        Context context = mapFragment.getActivity().getApplicationContext();
        mLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        mMultiSourceLocationListener = new MultiSourceLocationListener(mLocationManager, mapFragment, passive);

        if (mLocationManager == null) {
            // Ugly non-localized message, which is fine, the app is not usable on any device that shows this toast.
            Toast.makeText(context, "Error: no LOCATION_SERVICE", Toast.LENGTH_LONG).show();
            return;
        }

        Location lastLoc = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (lastLoc == null) {
            lastLoc = mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }
        if (lastLoc != null) {
            ClientLog.d(LOG_TAG, "set last known location");
            mapFragment.setUserPositionAt(lastLoc);
        }

        MainApp app = mapFragment.getApplication();
        if (app == null) {
            return;
        }
        mMultiSourceLocationListener.start();
    }

    static void enableLocationListener(LocationManager manager, boolean isEnabled, MapUpdatingLocationListener listener) {
        if (manager == null) {
            return;
        }

        try {
            if (isEnabled && !listener.mIsActive) {
                listener.requestLocationUpdates(manager);
            } else if (!isEnabled && listener.mIsActive) {
                manager.removeUpdates(listener);
            }
        } catch (IllegalArgumentException ex) {
            AppGlobals.guiLogError("enableLocationListener failed");
        }

        listener.mIsActive = isEnabled;
    }

    public void removeListener() {
        if (mLocationManager == null) {
            return;
        }
        mMultiSourceLocationListener.stop();
    }
}