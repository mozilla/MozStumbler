package org.mozilla.mozstumbler.client.mapview;

import android.location.Location;
import android.location.LocationManager;
import android.net.wifi.ScanResult;

import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.StumblerBundle;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.cellscanner.CellInfo;
import org.mozilla.osmdroid.util.GeoPoint;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Constructor;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.fail;

@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
public class ObservationPointTest {

    @Test
    public void testSetCounts() {
        Location mockLocation = new Location(LocationManager.GPS_PROVIDER);

        {
            StumblerBundle bundle = new StumblerBundle(mockLocation);
            testObservationCounts(bundle, 0, 0); // empty

            for (String bssid : new String[]{"01:23:45:67:89:ab", "23:45:67:89:ab:cd"}) {
                ScanResult scanResult = createScanResult(bssid, "", 0, 0, 0);
                bundle.addWifiData(bssid, scanResult);
            }
            testObservationCounts(bundle, 0, 2); // wifi only

            CellInfo cellInfo = createLteCellInfo(208, 1, 12345, CellInfo.UNKNOWN_CID, 2, 31, 1);
            bundle.addCellData(cellInfo.getCellIdentity(), cellInfo);
            testObservationCounts(bundle, 1, 2); // wifi and cell
        }

        {
            StumblerBundle bundle = new StumblerBundle(mockLocation);
            CellInfo cellInfo = createLteCellInfo(208, 1, 12345, CellInfo.UNKNOWN_CID, 2, 31, 1);
            bundle.addCellData(cellInfo.getCellIdentity(), cellInfo);
            testObservationCounts(bundle, 1, 0); // cell only
        }
    }

    private void testObservationCounts(StumblerBundle bundle, int cells, int wifis) {
        ObservationPoint observation = new ObservationPoint(new GeoPoint(bundle.getGpsPosition()));
        try {
            observation.setCounts(bundle.toMLSGeosubmit());
        } catch (JSONException e) {
            fail(e.toString());
        }
        assertEquals(cells, observation.mCellCount);
        assertEquals(wifis, observation.mWifiCount);
    }

    // see StumblerBundleTest
    public static CellInfo createLteCellInfo(int mcc,
                                             int mnc,
                                             int cid,
                                             int psc,
                                             int lac,
                                             int asu,
                                             int ta) {
        CellInfo cell = new CellInfo();
        cell.setLteCellInfo(mcc, mnc, cid, psc, lac, asu, ta);
        cell.setSignalStrength(-51);

        return cell;
    }

    // see StumblerBundleTest
    public static ScanResult createScanResult(String BSSID, String caps, int level, int frequency,
                                              long tsf) {
        Class<?> c = null;
        try {
            c = Class.forName("android.net.wifi.ScanResult");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Error loading ScanResult class");
        }
        Constructor[] constructors = c.getConstructors();

        Constructor<?> myConstructor = null;
        for (Constructor<?> construct : constructors) {
            if (construct.getParameterTypes().length == 6) {
                myConstructor = construct;
                break;
            }
        }

        if (myConstructor == null) {
            throw new RuntimeException("No constructor found");
        }
        ScanResult scan = null;
        try {
            scan = (ScanResult) myConstructor.newInstance(null, BSSID, caps, level, frequency, tsf);
        } catch (Exception e) {
            throw new RuntimeException(e.toString());
        }
        return scan;
    }
}
