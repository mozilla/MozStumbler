package org.mozilla.mozstumbler.client.mapview;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;
import org.mozilla.mozstumbler.client.ClientStumblerService;
import org.mozilla.mozstumbler.client.MainApp;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.GPSScanner;
import org.osmdroid.util.GeoPoint;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

class ObservedLocationsReceiver extends BroadcastReceiver {
    ArrayList<GeoPoint> mLocations = new ArrayList<GeoPoint>();
    private WeakReference<MapActivity> mMapActivity = new WeakReference<MapActivity>(null);
    private LinkedList<ObservationPoint> mCollectionPoints = new LinkedList<ObservationPoint>();
    private LinkedList<ObservationPoint> mQueuedForMLS = new LinkedList<ObservationPoint>();
    private final int MAX_QUEUED_MLS_POINTS_TO_FETCH = 10;
    private final long FREQ_FETCH_MLS_MS = 5 * 1000;

    LinkedList<ObservationPoint> getPoints() {
        return mCollectionPoints;
    }

    private ObservedLocationsReceiver() {
        mHandler.postDelayed(mFetchMLSRunnable, FREQ_FETCH_MLS_MS);
    }

    private static ObservedLocationsReceiver sInstance;

    void removeMapActivity() {
        setMapActivity(null);
    }

    static ObservedLocationsReceiver getInstance(MapActivity mapActivity) {
        if (sInstance == null) {
            sInstance = new ObservedLocationsReceiver();
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(GPSScanner.ACTION_GPS_UPDATED);
            LocalBroadcastManager.getInstance(mapActivity.getApplicationContext()).registerReceiver(sInstance, intentFilter);
        }
        sInstance.setMapActivity(mapActivity);
        return sInstance;
    }

    Runnable mFetchMLSRunnable = new Runnable() {
        @Override
        public void run() {
            mHandler.postDelayed(mFetchMLSRunnable, FREQ_FETCH_MLS_MS);
            if (mQueuedForMLS.size() < 1) {
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
                    synchronized (this) {
                        if (getMapActivity() != null) {
                            getMapActivity().newMLSPoint(obs);
                        }
                    }
                    li.remove();
                }
            }
        }
    };

    Handler mHandler = new Handler(Looper.getMainLooper());

    private synchronized void setMapActivity(MapActivity m) {
        mMapActivity = new WeakReference<MapActivity>(m);
    }

    private synchronized MapActivity getMapActivity() {
        return mMapActivity.get();
    }


    @Override
    public synchronized void onReceive(Context context, Intent i) {
        final Intent intent = i;
        final String action = intent.getAction();
        final String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);
        if (!action.equals(GPSScanner.ACTION_GPS_UPDATED)) {
            assert (action.equals(GPSScanner.ACTION_GPS_UPDATED));
            return;
        }

        if (subject.equals(GPSScanner.SUBJECT_NEW_LOCATION)) {
            Location newPosition = intent.getParcelableExtra(GPSScanner.NEW_LOCATION_ARG_LOCATION);
            if (newPosition != null) {
                mLocations.add(new GeoPoint(newPosition));
            }
        }

        if (getMapActivity() == null) {
            return;
        }
        getMapActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                synchronized (this) {
                    if (getMapActivity() == null) {
                        return;
                    }

                    getMapActivity().updateGPSInfo(intent);

                    final ClientStumblerService service = ((MainApp) getMapActivity().getApplication()).getService();
                    final Location location = service.getLocation();

                    if (!subject.equals(GPSScanner.SUBJECT_NEW_LOCATION) ||
                            !getMapActivity().isValidLocation(location)) {
                        return;
                    }

                    if (mCollectionPoints.size() > 0) {
                        final ObservationPoint last = mCollectionPoints.getLast();
                        last.setMLSQuery(service.getLastReportedBundle());
                        mQueuedForMLS.addFirst(last);
                    }

                    mCollectionPoints.add(new ObservationPoint(new GeoPoint(location)));
                    getMapActivity().newObservationPoint(mCollectionPoints.getLast());
                }
            }
        });
    }
}
