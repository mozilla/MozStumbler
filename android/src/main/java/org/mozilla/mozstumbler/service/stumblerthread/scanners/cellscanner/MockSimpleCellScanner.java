/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread.scanners.cellscanner;

import android.content.Context;

import org.mozilla.mozstumbler.service.core.logging.ClientLog;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.SimulationContext;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

import java.util.LinkedList;
import java.util.List;


public class MockSimpleCellScanner implements ISimpleCellScanner {

    private static final String LOG_TAG = LoggerUtil.makeLogTag(MockSimpleCellScanner.class);

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

        SimulationContext ctx;

        try {
            ctx = (SimulationContext) mAppContext;
            result.addAll(ctx.getNextMockCellBlock());
        } catch (ClassCastException ex) {
            ClientLog.e(LOG_TAG, "Error getting the proper context", ex);
        }
        return result;
    }

    public boolean isSupportedOnThisDevice() {
        return true;
    }
}
