package org.mozilla.mozstumbler.client;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.mozilla.mozstumbler.service.utils.AbstractCommunicator;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.DataStorageContract;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.DataStorageManager;
import org.mozilla.mozstumbler.service.uploadthread.AsyncUploader;
import org.mozilla.mozstumbler.service.utils.DateTimeUtils;
import org.mozilla.mozstumbler.R;

import java.io.IOException;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

public class UploadReportsDialog extends DialogFragment
        implements AsyncUploader.AsyncUploaderListener {
    private static final String LOG_TAG = AppGlobals.LOG_PREFIX + UploadReportsDialog.class.getSimpleName();

    private TextView mLastUpdateTimeView;
    private TextView mObservationsSentView;
    private TextView mTotalDataSentView;
    private TextView mCellsSentView;
    private TextView mWifisSentView;

    private TextView mQueuedObservationsView;
    private TextView mQueuedCellsView;
    private TextView mQueuedWifisView;
    private TextView mQueuedDataView;

    private View mUploadButton;
    private View mProgressbarView;
    private boolean hasQueuedObservations;
    private AsyncUploader mUploader;
    private final Timer mUpdateTimer = new Timer();

    private void updateUiThread() {
        this.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                update();
            }
        });
    }

    @Override
    public void onUploadComplete(AbstractCommunicator.SyncSummary result) {
        updateUiThread();
    }

    @Override
    public void onUploadProgress() {
        updateUiThread();
    }

    public UploadReportsDialog() {
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        final LayoutInflater inflater = getActivity().getLayoutInflater();
        final ViewGroup rootView = (ViewGroup) inflater.inflate(R.layout.dialog_upload_observations, null);
        mLastUpdateTimeView = (TextView) rootView.findViewById(R.id.last_upload_time_value);
        mObservationsSentView = (TextView) rootView.findViewById(R.id.observations_sent_value);
        mCellsSentView = (TextView) rootView.findViewById(R.id.cells_sent_value);
        mWifisSentView = (TextView) rootView.findViewById(R.id.wifis_sent_value);
        mQueuedObservationsView = (TextView) rootView.findViewById(R.id.observations_queued_value);
        mQueuedCellsView = (TextView) rootView.findViewById(R.id.cells_queued_value);
        mQueuedWifisView = (TextView) rootView.findViewById(R.id.wifis_queued_value);
        mProgressbarView = rootView.findViewById(R.id.progress);
        mUploadButton = rootView.findViewById(R.id.upload_observations_button);
        mTotalDataSentView = (TextView) rootView.findViewById(R.id.data_kb_sent_value);
        mQueuedDataView = (TextView) rootView.findViewById(R.id.data_kb_queued_value);

        mUploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AsyncUploader.UploadSettings settings =
                        new AsyncUploader.UploadSettings(Prefs.getInstance().getWifiScanAlways(), Prefs.getInstance().getUseWifiOnly());
                mUploader = new AsyncUploader(settings, UploadReportsDialog.this);
                mUploader.setNickname(Prefs.getInstance().getNickname());
                mUploader.execute();
                updateProgressbarStatus();
            }
        });

        // If already uploading check the stats in a few seconds
        if (AsyncUploader.isUploading()) {
            mUpdateTimer.scheduleAtFixedRate(new TimerTask() {
                public void run() {
                    updateUiThread();
                }
            }, 3000, 3000);
        }

        builder.setView(rootView)
                .setTitle(R.string.upload_observations_dialog_title)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        UploadReportsDialog.this.getDialog().cancel();
                    }
                });
        return builder.create();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        hasQueuedObservations = false;
    }

    void update() {
        if (!AsyncUploader.isUploading()) {
            mUpdateTimer.cancel();
            mUpdateTimer.purge();
        }

        updateSyncedStats();
        updateQueuedStats();
        updateProgressbarStatus();
    }

    @Override
    public void onStart() {
        super.onStart();
        update();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        mUpdateTimer.cancel();
        mUpdateTimer.purge();

        if (mUploader != null) {
            mUploader.clearListener();
        }

        mLastUpdateTimeView = null;
        mObservationsSentView = null;
        mCellsSentView = null;
        mWifisSentView = null;
        mQueuedObservationsView = null;
        mQueuedCellsView = null;
        mQueuedWifisView = null;
        mProgressbarView = null;
    }

    void updateSyncedStats() {
        if (mLastUpdateTimeView == null) {
            return;
        }

        mLastUpdateTimeView.setText("");
        mObservationsSentView.setText("");
        mCellsSentView.setText("");
        mWifisSentView.setText("");

        try {
            Properties props = DataStorageManager.getInstance().readSyncStats();
            long lastUploadTime = Long.parseLong(props.getProperty(DataStorageContract.Stats.KEY_LAST_UPLOAD_TIME, "0"));
            String value = (lastUploadTime > 0)? DateTimeUtils.formatTimeForLocale(lastUploadTime) : "-";
            mLastUpdateTimeView.setText(value);
            value = props.getProperty(DataStorageContract.Stats.KEY_OBSERVATIONS_SENT, "0");
            mObservationsSentView.setText(value);
            value = props.getProperty(DataStorageContract.Stats.KEY_CELLS_SENT, "0");
            mCellsSentView.setText(String.valueOf(value));
            value = props.getProperty(DataStorageContract.Stats.KEY_WIFIS_SENT, "0");
            mWifisSentView.setText(String.valueOf(value));
            value = props.getProperty(DataStorageContract.Stats.KEY_BYTES_SENT, "0");
            float kilobytes = Long.parseLong(value) / 1000.0f;
            mTotalDataSentView.setText(String.valueOf(kilobytes));
        }
        catch (IOException ex) {
            Log.e(LOG_TAG, "Exception in updateSyncedStats()", ex);
        }
    }

    void updateProgressbarStatus() {
        if (mProgressbarView == null) {
            return;
        }
        boolean syncActive = AsyncUploader.isUploading();
        mProgressbarView.setVisibility(syncActive ? View.VISIBLE : View.INVISIBLE);

        boolean uploadButtonActive = hasQueuedObservations && !syncActive;
        mUploadButton.setEnabled(uploadButtonActive);
    }

    void updateQueuedStats() {
        DataStorageManager.QueuedCounts q = DataStorageManager.getInstance().getQueuedCounts();
        mQueuedObservationsView.setText(String.valueOf(q.mReportCount));
        mQueuedCellsView.setText(String.valueOf(q.mCellCount));
        mQueuedWifisView.setText(String.valueOf(q.mWifiCount));
        mQueuedDataView.setText(String.valueOf(q.mBytes / 1000.0));
        hasQueuedObservations = q.mReportCount != 0;
        updateProgressbarStatus();
    }
}
