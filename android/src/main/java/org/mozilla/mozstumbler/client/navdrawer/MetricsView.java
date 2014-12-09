/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.navdrawer;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.ClientPrefs;
import org.mozilla.mozstumbler.client.DateTimeUtils;
import org.mozilla.mozstumbler.client.subactivities.PreferencesScreen;
import org.mozilla.mozstumbler.client.util.NotificationUtil;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.DataStorageContract;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.DataStorageManager;
import org.mozilla.mozstumbler.service.uploadthread.AsyncUploadParam;
import org.mozilla.mozstumbler.service.uploadthread.AsyncUploader;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Properties;

public class MetricsView {

    public interface IMapLayerToggleListener {
        public void setShowMLS(boolean isOn);
    }

    private static final String LOG_TAG = AppGlobals.makeLogTag(MetricsView.class.getSimpleName());

    private final TextView
            mLastUpdateTimeView,
            mAllTimeObservationsSentView,
            mQueuedObservationsView,
            mThisSessionObservationsView,
            mThisSessionUniqueCellsView,
            mThisSessionUniqueAPsView;

    private final CheckBox mOnMapShowMLS;

    private WeakReference<IMapLayerToggleListener> mMapLayerToggleListener = new WeakReference<IMapLayerToggleListener>(null);

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final long FREQ_UPDATE_UPLOADTIME = 10 * 1000;

    private final ImageButton mUploadButton;
    private final ImageButton mSettingsButton;
    private final RelativeLayout mButtonsContainer;
    private final View mView;
    private long mTotalBytesUploadedThisSession_lastDisplayed = -1;
    private long mLastUploadTime = 0;
    private final String mObservationAndSize = "%1$d  %2$s";

    private boolean mHasQueuedObservations;

    private static int sThisSessionObservationsCount;
    private static int sThisSessionUniqueWifiCount;
    private static int sThisSessionUniqueCellCount;

