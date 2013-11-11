package org.mozilla.mozstumbler.cellscanner;

import android.content.Context;
import android.telephony.CellLocation;
import android.telephony.NeighboringCellInfo;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class DefaultCellScanner implements CellScanner.CellScannerImpl {

    private static final String LOGTAG = DefaultCellScanner.class.getName();

    private final TelephonyManager mTelephonyManager;

    private final int mPhoneType;

    private volatile int mSignalStrength;
    private volatile int mCdmaDbm;

    DefaultCellScanner(Context context) {
        mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (mTelephonyManager == null) {
            throw new UnsupportedOperationException("TelephonyManager service is not available");
        }

        mPhoneType = mTelephonyManager.getPhoneType();

        if (mPhoneType != TelephonyManager.PHONE_TYPE_GSM
                && mPhoneType != TelephonyManager.PHONE_TYPE_CDMA) {
            throw new UnsupportedOperationException("Unexpected Phone Type: " + mPhoneType);
        }
        mSignalStrength = CellsRecord.CellInfo.UNKNOWN_SIGNAL;
        mCdmaDbm = CellsRecord.CellInfo.UNKNOWN_SIGNAL;
    }

    @Override
    public void start() {
        mSignalStrength = CellsRecord.CellInfo.UNKNOWN_SIGNAL;
        mCdmaDbm = CellsRecord.CellInfo.UNKNOWN_SIGNAL;
        mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
    }

    @Override
    public void stop() {
        mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
        mSignalStrength = CellsRecord.CellInfo.UNKNOWN_SIGNAL;
        mCdmaDbm = CellsRecord.CellInfo.UNKNOWN_SIGNAL;
    }

    @Override
    public List<CellsRecord> getCellInfo() {
        String networkOperator;
        final CellLocation cl = mTelephonyManager.getCellLocation();
        if (cl == null) {
            return Collections.emptyList();
        }
        Collection<NeighboringCellInfo> cells = mTelephonyManager.getNeighboringCellInfo();

        networkOperator = mTelephonyManager.getNetworkOperator();
        // getNetworkOperator() may be unreliable on CDMA networks
        if (networkOperator == null || networkOperator.length() <= 3) {
            networkOperator = mTelephonyManager.getSimOperator();
        }
        final CellsRecord info = new CellsRecord(mPhoneType);
        try {
            final int signalStrength = mSignalStrength;
            final int cdmaDbm = mCdmaDbm;
            info.putCellLocation(cl,
                    mTelephonyManager.getNetworkType(),
                    networkOperator,
                    signalStrength == CellsRecord.CellInfo.UNKNOWN_SIGNAL ? null : signalStrength,
                    cdmaDbm == CellsRecord.CellInfo.UNKNOWN_SIGNAL ? null : cdmaDbm);
        } catch (IllegalArgumentException iae) {
            Log.e(LOGTAG, "Skip invalid or incomplete CellLocation: " + cl, iae);
        }

        if (cells != null) {
            for (NeighboringCellInfo nci : cells) {
                try {
                    info.putNeighboringCell(nci, networkOperator);
                } catch (IllegalArgumentException iae) {
                    Log.e(LOGTAG, "Skip invalid or incomplete NeighboringCellInfo: " + nci, iae);
                }
            }
        }
        return Collections.singletonList(info);
    }

    private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onSignalStrengthsChanged(SignalStrength ss) {
            if (ss.isGsm()) {
                mSignalStrength = ss.getGsmSignalStrength();
            } else {
                mCdmaDbm = ss.getCdmaDbm();
            }
        }
    };
}
