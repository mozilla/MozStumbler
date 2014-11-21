package org.mozilla.mozstumbler.service.stumblerthread.datahandling;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.telephony.TelephonyManager;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mozilla.mozstumbler.service.stumblerthread.Reporter;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.GPSScanner;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.WifiScanner;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.cellscanner.CellInfo;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.cellscanner.CellScanner;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;


import static junit.framework.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class DataStorageManagerTest {

    private Context ctx;
    private DataStorageManager dm;
    private Reporter rp;

    public class StorageTracker implements DataStorageManager.StorageIsEmptyTracker {
        public void notifyStorageStateEmpty(boolean isEmpty) {
        }
    }

    @Before
    public void setUp() {
        ctx = getApplicationContext();

        StorageTracker tracker = new StorageTracker();

        long maxBytes = 20000;
        int maxWeeks = 10;

        dm = ClientDataStorageManager.createGlobalInstance(ctx, tracker, maxBytes, maxWeeks);
        rp = new Reporter();

        // The Reporter class needs a reference to a context
        rp.startup(ctx);

        Intent gpsUpdated = getLocationIntent();
        rp.onReceive(ctx, gpsUpdated);
        assertTrue(null != rp.getGPSLocation());
    }

    @Test
    public void testReporterWifiLimits() {
        // Spam the Reporter with wifi data
        ArrayList<String> bssidList = new ArrayList<String>();

        for (int offset = 0; offset< 40000; offset++) {
            String bssid = Long.toHexString(offset | 0xabcd00000000L);
            bssidList.add(bssid);
        }
        String[] bssidArray  = bssidList.toArray(new String[bssidList.size()]);
        Intent wifiIntent = getWifiIntent(bssidArray);

        Map<String, ScanResult> wifiData = rp.getWifiData();
        Assert.assertTrue("Max wifi limit is exceeded", wifiData.size() <= Reporter.MAX_WIFIS_PER_LOCATION);

        // This should push the reporter into a state that forces a
        // flush
        rp.onReceive(ctx, wifiIntent);

        wifiData = rp.getWifiData();
        Assert.assertTrue("Max wifi limit exceeded", wifiData == null);
    }

    @Test
    public void testReporterCellLimits() {
        // Spam the Reporter with cell data
        ArrayList<CellInfo> cellIdList = new ArrayList<CellInfo>();

        for (int offset = 100; offset< 40000; offset++) {
            CellInfo cell = makeCellInfo(1, 1, 2000+offset, 1600199+offset, 19);
            cellIdList.add(cell);
        }

        Intent cellIntent = getCellIntent(cellIdList);
        Map<String, CellInfo> cellData = rp.getCellData();
        Assert.assertTrue("Max cell limit exceeded", cellData.size() < Reporter.MAX_CELLS_PER_LOCATION);

        // Accepting the extra content into the Report should force
        // the Reporter to flush content
        rp.onReceive(ctx, cellIntent);

        cellData = rp.getCellData();
        Assert.assertTrue("Cell data was not flushed properly", cellData == null);
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
        for (String bssid: bssids) {
            scan = createScanResult(bssid, "caps", 3, 11, 10);
            scanResults.add(scan);
        }

        Intent i = new Intent(WifiScanner.ACTION_WIFIS_SCANNED);
        i.putParcelableArrayListExtra(WifiScanner.ACTION_WIFIS_SCANNED_ARG_RESULTS, scanResults);
        i.putExtra(WifiScanner.ACTION_WIFIS_SCANNED_ARG_TIME, System.currentTimeMillis());
        return i;
    }

    CellInfo makeCellInfo(int mcc, int mnc, int lac, int cid, int asu) {
        CellInfo cell = new CellInfo(TelephonyManager.PHONE_TYPE_GSM);
        Method method = getMethod(CellInfo.class, "setGsmCellInfo");
        assert (method != null);
        try {
            method.invoke(cell, mcc, mnc, lac, cid, asu);
        } catch (Exception e) {
            throw new RuntimeException(e.toString());
        }
        return cell;
    }

    Method getMethod(Class<?> c, String name) {
        Method[] methods = c.getDeclaredMethods();
        for (Method m : methods) {
            if (m.getName().contains(name)) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    ScanResult createScanResult(String BSSID, String caps, int level, int frequency,
                                        long tsf) {
        Class<?> c = null;
        try {
            c = Class.forName("android.net.wifi.ScanResult");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Error loading ScanResult class");
        }
        Constructor[] constructors = c.getConstructors();

        Constructor<?> myConstructor= null;
        for (Constructor<?> construct: constructors) {
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

    private Application getApplicationContext() {
        return Robolectric.application;
    }

}
