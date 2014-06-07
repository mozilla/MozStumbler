package org.mozilla.mozstumbler.service.scanners.cellscanner;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import org.mozilla.mozstumbler.service.SharedConstants;

public class CellScanner {
    public static final String ACTION_BASE = SharedConstants.ACTION_NAMESPACE + ".CellScanner.";
    public static final String ACTION_CELLS_SCANNED = ACTION_BASE + "CELLS_SCANNED";
    public static final String ACTION_CELLS_SCANNED_ARG_CELLS = "cells";
    public static final String ACTION_CELLS_SCANNED_ARG_TIME = SharedConstants.ACTION_ARG_TIME;

    private static final String LOGTAG = CellScanner.class.getName();
    private static final long CELL_MIN_UPDATE_TIME = 1000; // milliseconds

    private final Context mContext;
    private CellScannerImpl mImpl;
    private Timer mCellScanTimer;
    private final Set<String> mCells = new HashSet<String>();
    private int mCurrentCellInfoCount;

    interface CellScannerImpl {
        public void start();

        public void stop();

        public List<CellInfo> getCellInfo();
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
                ArrayList<CellInfo> cells = new ArrayList<CellInfo>(mImpl.getCellInfo());
                mCurrentCellInfoCount = cells.size();
                if (cells.isEmpty()) {
                    return;
                }
                for (CellInfo cell: cells) mCells.add(cell.getCellIdentity());

                Intent intent = new Intent(ACTION_CELLS_SCANNED);
                intent.putParcelableArrayListExtra(ACTION_CELLS_SCANNED_ARG_CELLS, cells);
                intent.putExtra(ACTION_CELLS_SCANNED_ARG_TIME, curTime);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
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

    public int getCellInfoCount() {
        return mCells.size();
    }

    public int getCurrentCellInfoCount() {
        return mCurrentCellInfoCount;
    }
}
