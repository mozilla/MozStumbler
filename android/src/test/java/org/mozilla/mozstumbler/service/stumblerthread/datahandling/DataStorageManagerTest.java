package org.mozilla.mozstumbler.service.stumblerthread.datahandling;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.telephony.TelephonyManager;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.core.logging.Log;
import org.mozilla.mozstumbler.service.stumblerthread.Reporter;
import org.mozilla.mozstumbler.service.stumblerthread.ReporterTest;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.GPSScanner;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.cellscanner.CellInfo;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.mozilla.mozstumbler.service.stumblerthread.ReporterTest.createCellInfo;
import static org.mozilla.mozstumbler.service.stumblerthread.ReporterTest.createScanResult;

@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
public class DataStorageManagerTest {

    private String LOG_TAG = AppGlobals.makeLogTag(DataStorageManagerTest.class);

    public class StorageTracker implements DataStorageManager.StorageIsEmptyTracker {
        public void notifyStorageStateEmpty(boolean isEmpty) {
        }
    }
    private DataStorageManager dm;
    private Reporter rp;
    private Context ctx;

    private Application getApplicationContext() {
        return Robolectric.application;
    }

    private Intent getLocationIntent() {
        Location location = new Location("mock");
        location.setLongitude(20);
        location.setLatitude(30);
        Intent i = new Intent(GPSScanner.ACTION_GPS_UPDATED);
        i.putExtra(Intent.EXTRA_SUBJECT, GPSScanner.SUBJECT_NEW_LOCATION);
        i.putExtra(GPSScanner.NEW_LOCATION_ARG_LOCATION, location);
        i.putExtra(GPSScanner.ACTION_ARG_TIME, System.currentTimeMillis());
        return i;
    }

    @Before
    public void setUp() {
        ctx = getApplicationContext();

        StorageTracker tracker = new StorageTracker();

        long maxBytes = 20000;
        int maxWeeks = 10;

        // The DM is required to handle the flush() operation in the Reporter.
        dm = DataStorageManager.createGlobalInstance(ctx, tracker, maxBytes, maxWeeks);

        // Reports are muddled because of the ReporterTest cases for some reason.
        while (dm.mCurrentReports.reports.size() > 0) {
            dm.mCurrentReports.reports.remove(0);
        }

        rp = new Reporter();

        // The Reporter class needs a reference to a context
        rp.startup(ctx);
        assertEquals(0, dm.mCurrentReports.reports.size());
    }

    @Test
    public void testMaxReportsLength() throws JSONException {
        StumblerBundle bundle;

        assertEquals(0, dm.mCurrentReports.reports.size());
        for (int locCount = 0; locCount < DataStorageManager.MAX_REPORTS_IN_MEMORY-1; locCount++) {
            Location loc = new Location("mock");
            loc.setLatitude(42+(locCount*0.1));
            loc.setLongitude(45+(locCount*0.1));

            bundle = new StumblerBundle(loc, TelephonyManager.PHONE_TYPE_GSM);

            for (int offset = 0; offset< StumblerBundle.MAX_WIFIS_PER_LOCATION*20; offset++) {
                String bssid = Long.toHexString(offset | 0xabcd00000000L);
                ScanResult scan = createScanResult(bssid, "caps", 3, 11, 10);
                bundle.addWifiData(bssid, scan);
            }

            for (int offset = 0; offset< StumblerBundle.MAX_CELLS_PER_LOCATION*20; offset++) {
                CellInfo cell = createCellInfo(1, 1, 2000 + offset, 1600199 + offset, 19);
                String key = cell.getCellIdentity();
                bundle.addCellData(key, cell);
            }

            JSONObject mlsObj = bundle.toMLSJSON();
            int wifiCount = mlsObj.getInt(DataStorageContract.ReportsColumns.WIFI_COUNT);
            int cellCount = mlsObj.getInt(DataStorageContract.ReportsColumns.CELL_COUNT);
            try {
                dm.insert(mlsObj.toString(), wifiCount, cellCount);
            } catch (IOException ioEx) {
            }
        }
        assertEquals(DataStorageManager.MAX_REPORTS_IN_MEMORY-1, dm.mCurrentReports.reports.size());


        for (int locCount = DataStorageManager.MAX_REPORTS_IN_MEMORY;
             locCount < DataStorageManager.MAX_REPORTS_IN_MEMORY+100;
             locCount++) {
            Location loc = new Location("mock");
            loc.setLatitude(42+(locCount*0.1));
            loc.setLongitude(45+(locCount*0.1));

            bundle = new StumblerBundle(loc, TelephonyManager.PHONE_TYPE_GSM);

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

            JSONObject mlsObj = bundle.toMLSJSON();
            int wifiCount = mlsObj.getInt(DataStorageContract.ReportsColumns.WIFI_COUNT);
            int cellCount = mlsObj.getInt(DataStorageContract.ReportsColumns.CELL_COUNT);
            try {
                dm.insert(mlsObj.toString(), wifiCount, cellCount);
            } catch (FileNotFoundException fex) {
                // This is ok
            } catch (IOException ioEx) {
            }

            assertTrue(dm.mCurrentReports.reports.size() < DataStorageManager.MAX_REPORTS_IN_MEMORY);

            // I think we can basically reproduce this if DataStorageManager.mBundle is set
            // then we call flush() and eat the FileNotFoundException
        }
    }

}
