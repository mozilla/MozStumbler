package org.mozilla.mozstumbler.service.stumblerthread.datahandling;

import android.app.Application;
import android.content.Context;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.telephony.TelephonyManager;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mozilla.mozstumbler.service.stumblerthread.Reporter;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.cellscanner.CellInfo;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.FileNotFoundException;
import java.io.IOException;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.mozilla.mozstumbler.service.stumblerthread.ReporterTest.createCellInfo;
import static org.mozilla.mozstumbler.service.stumblerthread.ReporterTest.createScanResult;

@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
public class DataStorageManagerTest {

    private DataStorageManager dm;
    private Reporter rp;
    private Context ctx;

    private Application getApplicationContext() {
        return Robolectric.application;
    }

    @Before
    public void setUp() {
        ctx = getApplicationContext();

        StorageTracker tracker = new StorageTracker();

        long maxBytes = 20000;
        int maxWeeks = 10;

        dm = ClientDataStorageManager.createGlobalInstance(ctx, tracker, maxBytes, maxWeeks);
        // Force the current reports to clear out between test runs.
        dm.mCurrentReports.clearReports();

        rp = new Reporter();

        // The Reporter class needs a reference to a context
        rp.startup(ctx);
        assertEquals(0, dm.mCurrentReports.reportsCount());
    }

    @Test
    public void testMaxReportsLength() throws JSONException {
        StumblerBundle bundle;

        assertEquals(0, dm.mCurrentReports.reportsCount());
        for (int locCount = 0; locCount < ReportBatchBuilder.MAX_REPORTS_IN_MEMORY - 1; locCount++) {
            Location loc = new Location("mock");
            loc.setLatitude(42 + (locCount * 0.1));
            loc.setLongitude(45 + (locCount * 0.1));

            bundle = new StumblerBundle(loc);

            for (int offset = 0; offset < StumblerBundle.MAX_WIFIS_PER_LOCATION * 20; offset++) {
                String bssid = Long.toHexString(offset | 0xabcd00000000L);
                ScanResult scan = createScanResult(bssid, "caps", 3, 11, 10);
                bundle.addWifiData(bssid, scan);
            }

            for (int offset = 0; offset < StumblerBundle.MAX_CELLS_PER_LOCATION * 20; offset++) {
                CellInfo cell = createCellInfo(1, 1, 2000 + offset, 1600199 + offset, 19);
                String key = cell.getCellIdentity();
                bundle.addCellData(key, cell);
            }

            JSONObject mlsObj = bundle.toMLSGeosubmit();
            int wifiCount = mlsObj.getJSONArray(DataStorageContract.ReportsColumns.WIFI).length();
            int cellCount = mlsObj.getJSONArray(DataStorageContract.ReportsColumns.CELL).length();
            try {
                dm.insert(mlsObj.toString(), wifiCount, cellCount);
            } catch (IOException ioEx) {
            }
        }

        assertEquals(ReportBatchBuilder.MAX_REPORTS_IN_MEMORY - 1,
                dm.mCurrentReports.reportsCount());


        for (int locCount = ReportBatchBuilder.MAX_REPORTS_IN_MEMORY;
             locCount < ReportBatchBuilder.MAX_REPORTS_IN_MEMORY + 100;
             locCount++) {
            Location loc = new Location("mock");
            loc.setLatitude(42 + (locCount * 0.1));
            loc.setLongitude(45 + (locCount * 0.1));

            bundle = new StumblerBundle(loc);

            for (int offset = 0; offset < StumblerBundle.MAX_WIFIS_PER_LOCATION * 20; offset++) {
                String bssid = Long.toHexString(offset | 0xabcd00000000L);
                ScanResult scan = createScanResult(bssid, "caps", 3, 11, 10);
                bundle.addWifiData(bssid, scan);
            }

            for (int offset = 0; offset < StumblerBundle.MAX_CELLS_PER_LOCATION * 20; offset++) {
                CellInfo cell = createCellInfo(1, 1, 2000 + offset, 1600199 + offset, 19);
                String key = cell.getCellIdentity();
                bundle.addCellData(key, cell);
            }

            JSONObject mlsObj = bundle.toMLSGeosubmit();
            int wifiCount = mlsObj.getJSONArray(DataStorageContract.ReportsColumns.WIFI).length();
            int cellCount = mlsObj.getJSONArray(DataStorageContract.ReportsColumns.CELL).length();
            try {
                dm.insert(mlsObj.toString(), wifiCount, cellCount);
            } catch (FileNotFoundException fex) {
                // This is ok
            } catch (IOException ioEx) {
            }

            assertTrue(dm.mCurrentReports.reportsCount() == ReportBatchBuilder.MAX_REPORTS_IN_MEMORY);
        }
    }

    public class StorageTracker implements DataStorageManager.StorageIsEmptyTracker {
        public void notifyStorageStateEmpty(boolean isEmpty) {
        }
    }
}
