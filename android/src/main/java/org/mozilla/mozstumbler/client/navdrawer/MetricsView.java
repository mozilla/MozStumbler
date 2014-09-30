/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.navdrawer;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.TextView;

import com.ocpsoft.pretty.time.PrettyTime;

import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.ClientPrefs;
import org.mozilla.mozstumbler.client.ClientStumblerService;
import org.mozilla.mozstumbler.client.MainApp;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.DataStorageContract;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.DataStorageManager;
import org.mozilla.mozstumbler.service.uploadthread.AsyncUploadParam;
import org.mozilla.mozstumbler.service.uploadthread.AsyncUploader;
import org.mozilla.mozstumbler.service.uploadthread.AsyncUploaderListener;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

public class MetricsView implements AsyncUploaderListener {

    public interface IMapLayerToggleListener {
        public void setShowMLS(boolean isOn);
        public void setShowObservationType(boolean isOn);
    }

    private static final String LOG_TAG = AppGlobals.LOG_PREFIX + MetricsView.class.getSimpleName();

    private final TextView
            mLastUpdateTimeView,
            mAllTimeObservationsSentView,
            mAllTimeCellsSentView,
            mAllTimeWifisSentView,
            mQueuedObservationsView,
            mQueuedCellsView,
            mQueuedWifisView,
            mThisSessionObservationsView,
            mThisSessionCellsView,
            mThisSessionWifisView;

    private final CheckBox mOnMapShowMLS;
    private final CheckBox mOnMapShowObservationType;

    private WeakReference<IMapLayerToggleListener> mMapLayerToggleListener;

    private ImageButton mUploadButton;
    private final View mView;
    private long mTotalBytesUploadedThisSession_lastDisplayed;
    private final String mObservationAndSize = "%1$d  %2$s";

    private boolean mHasQueuedObservations;
    private Timer mUpdateTimer;

