package org.mozilla.mozstumbler.cellscanner;

import android.content.Context;
import android.content.Intent;

import android.util.Log;

import org.mozilla.mozstumbler.ScannerService;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class CellScanner {
    private static final String LOGTAG = CellScanner.class.getName();
    private static final long CELL_MIN_UPDATE_TIME = 1000; // milliseconds

    private final Context mContext;
    private CellScannerImpl mImpl;
    private Timer mCellScanTimer;

    interface CellScannerImpl {
        public void start();

        public void stop();

        public List<CellsRecord> getCellInfo();
    }

    public CellScanner(Context context) {
        mContext = context;
    }

    public void start() {
        if (mImpl != null) {
            return;
        }

        try {
            mImpl = new DefaultCellScanner(mContext);
        } catch (UnsupportedOperationException uoe) {
            Log.e(LOGTAG, "Cell scanner probe failed", uoe);
            return;
        }

        mImpl.start();

        mCellScanTimer = new Timer();

        mCellScanTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                Log.d(LOGTAG, "Cell Scanning Timer fired");
                final long curTime = System.currentTimeMillis();
                for (CellsRecord record : mImpl.getCellInfo()) {
                    if (record.hasCells()) {
                        Intent intent = new Intent(ScannerService.MESSAGE_TOPIC);
                        intent.putExtra(Intent.EXTRA_SUBJECT, "CellScanner");
                        intent.putExtra("time", curTime);
                        intent.putExtra("data", record.getCellsAsJson().toString());
                        intent.putExtra("radioType", record.getRadio());
                        mContext.sendBroadcast(intent);
                    }
                }
            }
        }, 0, CELL_MIN_UPDATE_TIME);
    }

    public void stop() {
        if (mCellScanTimer != null) {
            mCellScanTimer.cancel();
            mCellScanTimer = null;
        }
        if (mImpl != null) {
            mImpl.stop();
            mImpl = null;
        }
    }
}
