package org.mozilla.mozstumbler.service.scanners.cellscanner;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.telephony.CellIdentityCdma;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.CellLocation;
import android.telephony.CellSignalStrengthCdma;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthWcdma;
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

    private static final GetAllCellInfoScannerImpl sGetAllInfoCellScanner;

    private final TelephonyManager mTelephonyManager;

    private final int mPhoneType;

    private volatile int mSignalStrength;
    private volatile int mCdmaDbm;

    interface GetAllCellInfoScannerImpl {
        List<CellInfo> getAllCellInfo(TelephonyManager tm);
    }

    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            sGetAllInfoCellScanner = new GetAllCellInfoScannerMr2();
        }else {
            sGetAllInfoCellScanner = new GetAllCellInfoScannerDummy();
        }
    }

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
        List<CellInfo> records = new ArrayList<CellInfo>();

        List<CellInfo> allCells = sGetAllInfoCellScanner.getAllCellInfo(mTelephonyManager);
        if (allCells.isEmpty()) {
            CellInfo currentCell = getCurrentCellInfo();
            if (currentCell == null) {
                return records;
            }
            records.add(currentCell);
        }else {
            records.addAll(allCells);
        }

        // getNeighboringCells() sometimes contains more information than that is already
        // in getAllCellInfo(). Use the results of both of them.
        records.addAll(getNeighboringCells());
        return records;
    }

    private String getNetworkOperator() {
        String networkOperator = mTelephonyManager.getNetworkOperator();
        // getNetworkOperator() may be unreliable on CDMA networks
        if (networkOperator == null || networkOperator.length() <= 3) {
            networkOperator = mTelephonyManager.getSimOperator();
        }
        return networkOperator;
    }

    private CellInfo getCurrentCellInfo() {
        final CellLocation currentCell;
        currentCell = mTelephonyManager.getCellLocation();
        if (currentCell == null) {
            return null;
        }
        try {
            final CellInfo info = new CellInfo(mPhoneType);
            final int signalStrength = mSignalStrength;
            final int cdmaDbm = mCdmaDbm;
            info.setCellLocation(currentCell,
                    mTelephonyManager.getNetworkType(),
                    getNetworkOperator(),
                    signalStrength == CellInfo.UNKNOWN_SIGNAL ? null : signalStrength,
                    cdmaDbm == CellInfo.UNKNOWN_SIGNAL ? null : cdmaDbm);
            return info;
        } catch (IllegalArgumentException iae) {
            Log.e(LOGTAG, "Skip invalid or incomplete CellLocation: " + currentCell, iae);
        }
        return null;
    }

    private List<CellInfo> getNeighboringCells() {
        Collection<NeighboringCellInfo> cells = mTelephonyManager.getNeighboringCellInfo();
        if (cells == null || cells.isEmpty()) {
            return Collections.emptyList();
        }

        String networkOperator = getNetworkOperator();
        List<CellInfo> records = new ArrayList<CellInfo>(cells.size());
        for (NeighboringCellInfo nci : cells) {
            try {
                final CellInfo record = new CellInfo(mPhoneType);
                record.setNeighboringCellInfo(nci, networkOperator);
                records.add(record);
            } catch (IllegalArgumentException iae) {
                Log.e(LOGTAG, "Skip invalid or incomplete NeighboringCellInfo: " + nci, iae);
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

    private static class GetAllCellInfoScannerDummy implements GetAllCellInfoScannerImpl {
        @Override
        public List<CellInfo> getAllCellInfo(TelephonyManager tm) {
            return Collections.emptyList();
        }
    }

    @TargetApi(18)
    private static class GetAllCellInfoScannerMr2 implements GetAllCellInfoScannerImpl {
        @Override
        public List<CellInfo> getAllCellInfo(TelephonyManager tm) {
            final List<android.telephony.CellInfo> observed = tm.getAllCellInfo();
            if (observed == null || observed.isEmpty()) {
                return Collections.emptyList();
            }

            List<CellInfo> cells = new ArrayList<CellInfo>(observed.size());
            for (android.telephony.CellInfo observedCell : observed) {
                if (observedCell instanceof CellInfoGsm) {
                    CellIdentityGsm ident = ((CellInfoGsm) observedCell).getCellIdentity();
                    if (ident.getMcc() != Integer.MAX_VALUE && ident.getMnc() != Integer.MAX_VALUE) {
                        CellSignalStrengthGsm strength = ((CellInfoGsm) observedCell).getCellSignalStrength();
                        CellInfo cell = new CellInfo(tm.getPhoneType());
                        cell.setGsmCellInfo(ident.getMcc(),
                                ident.getMnc(),
                                ident.getLac(),
                                ident.getCid(),
                                strength.getAsuLevel());
                        cells.add(cell);
                    }
                } else if (observedCell instanceof CellInfoCdma) {
                    CellInfo cell = new CellInfo(tm.getPhoneType());
                    CellIdentityCdma ident = ((CellInfoCdma) observedCell).getCellIdentity();
                    CellSignalStrengthCdma strength = ((CellInfoCdma) observedCell).getCellSignalStrength();
                    cell.setCdmaCellInfo(ident.getBasestationId(),
                            ident.getNetworkId(),
                            ident.getSystemId(),
                            strength.getDbm());
                    cells.add(cell);
                } else if (observedCell instanceof CellInfoLte) {
                    CellIdentityLte ident = ((CellInfoLte) observedCell).getCellIdentity();
                    if (ident.getMnc() != Integer.MAX_VALUE && ident.getMnc() != Integer.MAX_VALUE) {
                        CellInfo cell = new CellInfo(tm.getPhoneType());
                        CellSignalStrengthLte strength = ((CellInfoLte) observedCell).getCellSignalStrength();
                        cell.setLteCellInfo(ident.getMcc(),
                                ident.getMnc(),
                                ident.getCi(),
                                ident.getPci(),
                                ident.getTac(),
                                strength.getAsuLevel(),
                                strength.getTimingAdvance());
                    }
                } else if (observedCell instanceof CellInfoWcdma) {
                    CellIdentityWcdma ident = ((CellInfoWcdma) observedCell).getCellIdentity();
                    if (ident.getMnc() != Integer.MAX_VALUE && ident.getMnc() != Integer.MAX_VALUE) {
                        CellInfo cell = new CellInfo(tm.getPhoneType());
                        CellSignalStrengthWcdma strength = ((CellInfoWcdma) observedCell).getCellSignalStrength();
                        cell.setWcmdaCellInfo(ident.getMcc(),
                                ident.getMnc(),
                                ident.getLac(),
                                ident.getCid(),
                                ident.getPsc(),
                                strength.getAsuLevel());
                        cells.add(cell);
                    }
                } else {
                    Log.i(LOGTAG, "Skipped CellInfo of unknown class: " + observedCell.toString());
                }
            }
            return cells;
        }
    }
}
