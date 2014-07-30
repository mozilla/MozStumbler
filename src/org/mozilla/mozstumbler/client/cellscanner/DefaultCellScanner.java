package org.mozilla.mozstumbler.client.cellscanner;

import android.annotation.TargetApi;
import android.content.Context;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfoWcdma;
import android.telephony.CellLocation;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.TelephonyManager;

import org.mozilla.mozstumbler.service.scanners.cellscanner.CellInfo;
import org.mozilla.mozstumbler.service.scanners.cellscanner.CellScannerNoWCDMA;
import java.util.List;

public class DefaultCellScanner extends CellScannerNoWCDMA {

    ScreenMonitor mScreenMonitor;

    public DefaultCellScanner(Context context) {
        super(context);
        LOGTAG = DefaultCellScanner.class.getName();
        mScreenMonitor = new ScreenMonitor(mContext);
    }

    @Override
    public void start() {
        super.start();
        mScreenMonitor.start();
    }

    @Override
    public void stop() {
        try {
            mScreenMonitor.stop();
        } catch (IllegalArgumentException ex) {}
    }

    @Override
    protected CellInfo getCurrentCellInfo() {
        final CellLocation currentCell = mTelephonyManager.getCellLocation();
        if (currentCell == null) {
            return null;
        }
        mScreenMonitor.putLocation(currentCell);
        if (!mScreenMonitor.isLocationValid()) {
            return null;
        }
        return super.getCurrentCellInfo();
    }

    @TargetApi(18)
    protected boolean addCellToList(List<CellInfo> cells,
                                    android.telephony.CellInfo observedCell,
                                    TelephonyManager tm) {
        boolean added = false;
        if (observedCell instanceof CellInfoWcdma) {
            CellIdentityWcdma ident = ((CellInfoWcdma) observedCell).getCellIdentity();
            if (ident.getMnc() != Integer.MAX_VALUE && ident.getMcc() != Integer.MAX_VALUE) {
                CellInfo cell = new CellInfo(tm.getPhoneType());
                CellSignalStrengthWcdma strength = ((CellInfoWcdma) observedCell).getCellSignalStrength();
                cell.setWcmdaCellInfo(ident.getMcc(),
                        ident.getMnc(),
                        ident.getLac(),
                        ident.getCid(),
                        ident.getPsc(),
                        strength.getAsuLevel());
                cells.add(cell);
                added = true;
            }
            else {
                //if (SharedConstants.isDebug) Log.d(LOGTAG, String.format("Invalid-> mnc:%d mcc:%d", ident.getMnc(), ident.getMcc()));
            }
        }
        return added || super.addCellToList(cells, observedCell, tm);
    }
}
