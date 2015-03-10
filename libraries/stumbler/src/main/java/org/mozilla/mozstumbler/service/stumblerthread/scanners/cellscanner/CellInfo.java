/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread.scanners.cellscanner;

import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.telephony.CellLocation;
import android.telephony.NeighboringCellInfo;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;

public class CellInfo implements Parcelable {
    public static final String CELL_RADIO_UNKNOWN = "";
    public static final String CELL_RADIO_GSM = "gsm";
    public static final String CELL_RADIO_WCDMA = "wcdma";
    public static final String CELL_RADIO_CDMA = "cdma";
    public static final String CELL_RADIO_LTE = "lte";

    public static final int UNKNOWN_CID = -1;
    public static final int UNKNOWN_LAC = -1;
    public static final int UNKNOWN_SIGNAL_STRENGTH = -1000;
    public static final int UNKNOWN_ASU = -1;

    private static final String LOG_TAG = LoggerUtil.makeLogTag(CellInfo.class);

    private String mCellRadio;

    private int mMcc;
    private int mMnc;
    private int mCid;
    private int mLac;

    public void setSignalStrength(int signalStrength) {
        mSignalStrength = signalStrength;
    }

    private int mSignalStrength;
    private int mAsu;
    private int mTa;
    private int mPsc;

    public CellInfo() {
        reset();
    }

    /*
     This constructor is only used when building CellInfo for a neighbouring cell.
     */
    public CellInfo(NeighboringCellInfo nci, String networkOperator) {
        reset();
        mCellRadio = getCellRadioTypeName(nci.getNetworkType());
        setNetworkOperator(networkOperator);

        if (nci.getLac() >= 0) mLac = nci.getLac();

        if (nci.getCid() >= 0) mCid = nci.getCid();
        if (nci.getPsc() >= 0) mPsc = nci.getPsc();

        if (nci.getRssi() != NeighboringCellInfo.UNKNOWN_RSSI) {
            mSignalStrength = nci.getRssi();
        }
    }

    static String getCellRadioTypeName(int networkType) {
        switch (networkType) {
            // If the network is either GSM or any high-data-rate variant of it, the radio
            // field should be specified as `gsm`. This includes `GSM`, `EDGE` and `GPRS`.
            case TelephonyManager.NETWORK_TYPE_GPRS:
            case TelephonyManager.NETWORK_TYPE_EDGE:
                return CELL_RADIO_GSM;

            // If the network is either UMTS or any high-data-rate variant of it, the radio
            // field should be specified as `wcdma`. This includes `UMTS`, `HSPA`, `HSDPA`,
            // `HSPA+` and `HSUPA`.
            case TelephonyManager.NETWORK_TYPE_UMTS:
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_HSPAP:
                return CELL_RADIO_WCDMA;

            case TelephonyManager.NETWORK_TYPE_LTE:
                return CELL_RADIO_LTE;

            // If the network is either CDMA or one of the EVDO variants, the radio
            // field should be specified as `cdma`. This includes `1xRTT`, `CDMA`, `eHRPD`,
            // `EVDO_0`, `EVDO_A`, `EVDO_B`, `IS95A` and `IS95B`.
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
            case TelephonyManager.NETWORK_TYPE_1xRTT:
            case TelephonyManager.NETWORK_TYPE_EHRPD:
            case TelephonyManager.NETWORK_TYPE_IDEN:
                return CELL_RADIO_CDMA;

            default:
                Log.e(LOG_TAG, "", new IllegalArgumentException("Unexpected network type: " + networkType));
                return CELL_RADIO_UNKNOWN;
        }
    }

    @SuppressWarnings("fallthrough")
    private static String getRadioTypeName(int phoneType) {
        switch (phoneType) {
            case TelephonyManager.PHONE_TYPE_CDMA:
                return CELL_RADIO_CDMA;

            case TelephonyManager.PHONE_TYPE_GSM:
                return CELL_RADIO_GSM;

            default:
                Log.e(LOG_TAG, "", new IllegalArgumentException("Unexpected phone type: " + phoneType));
                // fallthrough

            case TelephonyManager.PHONE_TYPE_NONE:
            case TelephonyManager.PHONE_TYPE_SIP:
                // These devices have no radio.
                return "";
        }
    }

