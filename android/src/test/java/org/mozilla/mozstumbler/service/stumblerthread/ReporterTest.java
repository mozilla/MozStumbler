package org.mozilla.mozstumbler.service.stumblerthread;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.telephony.TelephonyManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.DataStorageManager;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.StumblerBundle;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.GPSScanner;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.WifiScanner;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.cellscanner.CellInfo;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.cellscanner.CellScanner;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Constructor;
import java.util.ArrayList;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class ReporterTest {

    private Context ctx;
    private Reporter rp;

    @SuppressWarnings("unused")
    private DataStorageManager dm;

    public static CellInfo createCellInfo(int mcc, int mnc, int lac, int cid, int asu) {
        CellInfo cell = new CellInfo();
        cell.setGsmCellInfo(mcc, mnc, lac, cid, asu);
        return cell;
    }

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

    public static Intent getLocationIntent(double lat, double lon) {

        if (lat == 0) {
            lat = 20;
        }
        if (lon == 0) {
            lon = 30;
        }
        Location location = new Location("mock");
        location.setLongitude(lat);
        location.setLatitude(lon);
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

        rp = new Reporter();

        // The Reporter class needs a reference to a context
        rp.startup(ctx);

        Intent gpsUpdated = getLocationIntent(0, 0);
        rp.onReceive(ctx, gpsUpdated);
        assertTrue(null != rp.getGPSLocation());
    }

    @Test
    public void testReporterWifiLimits() {
        // Spam the Reporter with wifi data
        ArrayList<String> bssidList = new ArrayList<String>();

        for (int offset = 0; offset < StumblerBundle.MAX_WIFIS_PER_LOCATION - 1; offset++) {
            String bssid = Long.toHexString(offset | 0xabcd00000000L);
            bssidList.add(bssid);
        }
        String[] bssidArray = bssidList.toArray(new String[bssidList.size()]);


        // This should push the reporter into a state that forces a
        // flush on the next wifi record.
        Intent wifiIntent = getWifiIntent(bssidArray);
        rp.onReceive(ctx, wifiIntent);
        assertEquals(StumblerBundle.MAX_WIFIS_PER_LOCATION - 1,
                rp.mBundle.getUnmodifiableWifiData().size());

        bssidArray = new String[]{Long.toHexString(0xabcd99999999L)};
        wifiIntent = getWifiIntent(bssidArray);
        // This will force a flush and the bundle should go to null
        rp.onReceive(ctx, wifiIntent);
        assertNull(rp.mBundle);
    }

    @Test
    public void testReporterCellLimits() {
        // Spam the Reporter with cell data
        ArrayList<CellInfo> cellIdList = new ArrayList<CellInfo>();

        for (int offset = 0; offset < StumblerBundle.MAX_CELLS_PER_LOCATION - 1; offset++) {
            CellInfo cell = createCellInfo(1, 1, 2000 + offset, 1600199 + offset, 19);
            cellIdList.add(cell);
        }

        Intent cellIntent = getCellIntent(cellIdList);
        rp.onReceive(ctx, cellIntent);
        assertEquals(StumblerBundle.MAX_CELLS_PER_LOCATION - 1,
                rp.mBundle.getUnmodifiableCellData().size());

        cellIdList.clear();
        CellInfo cell = createCellInfo(1, 1, 2000 + StumblerBundle.MAX_CELLS_PER_LOCATION + 1,
                1600199 + StumblerBundle.MAX_CELLS_PER_LOCATION + 1, 19);
        cellIdList.add(cell);
        cellIntent = getCellIntent(cellIdList);
        // This will force a flush and the bundle should go to null
        rp.onReceive(ctx, cellIntent);
        assertEquals(null, rp.mBundle);
    }

    private Intent getCellIntent(ArrayList<CellInfo> cells) {
        long curTime = System.currentTimeMillis();
        Intent intent = new Intent(CellScanner.ACTION_CELLS_SCANNED);
        intent.putParcelableArrayListExtra(CellScanner.ACTION_CELLS_SCANNED_ARG_CELLS, cells);
        intent.putExtra(CellScanner.ACTION_CELLS_SCANNED_ARG_TIME, curTime);
        return intent;
    }

    private Intent getWifiIntent(String[] bssids) {
        ArrayList<ScanResult> scanResults = new ArrayList<ScanResult>();

        ScanResult scan;
        for (String bssid : bssids) {
            scan = createScanResult(bssid, "caps", 3, 11, 10);
            scanResults.add(scan);
        }

        Intent i = new Intent(WifiScanner.ACTION_WIFIS_SCANNED);
        i.putParcelableArrayListExtra(WifiScanner.ACTION_WIFIS_SCANNED_ARG_RESULTS, scanResults);
        i.putExtra(WifiScanner.ACTION_WIFIS_SCANNED_ARG_TIME, System.currentTimeMillis());
        return i;
    }

    private Application getApplicationContext() {
        return Robolectric.application;
    }

    public class StorageTracker implements DataStorageManager.StorageIsEmptyTracker {
        public void notifyStorageStateEmpty(boolean isEmpty) {
        }
    }
}
