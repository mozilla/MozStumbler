package org.mozilla.mozstumbler.service.stumblerthread.scanners.cellscanner;

import android.content.Context;

import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.core.logging.Log;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.SimulateStumbleContextWrapper;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by victorng on 14-11-11.
 */
public class MockSimpleCellScanner implements ISimpleCellScanner {

    private static final String LOG_TAG = AppGlobals.LOG_PREFIX + MockSimpleCellScanner.class.getSimpleName();

    private final Context mAppContext;
    private boolean started = false;

    public MockSimpleCellScanner(Context appCtx) {
        mAppContext = appCtx;
    }

    @Override
    public void start() {
        started = true;
    }

    @Override
    public boolean isStarted() {
        return started;
    }

    @Override
    public void stop() {
        started = false;
    }

    @Override
    public List<CellInfo> getCellInfo() {

        LinkedList<CellInfo> result = new LinkedList<CellInfo>();

        SimulateStumbleContextWrapper ctx;

        try {
            ctx = (SimulateStumbleContextWrapper)mAppContext;
            result.addAll(ctx.getNextMockCellBlock());
        } catch (ClassCastException ex) {
            Log.e(LOG_TAG, "Error getting the proper context", ex);
        }
        return result;
    }
}
