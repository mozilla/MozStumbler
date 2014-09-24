/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.client.navdrawer;

import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import org.mozilla.mozstumbler.R;
import org.mozilla.mozstumbler.client.ClientStumblerService;
import org.mozilla.mozstumbler.client.DateTimeUtils;
import org.mozilla.mozstumbler.client.MainApp;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.DataStorageContract;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.DataStorageManager;

import java.io.IOException;
import java.util.Date;
import java.util.Properties;
import com.ocpsoft.pretty.time.PrettyTime;

public class MetricsView {
    private static final String LOG_TAG = AppGlobals.LOG_PREFIX + MetricsView.class.getSimpleName();

    private TextView 
            mLastUpdateTimeView,
            mAllTimeObservationsSentView,
            mAllTimeDataSentView,
            mAllTimeCellsSentView,
            mAllTimeWifisSentView,
            mQueuedObservationsView,
            mQueuedCellsView,
            mQueuedWifisView,
            mQueuedDataView,
            mThisSessionObservationsView,
            mThisSessionCellsView,
            mThisSessionWifisView,
            mThisSessionDataView;

    private ImageButton mUploadButton;

    View mView;
    
    public MetricsView(View view) {
        mView = view;

        mLastUpdateTimeView = (TextView) mView.findViewById(R.id.last_upload_time_value);
        mAllTimeDataSentView = (TextView) mView.findViewById(R.id.data_kb_sent_value);
        mAllTimeObservationsSentView = (TextView) mView.findViewById(R.id.observations_sent_value);
        mAllTimeCellsSentView = (TextView) mView.findViewById(R.id.cells_sent_value);
        mAllTimeWifisSentView = (TextView) mView.findViewById(R.id.wifis_sent_value);
        mQueuedObservationsView = (TextView) mView.findViewById(R.id.observations_queued_value);
        mQueuedCellsView = (TextView) mView.findViewById(R.id.cells_queued_value);
        mQueuedWifisView = (TextView) mView.findViewById(R.id.wifis_queued_value);
        mQueuedDataView = (TextView) mView.findViewById(R.id.data_kb_queued_value);
        mThisSessionObservationsView = (TextView) mView.findViewById(R.id.this_session_observations_value);
        mThisSessionCellsView = (TextView) mView.findViewById(R.id.this_session_cells_value);
        mThisSessionWifisView = (TextView) mView.findViewById(R.id.this_session_wifis_value);
        mThisSessionDataView= (TextView) mView.findViewById(R.id.this_session_kb_value);

        mUploadButton = (ImageButton) mView.findViewById(R.id.upload_observations_button);
//        mUploadButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                AsyncUploader.UploadSettings settings =
//                        new AsyncUploader.UploadSettings(ClientPrefs.getInstance().getUseWifiOnly());
//                mUploader = new AsyncUploader(settings, UploadReportsDialog.this);
//                mUploader.setNickname(ClientPrefs.getInstance().getNickname());
//                mUploader.execute();
//                updateProgressbarStatus();
//            }
//        });
    }
    
    public void update() {
        updateQueuedStats();
        updateSentStats();
        updateThisSessionStats();
    }

    private void updateThisSessionStats() {
        mThisSessionDataView.setText("");
        mThisSessionCellsView.setText("");
        mThisSessionWifisView.setText("");
        mThisSessionObservationsView.setText("");

        MainApp app = (MainApp)mView.getContext().getApplicationContext();
        ClientStumblerService service = app.getService();
        if (service == null) {
            return;
        }

        mThisSessionWifisView.setText(String.valueOf(service.getAPCount()));
        mThisSessionCellsView.setText(String.valueOf(service.getCellInfoCount()));
    }

    String formatKb(long bytes) {
        float kb = bytes / 1000.0f;
        return (Math.round(kb * 10.0f) / 10.0f) + " KB";
    }

    private void updateSentStats() {
        mLastUpdateTimeView.setText("");
        mAllTimeObservationsSentView.setText("");
        mAllTimeCellsSentView.setText("");
        mAllTimeWifisSentView.setText("");

        try {
            String value;
            Properties props = DataStorageManager.getInstance().readSyncStats();
            value = props.getProperty(DataStorageContract.Stats.KEY_OBSERVATIONS_SENT, "0");
            mAllTimeObservationsSentView.setText(value);
            value = props.getProperty(DataStorageContract.Stats.KEY_CELLS_SENT, "0");
            mAllTimeCellsSentView.setText(String.valueOf(value));
            value = props.getProperty(DataStorageContract.Stats.KEY_WIFIS_SENT, "0");
            mAllTimeWifisSentView.setText(String.valueOf(value));
            value = props.getProperty(DataStorageContract.Stats.KEY_BYTES_SENT, "0");
            mAllTimeDataSentView.setText(formatKb(Long.parseLong(value)));

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
        mQueuedObservationsView.setText(String.valueOf(q.mReportCount));
        mQueuedCellsView.setText(String.valueOf(q.mCellCount));
        mQueuedWifisView.setText(String.valueOf(q.mWifiCount));
        mQueuedDataView.setText(formatKb(q.mBytes));
       // hasQueuedObservations = q.mReportCount != 0;
       // updateProgressbarStatus();
    }
}
