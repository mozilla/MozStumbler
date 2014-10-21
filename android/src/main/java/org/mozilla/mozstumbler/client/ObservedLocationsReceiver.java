/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;

import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.mozstumbler.client.mapview.MapFragment;
import org.mozilla.mozstumbler.client.mapview.ObservationPoint;
import org.mozilla.mozstumbler.service.core.logging.Log;
import org.mozilla.mozstumbler.service.stumblerthread.Reporter;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.StumblerBundle;
import org.osmdroid.util.GeoPoint;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedList;

public class ObservedLocationsReceiver extends BroadcastReceiver {

    public interface ICountObserver {
        public void observedLocationCountIncrement();
    }

    private static final String LOG_TAG = ObservedLocationsReceiver.class.getSimpleName();
    private WeakReference<MapFragment> mMapActivity = new WeakReference<MapFragment>(null);
    private final LinkedList<ObservationPoint> mCollectionPoints = new LinkedList<ObservationPoint>();
    private final LinkedList<ObservationPoint> mQueuedForMLS = new LinkedList<ObservationPoint>();
    private final int MAX_QUEUED_MLS_POINTS_TO_FETCH = 10;
    private final long FREQ_FETCH_MLS_MS = 5 * 1000;
    private WeakReference<ICountObserver> mCountObserver = new WeakReference<ICountObserver>(null);
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    // Upper bound on the size of the linked lists of points, for memory and performance safety.
    // On older devices, store fewer observations
    private final int MAX_SIZE_OF_POINT_LISTS = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)?
                                                5000 : 2500;

    private ObservedLocationsReceiver() {
        mHandler.postDelayed(mFetchMLSRunnable, FREQ_FETCH_MLS_MS);
    }

    private static ObservedLocationsReceiver sInstance;

    public static void createGlobalInstance(Context context, ICountObserver countObserver) {
        sInstance = new ObservedLocationsReceiver();
        sInstance.mCountObserver = new WeakReference<ICountObserver>(countObserver);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Reporter.ACTION_NEW_BUNDLE);
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
    public synchronized void setMapActivity(MapFragment m) {
        mMapActivity = new WeakReference<MapFragment>(m);
    }

    // Must call when map activity stopped, to clear the reference to the map activity object
    public void removeMapActivity() {
        setMapActivity(null);
    }

    private synchronized MapFragment getMapActivity() {
        return mMapActivity.get();
    }

    @Override
    public synchronized void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (!action.equals(Reporter.ACTION_NEW_BUNDLE)) {
            return;
        }

        final StumblerBundle bundle = intent.getParcelableExtra(Reporter.NEW_BUNDLE_ARG_BUNDLE);
        if (bundle == null) {
            return;
        }

        Location position = bundle.getGpsPosition();
        if (position == null) {
            return;
        }
        ObservationPoint observation = new ObservationPoint(new GeoPoint(position));

        try {
            JSONObject jsonBundle = bundle.toMLSJSON();
            observation.setCounts(jsonBundle);

            boolean getInfoForMLS = ClientPrefs.getInstance().isOptionEnabledToShowMLSOnMap();
            if (getInfoForMLS) {
                observation.setMLSQuery(jsonBundle);

                if (mQueuedForMLS.size() < MAX_SIZE_OF_POINT_LISTS) {
                    mQueuedForMLS.addFirst(observation);
                }
            }
        } catch (JSONException e) {
            Log.w(LOG_TAG, "Failed to convert bundle to JSON: " + e);
        }

        // Notify main app of observation
        if (mCountObserver.get() != null) {
            mCountObserver.get().observedLocationCountIncrement();
        }

        while (mCollectionPoints.size() > MAX_SIZE_OF_POINT_LISTS) {
            mCollectionPoints.removeFirst();
        }

        if (mCollectionPoints.size() > 0) {
            observation.mHeading = observation.pointGPS.bearingTo(mCollectionPoints.getLast().pointGPS);
        }
        mCollectionPoints.addLast(observation);

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