    public MetricsView(View view) {
        mView = view;

        mOnMapShowMLS = (CheckBox) mView.findViewById(R.id.checkBox_show_mls);
        mOnMapShowObservationType = (CheckBox) mView.findViewById(R.id.checkBox_show_observation_type);

        mOnMapShowMLS.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                ClientPrefs.getInstance().setOnMapShowMLS(mOnMapShowMLS.isChecked());
                if (mMapLayerToggleListener.get() != null) {
                    mMapLayerToggleListener.get().setShowMLS(mOnMapShowMLS.isChecked());
                }
            }
        });

        mOnMapShowObservationType.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                ClientPrefs.getInstance().setOnMapShowObservationType(mOnMapShowObservationType.isChecked());
                if (mMapLayerToggleListener.get() != null) {
                    mMapLayerToggleListener.get().setShowObservationType(mOnMapShowObservationType.isChecked());
                }
            }
        });

        mLastUpdateTimeView = (TextView) mView.findViewById(R.id.last_upload_time_value);
        mAllTimeObservationsSentView = (TextView) mView.findViewById(R.id.observations_sent_value);
        mAllTimeCellsSentView = (TextView) mView.findViewById(R.id.cells_sent_value);
        mAllTimeWifisSentView = (TextView) mView.findViewById(R.id.wifis_sent_value);
        mQueuedObservationsView = (TextView) mView.findViewById(R.id.observations_queued_value);
        mQueuedCellsView = (TextView) mView.findViewById(R.id.cells_queued_value);
        mQueuedWifisView = (TextView) mView.findViewById(R.id.wifis_queued_value);
        mThisSessionObservationsView = (TextView) mView.findViewById(R.id.this_session_observations_value);
        mThisSessionCellsView = (TextView) mView.findViewById(R.id.this_session_cells_value);
        mThisSessionWifisView = (TextView) mView.findViewById(R.id.this_session_wifis_value);

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
                boolean useWifiOnly = false;
                 AsyncUploader uploader = new AsyncUploader();
                AsyncUploadParam param = new AsyncUploadParam(useWifiOnly,
                    MetricsView.this,
                    Prefs.getInstance().getNickname(),
                    Prefs.getInstance().getEmail());
                uploader.execute(param);

                setUploadButtonToSyncing(true);
            }
        });
    }

    public void setMapLayerToggleListener(IMapLayerToggleListener listener) {
        mMapLayerToggleListener = new WeakReference<IMapLayerToggleListener>(listener);
        mOnMapShowMLS.setChecked(ClientPrefs.getInstance().getOnMapShowMLS());
        mOnMapShowObservationType.setChecked(ClientPrefs.getInstance().getOnMapShowObservationType());
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
            mUploadButton.setImageResource(R.drawable.ic_action_refresh);
        } else {
            mUploadButton.setImageResource(R.drawable.ic_action_upload);
        }

        buttonIsSyncIcon = isSyncing;
        updateUploadButtonEnabled();
    }

    @Override
    public void onUploadProgress(boolean isUploading) {
        AsyncUploader.isUploading.set(isUploading);
        updateUiThread();
    }

    private void updateUiThread() {
        Handler h = new Handler(Looper.getMainLooper());
        h.post(new Runnable() {
            @Override
            public void run() {
                update();
            }
        });
    }

    public void update() {
        updateQueuedStats();
        updateSentStats();
        updateThisSessionStats();

        // If already uploading check the stats in a few seconds
        boolean isUpdating = AsyncUploader.isUploading.get();
        setUploadButtonToSyncing(isUpdating);

        if (isUpdating) {
            if (mUpdateTimer == null) {
                mUpdateTimer = new Timer();
                mUpdateTimer.scheduleAtFixedRate(new TimerTask() {
                    public void run() {
                        updateUiThread();
                    }
                }, 1000, 1000);
            }
        } else {
            if (mUpdateTimer != null) {
                mUpdateTimer.cancel();
                mUpdateTimer.purge();
                mUpdateTimer = null;
            }
        }


    }

    private void updateThisSessionStats() {
        mThisSessionCellsView.setText("");
        mThisSessionWifisView.setText("");

        MainApp app = (MainApp)mView.getContext().getApplicationContext();
        ClientStumblerService service = app.getService();
        if (service == null) {
            return;
        }

        mThisSessionWifisView.setText(String.valueOf(service.getAPCount()));
        mThisSessionCellsView.setText(String.valueOf(service.getCellInfoCount()));

        CharSequence obs = mThisSessionObservationsView.getText();
        if (obs == null || obs.length() < 1) {
            mThisSessionObservationsView.setText("0");
        }
    }

    String formatKb(long bytes) {
        float kb = bytes / 1000.0f;
        if (kb < 0.1) {
            return ""; // don't show 0.0 for size.
        }
        return "(" + (Math.round(kb * 10.0f) / 10.0f) + " KB)";
    }

    private void updateSentStats() {
        final long bytesUploadedThisSession = AsyncUploader.sTotalBytesUploadedThisSession.get();

        if (mTotalBytesUploadedThisSession_lastDisplayed > 0 &&
            mTotalBytesUploadedThisSession_lastDisplayed == bytesUploadedThisSession) {
            // no need to update
            return;
        }
        mTotalBytesUploadedThisSession_lastDisplayed = bytesUploadedThisSession;

        try {
            Properties props = DataStorageManager.getInstance().readSyncStats();
            String value;
            value = props.getProperty(DataStorageContract.Stats.KEY_CELLS_SENT, "0");
            mAllTimeCellsSentView.setText(String.valueOf(value));
            value = props.getProperty(DataStorageContract.Stats.KEY_WIFIS_SENT, "0");
            mAllTimeWifisSentView.setText(String.valueOf(value));
            value = props.getProperty(DataStorageContract.Stats.KEY_OBSERVATIONS_SENT, "0");
            String bytes = props.getProperty(DataStorageContract.Stats.KEY_BYTES_SENT, "0");
            value = String.format(mObservationAndSize, Integer.parseInt(value), formatKb(Long.parseLong(bytes)));
            mAllTimeObservationsSentView.setText(value);

            value = "never";
            final long lastUploadTime = Long.parseLong(props.getProperty(DataStorageContract.Stats.KEY_LAST_UPLOAD_TIME, "0"));
            if (lastUploadTime > 0) {
                value = new PrettyTime().format(new Date(lastUploadTime));
            }
            mLastUpdateTimeView.setText(value);
        }
        catch (IOException ex) {
            Log.e(LOG_TAG, "Exception in updateSyncedStats()", ex);
        }
    }

    private void updateQueuedStats() {
        DataStorageManager.QueuedCounts q = DataStorageManager.getInstance().getQueuedCounts();
        mQueuedCellsView.setText(String.valueOf(q.mCellCount));
        mQueuedWifisView.setText(String.valueOf(q.mWifiCount));

        String val = String.format(mObservationAndSize, q.mReportCount, formatKb(q.mBytes));
        mQueuedObservationsView.setText(val);

        mHasQueuedObservations = q.mReportCount > 0;
        updateUploadButtonEnabled();
    }

    public void setObservationCount(int count) {
        long bytesUploadedThisSession = AsyncUploader.sTotalBytesUploadedThisSession.get();
        String val = String.format(mObservationAndSize, count, formatKb(bytesUploadedThisSession));
        mThisSessionObservationsView.setText(val);
    }
}
