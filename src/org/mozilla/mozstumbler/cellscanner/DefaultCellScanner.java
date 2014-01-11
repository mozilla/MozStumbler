package org.mozilla.mozstumbler.cellscanner;

import android.content.Context;
import android.telephony.CellLocation;
import android.telephony.NeighboringCellInfo;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.util.ArrayList;
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
        mSignalStrength = CellInfo.UNKNOWN_SIGNAL;
        mCdmaDbm = CellInfo.UNKNOWN_SIGNAL;
    }

    @Override
    public void start() {
        mSignalStrength = CellInfo.UNKNOWN_SIGNAL;
        mCdmaDbm = CellInfo.UNKNOWN_SIGNAL;
        mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
    }

    @Override
    public void stop() {
        mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
        mSignalStrength = CellInfo.UNKNOWN_SIGNAL;
        mCdmaDbm = CellInfo.UNKNOWN_SIGNAL;
    }

    @Override
    public List<CellInfo> getCellInfo() {
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
        final List<CellInfo> records = new ArrayList<CellInfo>(1 + (cells == null ? 0 : cells.size()));
        final CellInfo info = new CellInfo(mPhoneType);
        try {
            final int signalStrength = mSignalStrength;
            final int cdmaDbm = mCdmaDbm;
            info.setCellLocation(cl,
                    mTelephonyManager.getNetworkType(),
                    networkOperator,
                    signalStrength == CellInfo.UNKNOWN_SIGNAL ? null : signalStrength,
                    cdmaDbm == CellInfo.UNKNOWN_SIGNAL ? null : cdmaDbm);
            records.add(info);
        } catch (IllegalArgumentException iae) {
            Log.e(LOGTAG, "Skip invalid or incomplete CellLocation: " + cl, iae);
        }

        if (cells != null) {
            for (NeighboringCellInfo nci : cells) {
                try {
                    final CellInfo record = new CellInfo(mPhoneType);
                    record.setNeighboringCellInfo(nci, networkOperator);
                    records.add(record);
                } catch (IllegalArgumentException iae) {
                    Log.e(LOGTAG, "Skip invalid or incomplete NeighboringCellInfo: " + nci, iae);
                }
            }
        }
        return records;
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
