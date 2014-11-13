/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread.scanners.cellscanner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.AppGlobals.ActiveOrPassiveStumbling;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import org.mozilla.mozstumbler.service.Prefs;
import org.mozilla.mozstumbler.service.core.logging.Log;
import org.mozilla.mozstumbler.service.stumblerthread.Reporter;

public class CellScanner {
    public static final String ACTION_BASE = AppGlobals.ACTION_NAMESPACE + ".CellScanner.";
    public static final String ACTION_CELLS_SCANNED = ACTION_BASE + "CELLS_SCANNED";
    public static final String ACTION_CELLS_SCANNED_ARG_CELLS = "cells";
    public static final String ACTION_CELLS_SCANNED_ARG_TIME = AppGlobals.ACTION_ARG_TIME;

    private static final String LOG_TAG = AppGlobals.LOG_PREFIX + CellScanner.class.getSimpleName();
    private static final long CELL_MIN_UPDATE_TIME = 1000; // milliseconds

    private final Context mAppContext;
    private Timer mCellScanTimer;
    private final Set<String> mCells = new HashSet<String>();
    private final ReportFlushedReceiver mReportFlushedReceiver = new ReportFlushedReceiver();
    private final AtomicBoolean mReportWasFlushed = new AtomicBoolean();
    private Handler mBroadcastScannedHandler;

    private final ISimpleCellScanner mSimpleCellScanner;

    public CellScanner(Context appCtx) {
        mAppContext = appCtx;

        if ((Prefs.getInstance() != null) && Prefs.getInstance().isSimulateStumble()) {
            mSimpleCellScanner = new MockSimpleCellScanner(mAppContext);
        } else {
            mSimpleCellScanner = new SimpleCellScannerImplementation(mAppContext);
        }
    }

    public void start(final ActiveOrPassiveStumbling stumblingMode) {
        if (mCellScanTimer != null) {
            return;
        }

        LocalBroadcastManager.getInstance(mAppContext).registerReceiver(mReportFlushedReceiver,
                new IntentFilter(Reporter.ACTION_NEW_BUNDLE));

        // This is to ensure the broadcast happens from the same thread the CellScanner start() is on
        mBroadcastScannedHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                Intent intent = (Intent) msg.obj;
                LocalBroadcastManager.getInstance(mAppContext).sendBroadcastSync(intent);
            }
        };

        mSimpleCellScanner.start();

        mCellScanTimer = new Timer();

        mCellScanTimer.schedule(new TimerTask() {
            int mPassiveScanCount;
            @Override
            public void run() {
                if (!mSimpleCellScanner.isStarted()) {
                    return;
                }

                if (stumblingMode == ActiveOrPassiveStumbling.PASSIVE_STUMBLING &&
                        mPassiveScanCount++ > AppGlobals.PASSIVE_MODE_MAX_SCANS_PER_GPS)
                {
                    mPassiveScanCount = 0;
                    stop();
                    return;
                }

                final long curTime = System.currentTimeMillis();
                ArrayList<CellInfo> cells = new ArrayList<CellInfo>(mSimpleCellScanner.getCellInfo());

                if (cells.isEmpty()) {
                    return;
                }

                if (mReportWasFlushed.getAndSet(false)) {
                    clearCells();
                }

                for (CellInfo cell : cells) {
                    addToCells(cell.getCellIdentity());
                }

                Intent intent = new Intent(ACTION_CELLS_SCANNED);
                intent.putParcelableArrayListExtra(ACTION_CELLS_SCANNED_ARG_CELLS, cells);
                intent.putExtra(ACTION_CELLS_SCANNED_ARG_TIME, curTime);
                // send to handler, so broadcast is not from timer thread
                Message message = new Message();
                message.obj = intent;
                mBroadcastScannedHandler.sendMessage(message);

            }
        }, 0, CELL_MIN_UPDATE_TIME);
    }

    private synchronized void clearCells() {
        mCells.clear();
    }

    private synchronized void addToCells(String cell) {
        mCells.add(cell);
    }

    public synchronized void stop() {
        mReportWasFlushed.set(false);
        clearCells();
        LocalBroadcastManager.getInstance(mAppContext).unregisterReceiver(mReportFlushedReceiver);

        if (mCellScanTimer != null) {
            mCellScanTimer.cancel();
            mCellScanTimer = null;
        }
        mSimpleCellScanner.stop();
    }

    public synchronized int getCellInfoCount() {
        return mCells.size();
    }

    private class ReportFlushedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            mReportWasFlushed.set(true);
        }
    }
}
