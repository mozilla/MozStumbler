package org.mozilla.mozstumbler.client;

import android.accounts.Account;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.SyncStatusObserver;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.mozilla.mozstumbler.BuildConfig;
import org.mozilla.mozstumbler.service.utils.DateTimeUtils;
import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.service.datahandling.DatabaseContract;
import org.mozilla.mozstumbler.service.sync.AuthenticatorService;
import org.mozilla.mozstumbler.service.sync.SyncUtils;

public class UploadReportsDialog extends DialogFragment {
    private static final String LOGTAG = UploadReportsDialog.class.getName();
    private static final boolean DBG = BuildConfig.DEBUG;

    private TextView mLastUpdateTimeView;
    private TextView mObservationsSentView;
    private TextView mCellsSentView;
    private TextView mWifisSentView;
    private TextView mObservationsQueuedView;
    private TextView mCellsQueuedView;
    private TextView mWifisQueuedView;
    private View mUploadButton;
    private View mProgressbarView;

    private Object mStatusChangeListener;
    private boolean hasQueuedObservations;
    private final Account mAccount = AuthenticatorService.GetAccount();

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
                SyncUtils.TriggerRefresh(true);
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

        LoaderManager lm = getLoaderManager();
        lm.initLoader(0, null, mSyncStatsLoaderCallbacks);
        lm.initLoader(1, null, mReportsSummaryLoaderCallbacks);
    }

    @Override
    public void onStart() {
        super.onStart();
        mStatusChangeListener = ContentResolver.addStatusChangeListener(
                ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE, mSyncStatusObserver);
        updateProgressbarStatus();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mStatusChangeListener != null) {
            ContentResolver.removeStatusChangeListener(mStatusChangeListener);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mLastUpdateTimeView = null;
        mObservationsSentView = null;
        mCellsSentView = null;
        mWifisSentView = null;
        mObservationsQueuedView = null;
        mCellsQueuedView = null;
        mWifisQueuedView = null;
        mProgressbarView = null;
    }

    void updateSyncStats(Cursor cursor) {
        if (mLastUpdateTimeView == null) {
            return;
        }
        if (cursor != null) {
            cursor.moveToPosition(-1);
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
        boolean syncActive = ContentResolver.isSyncActive(mAccount, DatabaseContract.CONTENT_AUTHORITY);
        mProgressbarView.setVisibility(syncActive ? View.VISIBLE : View.INVISIBLE);

        boolean uploadButtonActive = hasQueuedObservations && !syncActive;
        mUploadButton.setEnabled(uploadButtonActive);
    }

    void updateReportsSummary(Cursor cursor) {
        if (mLastUpdateTimeView == null) {
            return;
        }
        if (cursor != null && cursor.moveToFirst()) {
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
    }

    private final LoaderManager.LoaderCallbacks<Cursor> mSyncStatsLoaderCallbacks = new LoaderManager.LoaderCallbacks<Cursor>() {

        @Override
        public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
            return new CursorLoader(getActivity(), DatabaseContract.Stats.CONTENT_URI,
                    null, null, null, null);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
            updateSyncStats(cursor);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> cursorLoader) {
            updateSyncStats(null);
        }
    };

    private final LoaderManager.LoaderCallbacks<Cursor> mReportsSummaryLoaderCallbacks = new LoaderManager.LoaderCallbacks<Cursor>() {
        @Override
        public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
            return new CursorLoader(getActivity(), DatabaseContract.Reports.CONTENT_URI_SUMMARY,
                    null, null, null, null);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
            updateReportsSummary(cursor);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> cursorLoader) {
            updateReportsSummary(null);
        }
    };

    private final SyncStatusObserver mSyncStatusObserver = new SyncStatusObserver() {

        private final Handler mHandler = new Handler();

        @Override
        public void onStatusChanged(final int which) {
            if (DBG) Log.v(LOGTAG, "onStatusChanged() " + which);
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    updateProgressbarStatus();
                }
            });
        }
    };

}