    public MetricsView(View view) {
        mView = view;

        mOnMapShowMLS = (CheckBox) mView.findViewById(R.id.checkBox_show_mls);
        mOnMapShowMLS.setVisibility(View.GONE);
        mOnMapShowMLS.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                ClientPrefs.getInstance().setOnMapShowMLS(mOnMapShowMLS.isChecked());
                if (mMapLayerToggleListener.get() != null) {
                    mMapLayerToggleListener.get().setShowMLS(mOnMapShowMLS.isChecked());
                }
            }
        });

        mLastUpdateTimeView = (TextView) mView.findViewById(R.id.last_upload_time_value);
        mAllTimeObservationsSentView = (TextView) mView.findViewById(R.id.observations_sent_value);
        mQueuedObservationsView = (TextView) mView.findViewById(R.id.observations_queued_value);
        mThisSessionObservationsView = (TextView) mView.findViewById(R.id.this_session_observations_value);
        mThisSessionUniqueCellsView = (TextView) mView.findViewById(R.id.cells_unique_value);
        mThisSessionUniqueAPsView = (TextView) mView.findViewById(R.id.wifis_unique_value);

        mUploadButton = (ImageButton) mView.findViewById(R.id.upload_observations_button);
        mUploadButton.setEnabled(false);
        mUploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mHasQueuedObservations) {
                    return;
                }

                // @TODO: Emit a signal here to initiate an upload
                // and have it handled by MainApp
                AsyncUploader uploader = new AsyncUploader();
                AsyncUploadParam param = new AsyncUploadParam(false /* useWifiOnly */,
                    Prefs.getInstance().getNickname(),
                    Prefs.getInstance().getEmail());
                uploader.execute(param);

                setUploadButtonToSyncing(true);
            }
        });

        mSettingsButton = (ImageButton) mView.findViewById(R.id.metrics_settings_button);
        mSettingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                view.getContext().startActivity(new Intent(view.getContext(), PreferencesScreen.class));
            }
        });

        // Remove click listener of the Drawer buttons container to avoid to get it triggered on the Map view
        mButtonsContainer = (RelativeLayout) mView.findViewById(R.id.metrics_buttons_container);
        mButtonsContainer.setOnClickListener(null);

        mHandler.postDelayed(mUpdateLastUploadedLabel, FREQ_UPDATE_UPLOADTIME);
    }

    public void setMapLayerToggleListener(IMapLayerToggleListener listener) {
        mMapLayerToggleListener = new WeakReference<IMapLayerToggleListener>(listener);
        mOnMapShowMLS.setChecked(ClientPrefs.getInstance().getOnMapShowMLS());
    }


    private boolean buttonIsSyncIcon;
    private void updateUploadButtonEnabled() {
        if (buttonIsSyncIcon) {
            mUploadButton.setEnabled(false);
        } else {
            mUploadButton.setEnabled(mHasQueuedObservations);
        }
    }

    private void setUploadButtonToSyncing(boolean isSyncing) {
        if (isSyncing) {
            mUploadButton.setImageResource(android.R.drawable.ic_popup_sync);
        } else {
            mUploadButton.setImageResource(R.drawable.ic_action_upload);
        }

        buttonIsSyncIcon = isSyncing;
        updateUploadButtonEnabled();
    }

    public void setUploadState(boolean isUploadingObservations) {
        updateUiThread();
    }

    private void updateUiThread() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                update();
            }
        });
    }

    public void update() {
        DataStorageManager dm = DataStorageManager.getInstance();
        if (dm == null) {
            return;
        }

        updateQueuedStats(dm);
        updateSentStats(dm);
        updateThisSessionStats();

        setUploadButtonToSyncing(AsyncUploader.isUploading.get());
    }

    public void onOpened() {
        if (ClientPrefs.getInstance().isOptionEnabledToShowMLSOnMap()) {
            mOnMapShowMLS.setVisibility(View.VISIBLE);
        } else {
            mOnMapShowMLS.setVisibility(View.GONE);
        }
        update();
    }

    private void updateThisSessionStats() {
        mThisSessionUniqueCellsView.setText(String.valueOf(sThisSessionUniqueCellCount));
        mThisSessionUniqueAPsView.setText(String.valueOf(sThisSessionUniqueWifiCount));

        if (sThisSessionObservationsCount < 1) {
            mThisSessionObservationsView.setText("0");
            return;
        }

        long bytesUploadedThisSession = AsyncUploader.sTotalBytesUploadedThisSession.get();
        String val = String.format(mObservationAndSize, sThisSessionObservationsCount, formatKb(bytesUploadedThisSession));
        mThisSessionObservationsView.setText(val);
    }

    String formatKb(long bytes) {
        float kb = bytes / 1000.0f;
        if (kb < 0.1) {
            return ""; // don't show 0.0 for size.
        }
        return "(" + (Math.round(kb * 10.0f) / 10.0f) + " KB)";
    }

    private final Runnable mUpdateLastUploadedLabel = new Runnable() {
        @Override
        public void run() {
            updateLastUploadedLabel();
            mHandler.postDelayed(mUpdateLastUploadedLabel, FREQ_UPDATE_UPLOADTIME);
        }
    };

    private void updateLastUploadedLabel() {
        Context context = mView.getContext();
        String value = context.getString(R.string.metrics_observations_last_upload_time_never);
        if (mLastUploadTime > 0) {
            value = DateTimeUtils.prettyPrintTimeDiff(mLastUploadTime, context.getResources());
        }
        mLastUpdateTimeView.setText(value);
    }

    private void updateSentStats(DataStorageManager dataStorageManager) {

        final long bytesUploadedThisSession = AsyncUploader.sTotalBytesUploadedThisSession.get();

        if (mTotalBytesUploadedThisSession_lastDisplayed == bytesUploadedThisSession) {
            // no need to update
            return;
        }
        mTotalBytesUploadedThisSession_lastDisplayed = bytesUploadedThisSession;

        try {
            Properties props = dataStorageManager.readSyncStats();
            String sent = props.getProperty(DataStorageContract.Stats.KEY_OBSERVATIONS_SENT, "0");
            String bytes = props.getProperty(DataStorageContract.Stats.KEY_BYTES_SENT, "0");
            String value = String.format(mObservationAndSize, Integer.parseInt(sent), formatKb(Long.parseLong(bytes)));
            mAllTimeObservationsSentView.setText(value);

            mLastUploadTime = Long.parseLong(props.getProperty(DataStorageContract.Stats.KEY_LAST_UPLOAD_TIME, "0"));
            updateLastUploadedLabel();
        } catch (IOException ex) {
            Log.e(LOG_TAG, "Exception in updateSentStats()", ex);
        }
    }

    private void updateQueuedStats(DataStorageManager dataStorageManager) {
        DataStorageManager.QueuedCounts q = dataStorageManager.getQueuedCounts();
        String val = String.format(mObservationAndSize, q.mReportCount, formatKb(q.mBytes));
        mQueuedObservationsView.setText(val);

        mHasQueuedObservations = q.mReportCount > 0;
        updateUploadButtonEnabled();
    }

    public void setObservationCount(int observations, int cells, int wifis, boolean isActive) {
        sThisSessionObservationsCount = observations;
        sThisSessionUniqueCellCount = cells;
        sThisSessionUniqueWifiCount = wifis;

        NotificationUtil util = new NotificationUtil(mView.getContext().getApplicationContext());
        util.updateMetrics(observations, cells, wifis, mLastUploadTime, isActive);
    }
}
