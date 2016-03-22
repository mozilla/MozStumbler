/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.support.v4.content.LocalBroadcastManager;

import org.json.JSONException;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.core.logging.ClientLog;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.DataStorageManager;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.MLSJSONObject;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.StumblerBundle;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.GPSScanner;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.WifiScanner;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.cellscanner.CellInfo;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.cellscanner.CellScanner;
import org.mozilla.mozstumbler.svclocator.ServiceLocator;
import org.mozilla.mozstumbler.svclocator.services.log.ILogger;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Reporter extends BroadcastReceiver implements IReporter {

    private static final ILogger Log = (ILogger) ServiceLocator.getInstance().getService(ILogger.class);
    private static final String LOG_TAG = LoggerUtil.makeLogTag(Reporter.class);

    public static final String ACTION_FLUSH_TO_BUNDLE = AppGlobals.ACTION_NAMESPACE + ".FLUSH";
    public static final String ACTION_NEW_BUNDLE = AppGlobals.ACTION_NAMESPACE + ".NEW_BUNDLE";
    public static final String NEW_BUNDLE_ARG_BUNDLE = "bundle";
    private final Set<String> mUniqueAPs = new HashSet<String>();
    private final Set<String> mUniqueCells = new HashSet<String>();
    StumblerBundle mBundle;
    private boolean mHasGpsFix = false;
    private int mTrackSegment = 0;
    private boolean mIsStarted = false;
    private Context mContext;
    private int mObservationCount = 0;

    public Reporter() {
    }

    public synchronized void startup(Context context) {
        if (mIsStarted) {
            return;
        }

        mContext = context.getApplicationContext();
        mIsStarted = true;
        mBundle = null;
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiScanner.ACTION_WIFIS_SCANNED);
        intentFilter.addAction(CellScanner.ACTION_CELLS_SCANNED);
        intentFilter.addAction(GPSScanner.ACTION_GPS_UPDATED);

        intentFilter.addAction(StumblerServiceIntentActions.SVC_REQ_OBSERVATION_PT);
        intentFilter.addAction(StumblerServiceIntentActions.SVC_REQ_UNIQUE_CELL_COUNT);
        intentFilter.addAction(StumblerServiceIntentActions.SVC_REQ_UNIQUE_WIFI_COUNT);

        intentFilter.addAction(ACTION_FLUSH_TO_BUNDLE);

        LocalBroadcastManager.getInstance(mContext).registerReceiver(this,
                intentFilter);
    }

    public synchronized void shutdown() {
        if (mContext == null) {
            return;
        }

        mIsStarted = false;

        ClientLog.d(LOG_TAG, "shutdown");
        flush();
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(this);
    }

    private void receivedWifiMessage(Intent intent) {
        List<ScanResult> results = intent.getParcelableArrayListExtra(WifiScanner.ACTION_WIFIS_SCANNED_ARG_RESULTS);
        putWifiResults(results);
    }

    private void receivedCellMessage(Intent intent) {
        List<CellInfo> results = intent.getParcelableArrayListExtra(CellScanner.ACTION_CELLS_SCANNED_ARG_CELLS);
        putCellResults(results);
    }

    private void receivedGpsMessage(Intent intent) {
        String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);

        if (subject != null && subject.equals(GPSScanner.SUBJECT_NEW_LOCATION)) {
            Location newPosition = intent.getParcelableExtra(GPSScanner.NEW_LOCATION_ARG_LOCATION);
            // Only create StumblerBundle instances if the position exists
            if (newPosition != null) {
                flush();
                mBundle = new StumblerBundle(newPosition, mTrackSegment);
                mHasGpsFix = true;
            }
        } else if (subject.equals(GPSScanner.SUBJECT_LOCATION_LOST) && mHasGpsFix) {
            flush();

            ClientLog.i(LOG_TAG, "Track segment ended: " + mTrackSegment);
            mTrackSegment++;
            mHasGpsFix = false;
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (action.equals(ACTION_FLUSH_TO_BUNDLE)) {
            flush();
            return;
        } else if (action.equals(WifiScanner.ACTION_WIFIS_SCANNED)) {
            receivedWifiMessage(intent);
        } else if (action.equals(CellScanner.ACTION_CELLS_SCANNED)) {
            receivedCellMessage(intent);
        } else if (action.equals(GPSScanner.ACTION_GPS_UPDATED)) {
            // This is the common case
            receivedGpsMessage(intent);
        } else if (intent.getAction().equals(StumblerServiceIntentActions.SVC_REQ_OBSERVATION_PT)) {
            StumblerService.broadcastCount(mContext,
                    StumblerServiceIntentActions.SVC_RESP_OBSERVATION_PT,
                    getObservationCount());
        } else if (intent.getAction().equals(StumblerServiceIntentActions.SVC_REQ_UNIQUE_CELL_COUNT)) {
            StumblerService.broadcastCount(mContext,
                    StumblerServiceIntentActions.SVC_RESP_UNIQUE_CELL_COUNT,
                    getUniqueCellCount());
        } else if (intent.getAction().equals(StumblerServiceIntentActions.SVC_REQ_UNIQUE_WIFI_COUNT)) {
            StumblerService.broadcastCount(mContext,
                    StumblerServiceIntentActions.SVC_RESP_UNIQUE_WIFI_COUNT,
                    getUniqueAPCount());
        }

        if (mBundle != null &&
                (mBundle.hasMaxWifisPerLocation() || mBundle.hasMaxCellsPerLocation())) {
            // no gps for a while, have too much data, just bundle it
            flush();
        }
    }

    public synchronized Location getGPSLocation() {
        if (mBundle == null) {
            return null;
        }
        return mBundle.getGpsPosition();
    }

    private void putWifiResults(List<ScanResult> results) {
        if (mBundle == null) {
            return;
        }
        for (ScanResult result : results) {
            String key = result.BSSID;
            mBundle.addWifiData(key, result);
        }
    }

    private void putCellResults(List<CellInfo> cells) {
        if (mBundle == null) {
            return;
        }
        for (CellInfo result : cells) {
            String key = result.getCellIdentity();
            mBundle.addCellData(key, result);
        }
    }

    private synchronized void flush() {
        if (mBundle == null) {
            return;
        }

        MLSJSONObject geoSubmitJSON;
        try {
            geoSubmitJSON = mBundle.toMLSGeosubmit();
        } catch (JSONException e) {
            ClientLog.w(LOG_TAG, "Failed to convert bundle to JSON: " + e);
            mBundle = null;
            return;
        }

        if (geoSubmitJSON.radioCount() > 0) {
            DataStorageManager.getInstance().insert(geoSubmitJSON);
            mObservationCount++;
            mUniqueAPs.addAll(mBundle.getUnmodifiableWifiData().keySet());
            mUniqueCells.addAll(mBundle.getUnmodifiableCellData().keySet());
        }

        Intent i = new Intent(ACTION_NEW_BUNDLE);
        i.putExtra(NEW_BUNDLE_ARG_BUNDLE, mBundle);
        i.putExtra(AppGlobals.ACTION_ARG_TIME, System.currentTimeMillis());
        LocalBroadcastManager.getInstance(mContext).sendBroadcastSync(i);

        mBundle = null;
    }

    private int getObservationCount() {
        // this is only updated during flush() which is only called from onReceive
        return mObservationCount;
    }

    private int getUniqueAPCount() {
        // this is only updated during flush() which is only called from onReceive
        return mUniqueAPs.size();
    }

    private int getUniqueCellCount() {
        // this is only updated during flush() which is only called from onReceive
        return mUniqueCells.size();
    }
}
