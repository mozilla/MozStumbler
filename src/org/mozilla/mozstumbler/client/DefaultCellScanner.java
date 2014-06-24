package org.mozilla.mozstumbler.client;

import android.annotation.TargetApi;
import android.content.Context;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfoWcdma;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.TelephonyManager;
import android.util.Log;

import org.mozilla.mozstumbler.service.SharedConstants;
import org.mozilla.mozstumbler.service.scanners.cellscanner.CellInfo;
import org.mozilla.mozstumbler.service.scanners.cellscanner.CellScannerNoWCDMA;
import java.util.List;

public class DefaultCellScanner extends CellScannerNoWCDMA {

    public DefaultCellScanner(Context context) {
        super(context);
        LOGTAG = DefaultCellScanner.class.getName();
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
                if (SharedConstants.isDebug) Log.d(LOGTAG, String.format("Invalid-> mnc:%d mcc:%d", ident.getMnc(), ident.getMcc()));
            }
        }
        if (!added) {
            return super.addCellToList(cells, observedCell, tm);
        } else {
            return true;
        }
    }
}
