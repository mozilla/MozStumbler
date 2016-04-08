/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.service.stumblerthread.scanners.cellscanner;

import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static junit.framework.Assert.assertEquals;


@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
public class CellScannerInfoTest {

    @Test
    public void testCellInfoCellRadioType() {
        CellInfo cellInfo;

        cellInfo = new CellInfo();
        GsmCellLocation gcl = new GsmCellLocation();
        gcl.setLacAndCid(1, 2);

        int[] netTypes;

        netTypes = new int[]{TelephonyManager.NETWORK_TYPE_EVDO_0};
        for (int networkType : netTypes) {
            cellInfo.setCellLocation(gcl, networkType, "123456", 5);
            assertEquals(CellInfo.CELL_RADIO_CDMA, cellInfo.getCellRadio());
        }
    }
    
}