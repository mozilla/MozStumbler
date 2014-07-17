package org.mozilla.mozstumbler.client;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SyncResult;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.mozilla.mozstumbler.service.SharedConstants;
import org.mozilla.mozstumbler.service.sync.AsyncUploader;
import org.mozilla.mozstumbler.service.utils.DateTimeUtils;
import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.service.datahandling.DatabaseContract;

public class UploadReportsDialog extends DialogFragment
        implements AsyncUploader.AsyncUploaderListener {
    private static final String LOGTAG = UploadReportsDialog.class.getName();

    private TextView mLastUpdateTimeView;
    private TextView mObservationsSentView;
    private TextView mCellsSentView;
    private TextView mWifisSentView;
    private TextView mObservationsQueuedView;
    private TextView mCellsQueuedView;
    private TextView mWifisQueuedView;
    private View mUploadButton;
    private View mProgressbarView;
    private boolean hasQueuedObservations;
    private AsyncUploader mUploader;

    @Override
    public void onUploadComplete(SyncResult result) {
        this.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                update();
            }
        });
    }

    @Override
    public void onUploadProgress() {
        this.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                update();
            }
        });
    }

    public UploadReportsDialog() {
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();

        ViewGroup rootView = (ViewGroup) inflater.inflate(R.layout.dialog_upload_observations, null);
        mLastUpdateTimeView = (TextView) rootView.findViewById(R.id.last_upload_time_value);
        mObservationsSentView = (TextView) rootView.findViewById(R.id.observations_sent_value);
        mCellsSentView = (TextView) rootView.findViewById(R.id.cells_sent_value);
        mWifisSentView = (TextView) rootView.findViewById(R.id.wifis_sent_value);
        mObservationsQueuedView = (TextView) rootView.findViewById(R.id.observations_queued_value);
        mCellsQueuedView = (TextView) rootView.findViewById(R.id.cells_queued_value);
        mWifisQueuedView = (TextView) rootView.findViewById(R.id.wifis_queued_value);
        mProgressbarView = rootView.findViewById(R.id.progress);
        mUploadButton = rootView.findViewById(R.id.upload_observations_button);

        mUploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mUploader = new AsyncUploader(UploadReportsDialog.this);
                mUploader.execute();
                updateProgressbarStatus();
            }
        });

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
        if (mUploader != null)
            mUploader.clearListener();

        mLastUpdateTimeView = null;
        mObservationsSentView = null;
        mCellsSentView = null;
        mWifisSentView = null;
        mObservationsQueuedView = null;
        mCellsQueuedView = null;
        mWifisQueuedView = null;
        mProgressbarView = null;
    }

    void updateSyncedStats() {
        if (mLastUpdateTimeView == null) {
            return;
        }

        Cursor cursor = SharedConstants.stumblerContentResolver.query(DatabaseContract.Stats.CONTENT_URI, null, null, null, null);
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    String key = cursor.getString(cursor.getColumnIndex(DatabaseContract.Stats.KEY));
                    String value = cursor.getString(cursor.getColumnIndex(DatabaseContract.Stats.VALUE));

                    if (DatabaseContract.Stats.KEY_LAST_UPLOAD_TIME.equals(key)) {
                        long lastUploadTime = Long.valueOf(value);
                        String lastUploadTimeString = (lastUploadTime > 0)
                                ? DateTimeUtils.formatTimeForLocale(lastUploadTime)
                                : "-";
                        mLastUpdateTimeView.setText(lastUploadTimeString);
                    } else if (DatabaseContract.Stats.KEY_OBSERVATIONS_SENT.equals(key)) {
                        mObservationsSentView.setText(value);
                    } else if (DatabaseContract.Stats.KEY_CELLS_SENT.equals(key)) {
                        mCellsSentView.setText(String.valueOf(value));
                    } else if (DatabaseContract.Stats.KEY_WIFIS_SENT.equals(key)) {
                        mWifisSentView.setText(String.valueOf(value));
                    }
                }
            } finally {
                cursor.close();
            }

        } else {
            mLastUpdateTimeView.setText("");
            mObservationsSentView.setText("");
            mCellsSentView.setText("");
            mWifisSentView.setText("");
        }
    }

    void updateProgressbarStatus() {
        if (mProgressbarView == null) {
            return;
        }
        boolean syncActive = AsyncUploader.sIsUploading;
        mProgressbarView.setVisibility(syncActive ? View.VISIBLE : View.INVISIBLE);

        boolean uploadButtonActive = hasQueuedObservations && !syncActive;
        mUploadButton.setEnabled(uploadButtonActive);
    }

    void updateQueuedStats() {
        Cursor cursor = SharedConstants.stumblerContentResolver.query(DatabaseContract.Reports.CONTENT_URI_SUMMARY, null, null, null, null);

        if (mLastUpdateTimeView == null || cursor == null) {
            return;
        }
        try {
            if (cursor.moveToFirst()) {
                long observationQueued = cursor.getLong(cursor.getColumnIndex(DatabaseContract.Reports.TOTAL_OBSERVATION_COUNT));
                long cellsQueued = cursor.getLong(cursor.getColumnIndex(DatabaseContract.Reports.TOTAL_CELL_COUNT));
                long wifisQueued = cursor.getLong(cursor.getColumnIndex(DatabaseContract.Reports.TOTAL_WIFI_COUNT));

                mObservationsQueuedView.setText(String.valueOf(observationQueued));
                mCellsQueuedView.setText(String.valueOf(cellsQueued));
                mWifisQueuedView.setText(String.valueOf(wifisQueued));
                hasQueuedObservations = observationQueued != 0;
            } else {
                mObservationsQueuedView.setText("");
                mCellsQueuedView.setText("");
                mWifisQueuedView.setText("");
                hasQueuedObservations = false;
            }
            updateProgressbarStatus();
        } finally {
            cursor.close();
        }
    }

}