    public boolean isCellRadioValid() {
        return mCellRadio != null && (mCellRadio.length() > 0) && !mCellRadio.equals("0");
    }

    public String getCellRadio() {
        return mCellRadio;
    }

    public int getMcc() {
        return mMcc;
    }

    public int getMnc() {
        return mMnc;
    }

    public int getCid() {
        return mCid;
    }

    public int getLac() {
        return mLac;
    }

    public int getPsc() {
        return mPsc;
    }

    public JSONObject toJSONObject() {
        final JSONObject obj = new JSONObject();

        try {
            obj.put("radioType", getCellRadio());

            // Bug #1510
            // if (mCid != UNKNOWN_CID) {
            obj.put("cellId", mCid);
            //}

            // Bug #1510
            //if (mLac != UNKNOWN_LAC) {
            obj.put("locationAreaCode", mLac);
            //}

            obj.put("mobileCountryCode", mMcc);
            obj.put("mobileNetworkCode", mMnc);

            if (mSignalStrength != UNKNOWN_SIGNAL_STRENGTH) obj.put("signalStrength", mSignalStrength);
            if (mTa != UNKNOWN_CID) obj.put("timingAdvance", mTa);
            if (mPsc != UNKNOWN_CID) obj.put("psc", mPsc);
            if (mAsu != UNKNOWN_ASU) obj.put("asu", mAsu);

        } catch (JSONException jsonE) {
            throw new IllegalStateException(jsonE);
        }

        return obj;
    }

