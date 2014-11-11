package org.mozilla.mozstumbler.service.stumblerthread.scanners.cellscanner;

import android.content.Context;

import java.util.List;

/**
 * Created by victorng on 14-11-11.
 */
public class MockSimpleCellScanner implements ISimpleCellScanner {
    private boolean started = false;

    public MockSimpleCellScanner(Context applicationContext) {
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
        // TODO: hook the context to grab the generator
        return null;
    }
}
