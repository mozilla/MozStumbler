/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.mozstumbler.tests;

import android.content.Intent;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.telephony.TelephonyManager;
import android.test.ServiceTestCase;
import android.util.Log;

import org.mozilla.mozstumbler.service.utils.AbstractCommunicator;
import org.mozilla.mozstumbler.service.AppGlobals;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.ScanManager;
import org.mozilla.mozstumbler.service.stumblerthread.StumblerService;
import org.mozilla.mozstumbler.service.stumblerthread.datahandling.DataStorageManager;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.GPSScanner;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.WifiScanner;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.cellscanner.CellInfo;
import org.mozilla.mozstumbler.service.stumblerthread.scanners.cellscanner.CellScanner;
import org.mozilla.mozstumbler.service.uploadthread.AsyncUploader;
import org.mozilla.mozstumbler.service.uploadthread.AsyncUploader.UploadSettings;
import org.mozilla.mozstumbler.service.utils.Zipper;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

public class ServiceTest extends ServiceTestCase<StumblerService> implements AsyncUploader.AsyncUploaderListener {

    public ServiceTest() {
        super(StumblerService.class);
    }

    @Override
    public void onUploadProgress() {}

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    // setActivityIntent
    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    Object getScanner(ScanManager scanManager, String privateMember) throws NoSuchFieldException, IllegalAccessException {
        Field field = ScanManager.class.getDeclaredField(privateMember);
        field.setAccessible(true);
        return field.get(scanManager);
    }


    public StumblerService startPassiveService() {
        Intent intent = new Intent(getSystemContext(), StumblerService.class);
        intent.putExtra(StumblerService.ACTION_START_PASSIVE, true);
        super.startService(intent);
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        StumblerService service = getService();
        assertNotNull(service);
        return service;
    }

    static double c = 0.01;
    public void sendLocation(ScanManager scanManager) throws NoSuchFieldException, IllegalAccessException, InterruptedException {
        Location loc = new Location("gps");
        loc.setLatitude(89.9 + c);
        c += 0.01;
        loc.setLongitude(-179.9);
        loc.setTime(System.currentTimeMillis());

        GPSScanner gpsScanner = (GPSScanner) getScanner(scanManager, "mGPSScanner");
        gpsScanner.onLocationChanged(loc);
    }

