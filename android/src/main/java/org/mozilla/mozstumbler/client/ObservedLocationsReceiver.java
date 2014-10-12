/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;

import org.json.JSONObject;
import org.mozilla.mozstumbler.client.mapview.MapActivity;
import org.mozilla.mozstumbler.client.mapview.ObservationPoint;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.GPSScanner;
import org.mozilla.mozstumbler.service.utils.CoordinateUtils;
import org.osmdroid.util.GeoPoint;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedList;

public class ObservedLocationsReceiver extends BroadcastReceiver {

    public interface ICountObserver {
        public void observedLocationCountIncrement();
    }

    private static final String LOG_TAG = ObservedLocationsReceiver.class.getSimpleName();
    private WeakReference<MapActivity> mMapActivity = new WeakReference<MapActivity>(null);
    private final LinkedList<ObservationPoint> mCollectionPoints = new LinkedList<ObservationPoint>();
    private final LinkedList<ObservationPoint> mQueuedForMLS = new LinkedList<ObservationPoint>();
    private final int MAX_QUEUED_MLS_POINTS_TO_FETCH = 10;
    private final long FREQ_FETCH_MLS_MS = 5 * 1000;
    private JSONObject mPreviousBundleForDuplicateCheck;
    private WeakReference<ICountObserver> mCountObserver = new WeakReference<ICountObserver>(null);
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    // Upper bound on the size of the linked lists of points, for memory and performance safety.
    private final int MAX_SIZE_OF_POINT_LISTS = 5000;

    private ObservedLocationsReceiver() {
        mHandler.postDelayed(mFetchMLSRunnable, FREQ_FETCH_MLS_MS);
    }

    private static ObservedLocationsReceiver sInstance;

    public static void createGlobalInstance(Context context, ICountObserver countObserver) {
        sInstance = new ObservedLocationsReceiver();
        sInstance.mCountObserver = new WeakReference<ICountObserver>(countObserver);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(GPSScanner.ACTION_GPS_UPDATED);
        LocalBroadcastManager.getInstance(context).registerReceiver(sInstance, intentFilter);
    }

    public static ObservedLocationsReceiver getInstance() {
        return sInstance;
    }

    public LinkedList<ObservationPoint> getObservationPoints() {
        return mCollectionPoints;
    }

    private final Runnable mFetchMLSRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (ObservedLocationsReceiver.this) {
                mHandler.postDelayed(mFetchMLSRunnable, FREQ_FETCH_MLS_MS);
                if (mQueuedForMLS.size() < 1 || !ClientPrefs.getInstance().getOnMapShowMLS()) {
                    return;
                }
                int count = 0;
                Iterator<ObservationPoint> li = mQueuedForMLS.iterator();
                while (li.hasNext() && count < MAX_QUEUED_MLS_POINTS_TO_FETCH) {
                    ObservationPoint obs = li.next();
                    if (obs.needsToFetchMLS()) {
                        obs.fetchMLS();
                        count++;
                    } else {
                        if (getMapActivity() != null) {
                            getMapActivity().newMLSPoint(obs);
                        }
                        li.remove();
                    }
                }
            }
        }
    };

    // Must be called by map activity when it is showing to get points displayed
    public synchronized void setMapActivity(MapActivity m) {
        mMapActivity = new WeakReference<MapActivity>(m);
    }

    // Must call when map activity stopped, to clear the reference to the map activity object
    public void removeMapActivity() {
        setMapActivity(null);
    }

    private synchronized MapActivity getMapActivity() {
        return mMapActivity.get();
    }

    @Override
    public synchronized void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        final String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);
        if (!action.equals(GPSScanner.ACTION_GPS_UPDATED) || !subject.equals(GPSScanner.SUBJECT_NEW_LOCATION)) {
            return;
        }

        final Location newPosition = intent.getParcelableExtra(GPSScanner.NEW_LOCATION_ARG_LOCATION);
        if (newPosition == null || !CoordinateUtils.isValidLocation(newPosition)) {
            return;
        }

        final Context appContext = context.getApplicationContext();
        final ClientStumblerService service = ((MainApp) appContext).getService();
        ObservationPoint lastObservation = null;
        if (mCollectionPoints.size() > 0) {
            lastObservation = mCollectionPoints.getLast();
        }

        if (service != null) {
            JSONObject currentBundle = service.getLastReportedBundle();
            if (mPreviousBundleForDuplicateCheck == currentBundle) {
                return;
            }
            mPreviousBundleForDuplicateCheck = currentBundle;

            if (lastObservation != null) {
                if (ClientPrefs.getInstance().isOptionEnabledToShowMLSOnMap()) {
                    lastObservation.setMLSQuery(currentBundle);
                    if (mQueuedForMLS.size() < MAX_SIZE_OF_POINT_LISTS) {
                        mQueuedForMLS.addFirst(lastObservation);
                    }
                } else {
                    lastObservation.setCounts(currentBundle);
                }
            }
        }

        // Notify main app of observation
        if (mCountObserver.get() != null) {
            mCountObserver.get().observedLocationCountIncrement();
        }

        if (mCollectionPoints.size() > MAX_SIZE_OF_POINT_LISTS) {
            return;
        }

        ObservationPoint observation = new ObservationPoint(new GeoPoint(newPosition));
        mCollectionPoints.add(observation);
        if (lastObservation != null) {
            observation.mHeading = observation.pointGPS.bearingTo(lastObservation.pointGPS);
        }

        if (getMapActivity() == null) {
            return;
        }

        getMapActivity().getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                addObservationPointToMap();
            }
        });
    }

    private synchronized void addObservationPointToMap() {
        if (getMapActivity() == null) {
            return;
        }

        getMapActivity().newObservationPoint(mCollectionPoints.getLast());
    }
}
