package org.mozilla.mozstumbler.service.stumblerthread.scanners.cellscanner;

import java.util.List;

/**
 * Created by victorng on 14-11-10.
 */
public interface ISimpleCellScanner {
    void start();
    boolean isStarted();
    void stop();
    List<CellInfo> getCellInfo();
}