    public void checkWifi_nomap(WifiScanner wifiScanner) {
        try {
            ScanResult sr = makeScanResult();
            sr.BSSID = "00:00:00:00:00:11";
            sr.SSID = "test of _nomap";

            Set<String> wifiOrig = wifiScanner.getAccessPoints(this);

            wifiScanner.mTestModeFakeScanResults.clear();
            wifiScanner.mTestModeFakeScanResults.add(sr);

            Intent i = new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
            wifiScanner.onReceive(mContext, i);

            Set<String> wifis = wifiScanner.getAccessPoints(this);
            assertTrue(wifis.size() == wifiOrig.size());
        } catch (Exception ex) {
            assertTrue(false);
        }
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

    CellInfo makeCellInfo(int mcc, int mnc, int lac, int cid, int asu) throws IllegalAccessException, InvocationTargetException {
        CellInfo cell = new CellInfo(TelephonyManager.PHONE_TYPE_GSM);

        Method method = getMethod(CellInfo.class, "setGsmCellInfo");
        assert (method != null);
        method.invoke(cell, mcc, mnc, lac, cid, asu);
        return cell;
    }

    // https://github.com/mozilla/ichnaea/issues/247
    public void sendWifiAndCell(WifiScanner wifiScanner, CellScanner cellScanner) {
        try {
            ScanResult sr = makeScanResult();
            sr.BSSID = "01:00:5e:90:10:00"; // known test value on server
            sr.SSID = "log_me";

            wifiScanner.mTestModeFakeScanResults.add(sr);

            Intent i = new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
            wifiScanner.onReceive(mContext, i);

            Set<String> wifis = wifiScanner.getAccessPoints(this);
            assertTrue(wifis.size() == 1);

            cellScanner.sTestingModeCellInfoArray = new ArrayList<CellInfo>();
            cellScanner.sTestingModeCellInfoArray.add(makeCellInfo(1, 1, 60330, 1660199, 19));
            cellScanner.sTestingModeCellInfoArray.add(makeCellInfo(1, 1, -1, -1, -1));
        } catch (Exception ex) {
            ex.printStackTrace();
            assertTrue(false);
        }
    }

    /* This rather fragile, and is ok for test, but is there a better way to do this? */
    public ScanResult makeScanResult() throws IllegalAccessException, InstantiationException, InvocationTargetException {
        Constructor<?>[] ctors = ScanResult.class.getDeclaredConstructors();
        assertTrue(ctors.length > 0);
        for (Constructor<?> ctor : ctors) {
            int len = ctor.getParameterTypes().length;
            if (len == 5) {
                ctor.setAccessible(true);
                return (ScanResult) ctor.newInstance("", "", "", 0, 0);
            }
            if (len == 6) { // Android 4.4.3 has this constructor
                ctor.setAccessible(true);
                return (ScanResult) ctor.newInstance(null, "", "", 0, 0, 0);
            }
        }
        assertTrue(false);
        return null;
    }


    // for pausing to wait for asynctask
    final CountDownLatch signal = new CountDownLatch(1);
    public void onUploadComplete(AbstractCommunicator.SyncSummary result) {
        signal.countDown();
    }

    /*
    public void testPassiveService() {
        WifiScanner.sIsTestMode = true;
        ScanManager scanManager = null;
        WifiScanner wifiScanner = null;
        CellScanner cellScanner = null;
        StumblerService service = startPassiveService();
        assertTrue(DataStorageManager.getInstance() != null);
        try {

            Field field = StumblerService.class.getDeclaredField("mScanManager");
            field.setAccessible(true);
            scanManager = (ScanManager) field.get(service);
            wifiScanner = (WifiScanner) getScanner(scanManager, "mWifiScanner");
            cellScanner = (CellScanner) getScanner(scanManager, "mCellScanner");
        } catch (Exception ex) {
            assertTrue(false);
            return;
        }

        for (int i = 0; i < 2; i++) {
            try {
                sendLocation(scanManager);
            } catch (Exception ex) {}
            sendWifiAndCell(wifiScanner, cellScanner);
            Intent intent = new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
            wifiScanner.onReceive(mContext, intent);
            // now wait for cell scanner thread to run and pick up changes
            try {
                Thread.sleep(3200); // 3 seconds between gps locations
            } catch (Exception ex) {}
        }

        try {
            sendLocation(scanManager);
            Thread.sleep(5000);
        } catch (Exception ex) {}

        try {
            DataStorageManager.ReportBatch batch = DataStorageManager.getInstance().getFirstBatch();
            Log.d("test", "c:" + batch.reportCount);
            String val = Zipper.unzipData(batch.data);
            Log.d("test", "d:" + val);
        } catch (IOException ex) {
            assertTrue(false);
        }

    //    assertTrue(s.equals("[{\"asu\":19,\"radio\":\"gsm\",\"mnc\":1,\"cid\":1660199,\"mcc\":1,\"lac\":60330},{\"asu\":-1,\"mcc\":1,\"radio\":\"gsm\",\"mnc\":1}]"));
      //  assertTrue(s.equals("[{\"signal\":0,\"key\":\"01005e901000\",\"frequency\":0}]"));

        UploadSettings settings = new UploadSettings(true, false);
        AsyncUploader upper = new AsyncUploader(settings, this);
        upper.execute();
        try {
            signal.await();
        } catch (Exception ex) {}

        checkWifi_nomap(wifiScanner);

        Log.d(AppGlobals.appName, "•••••••••••••••••••• done passive scan test •••••••••••••••");
    }
    */
}