    public String getCellIdentity() {
        return getCellRadio()
                + " " + getMcc()
                + " " + getMnc()
                + " " + getLac()
                + " " + getCid()
                + " " + getPsc();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mCellRadio);
        dest.writeInt(mMcc);
        dest.writeInt(mMnc);
        dest.writeInt(mCid);
        dest.writeInt(mLac);
        dest.writeInt(mSignalStrength);
        dest.writeInt(mAsu);
        dest.writeInt(mTa);
        dest.writeInt(mPsc);
    }

    void reset() {
        mCellRadio = CELL_RADIO_GSM;
        mMcc = UNKNOWN_CID;
        mMnc = UNKNOWN_CID;
        mLac = UNKNOWN_LAC;
        mCid = UNKNOWN_CID;
        mSignalStrength = UNKNOWN_SIGNAL_STRENGTH;
        mAsu = UNKNOWN_ASU;
        mTa = UNKNOWN_CID;
        mPsc = UNKNOWN_CID;
    }

    void setCellLocation(CellLocation cl,
                         int networkType,
                         String networkOperator,
                         Integer signalStrength) {
        if (cl instanceof GsmCellLocation) {
            final int lac, cid;
            final GsmCellLocation gcl = (GsmCellLocation) cl;

            reset();
            mCellRadio = getCellRadioTypeName(networkType);
            setNetworkOperator(networkOperator);

            lac = gcl.getLac();
            cid = gcl.getCid();
            if (lac >= 0) mLac = lac;
            if (cid >= 0) mCid = cid;

            if (Build.VERSION.SDK_INT >= 9) {
                final int psc = gcl.getPsc();
                if (psc >= 0) mPsc = psc;
            }

            if (signalStrength != null) {
                mSignalStrength = signalStrength;
            }
        } else if (cl instanceof CdmaCellLocation) {
            final CdmaCellLocation cdl = (CdmaCellLocation) cl;

            reset();
            mCellRadio = getCellRadioTypeName(networkType);

            setNetworkOperator(networkOperator);

            mMnc = cdl.getSystemId();

            mLac = cdl.getNetworkId();
            mCid = cdl.getBaseStationId();

            if (signalStrength != null) {
                mSignalStrength = signalStrength;
            }
        } else {
            throw new IllegalArgumentException("Unexpected CellLocation type: " + cl.getClass().getName());
        }
    }

    public void setGsmCellInfo(int mcc, int mnc, int lac, int cid, int asu) {
        mCellRadio = CELL_RADIO_GSM;
        mMcc = mcc != Integer.MAX_VALUE ? mcc : UNKNOWN_CID;
        mMnc = mnc != Integer.MAX_VALUE ? mnc : UNKNOWN_CID;
        mLac = lac != Integer.MAX_VALUE ? lac : UNKNOWN_LAC;
        mCid = cid != Integer.MAX_VALUE ? cid : UNKNOWN_CID;
        mAsu = asu;
    }

    public void setWcdmaCellInfo(int mcc, int mnc, int lac, int cid, int psc, int asu) {
        mCellRadio = CELL_RADIO_WCDMA;
        mMcc = mcc != Integer.MAX_VALUE ? mcc : UNKNOWN_CID;
        mMnc = mnc != Integer.MAX_VALUE ? mnc : UNKNOWN_CID;
        mLac = lac != Integer.MAX_VALUE ? lac : UNKNOWN_LAC;
        mCid = cid != Integer.MAX_VALUE ? cid : UNKNOWN_CID;
        mPsc = psc != Integer.MAX_VALUE ? psc : UNKNOWN_CID;
        mAsu = asu;
    }

    /**
     * @param mcc Mobile Country Code, Integer.MAX_VALUE if unknown
     * @param mnc Mobile Network Code, Integer.MAX_VALUE if unknown
     * @param ci  Cell Identity, Integer.MAX_VALUE if unknown
     * @param psc Physical Cell Id, Integer.MAX_VALUE if unknown
     * @param lac Tracking Area Code, Integer.MAX_VALUE if unknown
     * @param asu Arbitrary strength unit
     * @param ta  Timing advance
     */
    public void setLteCellInfo(int mcc, int mnc, int ci, int psc, int lac, int asu, int ta) {
        mCellRadio = CELL_RADIO_LTE;
        mMcc = mcc != Integer.MAX_VALUE ? mcc : UNKNOWN_CID;
        mMnc = mnc != Integer.MAX_VALUE ? mnc : UNKNOWN_CID;
        mLac = lac != Integer.MAX_VALUE ? lac : UNKNOWN_LAC;
        mCid = ci != Integer.MAX_VALUE ? ci : UNKNOWN_CID;
        mPsc = psc != Integer.MAX_VALUE ? psc : UNKNOWN_CID;
        mAsu = asu;
        mTa = ta;
    }

    void setCdmaCellInfo(int baseStationId, int networkId, int systemId, int dbm) {
        mCellRadio = CELL_RADIO_CDMA;
        mMnc = systemId != Integer.MAX_VALUE ? systemId : UNKNOWN_CID;
        mLac = networkId != Integer.MAX_VALUE ? networkId : UNKNOWN_LAC;
        mCid = baseStationId != Integer.MAX_VALUE ? baseStationId : UNKNOWN_CID;
        mSignalStrength = dbm;
    }

    void setNetworkOperator(String mccMnc) {
        if (mccMnc == null || mccMnc.length() < 5 || mccMnc.length() > 8) {
            throw new IllegalArgumentException("Bad mccMnc: " + mccMnc);
        }
        mMcc = Integer.parseInt(mccMnc.substring(0, 3));
        mMnc = Integer.parseInt(mccMnc.substring(3));
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof CellInfo)) {
            return false;
        }
        CellInfo ci = (CellInfo) o;
        return mCellRadio.equals(ci.mCellRadio)
                && mMcc == ci.mMcc
                && mMnc == ci.mMnc
                && mCid == ci.mCid
                && mLac == ci.mLac
                && mSignalStrength == ci.mSignalStrength
                && mAsu == ci.mAsu
                && mTa == ci.mTa
                && mPsc == ci.mPsc;
    }

    @Override
    public int hashCode() {

        // WTH???
        int result = 17;
        result = 31 * result + mCellRadio.hashCode();
        result = 31 * result + mMcc;
        result = 31 * result + mMnc;
        result = 31 * result + mCid;
        result = 31 * result + mLac;
        result = 31 * result + mSignalStrength;
        result = 31 * result + mAsu;
        result = 31 * result + mTa;
        result = 31 * result + mPsc;
        return result;
    }
}
