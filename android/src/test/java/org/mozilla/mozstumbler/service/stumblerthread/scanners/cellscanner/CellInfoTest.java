package org.mozilla.mozstumbler.service.stumblerthread.scanners.cellscanner;


import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mozilla.mozstumbler.svclocator.services.log.LoggerUtil;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static junit.framework.Assert.assertEquals;

/**
 * Created by victorng on 14-11-19.
 */

@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
public class CellInfoTest {

    private static final String LOG_TAG = LoggerUtil.makeLogTag(CellInfoTest.class);

    // Only two fields in the CellInfo struct are strings.  This just makes sure that all
    // values will map to known values or the empty string.

    @Test
    public void testCellInfoRadioType() {
        // The only two fields in CellInfo which have unrestricted length are
        // mCellRadio and mRadio.  Both of those should only allow setting string
        // values from known lists.

        int GARBAGE_PHONE_TYPE = 1600000000;
        CellInfo cellInfo;

        cellInfo = new CellInfo(GARBAGE_PHONE_TYPE);
        assertEquals(cellInfo.getRadio(), "");

        cellInfo = new CellInfo(TelephonyManager.PHONE_TYPE_NONE);
        assertEquals(cellInfo.getRadio(), "");

        cellInfo = new CellInfo(TelephonyManager.PHONE_TYPE_CDMA);
        assertEquals(cellInfo.getRadio(), "cdma");

        cellInfo = new CellInfo(TelephonyManager.PHONE_TYPE_NONE);
        assertEquals(cellInfo.getRadio(), "");

        cellInfo = new CellInfo(TelephonyManager.PHONE_TYPE_SIP);
        assertEquals(cellInfo.getRadio(), "");
    }

    @Test
    public void testCellInfoCellRadioType() {
        int GARBAGE_PHONE_TYPE = 1600000000;
        CellInfo cellInfo;

        cellInfo = new CellInfo(GARBAGE_PHONE_TYPE);
        GsmCellLocation gcl = new GsmCellLocation();
        gcl.setLacAndCid(1, 2);

        int[] netTypes;

        netTypes = new int[]{TelephonyManager.NETWORK_TYPE_UNKNOWN, 32432789};
        for (int networkType : netTypes) {
            cellInfo.setCellLocation(gcl, networkType, "123456", 5, 5);
            assertEquals("", cellInfo.getCellRadio());
        }

        netTypes = new int[]{TelephonyManager.NETWORK_TYPE_EVDO_0};
        for (int networkType : netTypes) {
            cellInfo.setCellLocation(gcl, networkType, "123456", 5, 5);
            assertEquals(CellInfo.CELL_RADIO_CDMA, cellInfo.getCellRadio());
        }
    }


}