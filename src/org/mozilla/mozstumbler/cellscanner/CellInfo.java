package org.mozilla.mozstumbler.cellscanner;

import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.telephony.CellLocation;
import android.telephony.NeighboringCellInfo;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;

import org.json.JSONException;
import org.json.JSONObject;

public class CellInfo implements Parcelable {
    public static final String RADIO_GSM = "gsm";
    public static final String RADIO_CDMA = "cdma";
    public static final String RADIO_WCDMA = "wcdma";

    public static final String CELL_RADIO_GSM = "gsm";
    public static final String CELL_RADIO_UMTS = "umts";
    public static final String CELL_RADIO_CDMA = "cdma";
    public static final String CELL_RADIO_LTE = "lte";

    static final int UNKNOWN_CID = -1;
    static final int UNKNOWN_SIGNAL = -1000;

    public static final Parcelable.Creator<CellInfo> CREATOR
            = new Parcelable.Creator<CellInfo>() {
        public CellInfo createFromParcel(Parcel in) {
            return new CellInfo(in);
        }

        public CellInfo[] newArray(int size) {
            return new CellInfo[size];
        }
    };

    private String mRadio;
    private String mCellRadio;

    private int mMcc;
    private int mMnc;
    private int mCid;
    private int mLac;
    private int mSignal;
    private int mAsu;
    private int mTa;
    private int mPsc;

    CellInfo(int phoneType) {
        reset();
        setRadio(phoneType);
    }

    private CellInfo(Parcel in) {
        mRadio = in.readString();
        mCellRadio = in.readString();
        mMcc = in.readInt();
        mMnc = in.readInt();
        mCid = in.readInt();
        mLac = in.readInt();
        mSignal = in.readInt();
        mAsu = in.readInt();
        mTa = in.readInt();
        mPsc = in.readInt();
    }

    public String getRadio() {
        return mRadio;
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

    public JSONObject toJSONObject() {
        final JSONObject obj = new JSONObject();

        try {
            obj.put("radio", getCellRadio());
            obj.put("mcc", mMcc);
            obj.put("mnc", mMnc);
            if (mLac != UNKNOWN_CID) obj.put("lac", mLac);
            if (mCid != UNKNOWN_CID) obj.put("cid", mCid);
            if (mSignal != UNKNOWN_SIGNAL) obj.put("signal", mSignal);
            if (mAsu != UNKNOWN_SIGNAL) obj.put("asu", mAsu);
            if (mTa != UNKNOWN_CID) obj.put("ta", mTa);
            if (mPsc != UNKNOWN_CID) obj.put("psc", mPsc);
        } catch (JSONException jsonE) {
            throw new IllegalStateException(jsonE);
        }

        return obj;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mRadio);
        dest.writeString(mCellRadio);
        dest.writeInt(mMcc);
        dest.writeInt(mMnc);
        dest.writeInt(mCid);
        dest.writeInt(mLac);
        dest.writeInt(mSignal);
        dest.writeInt(mAsu);
        dest.writeInt(mTa);
        dest.writeInt(mPsc);
    }

    void reset() {
        mRadio = RADIO_GSM;
        mCellRadio = CELL_RADIO_GSM;
        mMcc = UNKNOWN_CID;
        mMnc = UNKNOWN_CID;
        mLac = UNKNOWN_CID;
        mCid = UNKNOWN_CID;
        mSignal = UNKNOWN_SIGNAL;
        mAsu = UNKNOWN_SIGNAL;
        mTa = UNKNOWN_CID;
        mPsc = UNKNOWN_CID;
    }

    void setRadio(int phoneType) {
        String radio = getRadioTypeName(phoneType);
        if (radio == null) {
            throw new IllegalArgumentException("Unexpected Phone Type: " + phoneType);
        }
        mRadio = radio;
    }

    void setCellLocation(CellLocation cl,
                         int networkType,
                         String networkOperator,
                         Integer gsmSignalStrength,
                         Integer cdmaRssi) {
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

            if (gsmSignalStrength != null) {
                mAsu = gsmSignalStrength;
            }
        } else if (cl instanceof CdmaCellLocation) {
            final CdmaCellLocation cdl = (CdmaCellLocation) cl;

            reset();
            mCellRadio = getCellRadioTypeName(networkType);

            setNetworkOperator(networkOperator);

            mMnc = cdl.getSystemId();

            mLac = cdl.getNetworkId();
            mCid = cdl.getBaseStationId();

            if (cdmaRssi != null) {
                mSignal = cdmaRssi;
            }
        } else {
            throw new IllegalArgumentException("Unexpected CellLocation type: " + cl.getClass().getName());
        }
    }

    void setNeighboringCellInfo(NeighboringCellInfo nci, String networkOperator) {
        final int lac, cid, psc, rssi;

        reset();
        mCellRadio = getCellRadioTypeName(nci.getNetworkType());
        setNetworkOperator(networkOperator);

        lac = nci.getLac();
        cid = nci.getCid();
        psc = nci.getPsc();
        rssi = nci.getRssi();

        if (lac >= 0) mLac = lac;
        if (cid >= 0) mCid = cid;
        if (psc >= 0) mPsc = psc;
        if (rssi != NeighboringCellInfo.UNKNOWN_RSSI) mAsu = rssi;
    }

    void setNetworkOperator(String mccMnc) {
        if (mccMnc == null || mccMnc.length() < 5 || mccMnc.length() > 8) {
            throw new IllegalArgumentException("Bad mccMnc: " + mccMnc);
        }
        mMcc = Integer.parseInt(mccMnc.substring(0, 3));
        mMnc = Integer.parseInt(mccMnc.substring(3));
    }

    static String getCellRadioTypeName(int networkType) {
        String name;
        switch (networkType) {
            case TelephonyManager.NETWORK_TYPE_GPRS:
            case TelephonyManager.NETWORK_TYPE_EDGE:
                return CELL_RADIO_GSM;
            case TelephonyManager.NETWORK_TYPE_UMTS:
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_HSPAP:
                return CELL_RADIO_UMTS;
            case TelephonyManager.NETWORK_TYPE_LTE:
                return CELL_RADIO_LTE;
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
                name = "CDMA - EvDo rev. 0";
                break;
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
                name = "CDMA - EvDo rev. A";
                break;
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
                name = "CDMA - EvDo rev. B";
                break;
            case TelephonyManager.NETWORK_TYPE_1xRTT:
                name = "CDMA - 1xRTT";
                break;
            case TelephonyManager.NETWORK_TYPE_EHRPD:
                name = "CDMA - eHRPD";
                break;
            case TelephonyManager.NETWORK_TYPE_IDEN:
                name = "iDEN";
                break;
            default:
                name = "UNKNOWN (" + String.valueOf(networkType) + ")";
                break;
        }
        throw new IllegalArgumentException("Unexpected Network Type: " + name);
    }

    private static String getRadioTypeName(int phoneType) {
        switch (phoneType) {
            case TelephonyManager.PHONE_TYPE_CDMA:
                return RADIO_CDMA;

            case TelephonyManager.PHONE_TYPE_GSM:
                return RADIO_GSM;

            case TelephonyManager.PHONE_TYPE_NONE:
            case TelephonyManager.PHONE_TYPE_SIP:
                // These devices have no radio.
            default:
                return null;
        }
    }
}
