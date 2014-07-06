package org.mozilla.mozstumbler.tests;

import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.telephony.TelephonyManager;
import android.test.ServiceTestCase;
import android.util.Log;

import org.mozilla.mozstumbler.client.datahandling.ClientContentResolver;
import org.mozilla.mozstumbler.service.Scanner;
import org.mozilla.mozstumbler.service.SharedConstants;
import org.mozilla.mozstumbler.service.StumblerService;
import org.mozilla.mozstumbler.service.datahandling.Database;
import org.mozilla.mozstumbler.service.datahandling.DatabaseContract;
import org.mozilla.mozstumbler.service.scanners.GPSScanner;
import org.mozilla.mozstumbler.service.scanners.WifiScanner;
import org.mozilla.mozstumbler.service.scanners.cellscanner.CellInfo;
import org.mozilla.mozstumbler.service.scanners.cellscanner.CellScanner;
import org.mozilla.mozstumbler.service.sync.AsyncUploader;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

public class ServiceTest extends ServiceTestCase<StumblerService> implements AsyncUploader.AsyncUploaderListener {

    public ServiceTest() {
        super(StumblerService.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        String path = mContext.getFilesDir().getPath();
        SQLiteDatabase db = SQLiteDatabase.openDatabase(path + "/../databases/stumbler.db", null, 0);
        db.execSQL("delete from " + Database.TABLE_REPORTS);
        db.close();
    }

    // setActivityIntent
    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    Object getScanner(Scanner scanner, String privateMember) throws NoSuchFieldException, IllegalAccessException {
        Field field = Scanner.class.getDeclaredField(privateMember);
        field.setAccessible(true);
        return field.get(scanner);
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

    public void sendLocation(Scanner scanner) throws NoSuchFieldException, IllegalAccessException {
        Location loc = new Location("gps");
        loc.setLatitude(89.9);
        loc.setLongitude(-179.9);
        loc.setTime(System.currentTimeMillis());

        GPSScanner gpsScanner = (GPSScanner) getScanner(scanner, "mGPSScanner");
        gpsScanner.onLocationChanged(loc);

    }

    public void checkWifi_nomap(WifiScanner wifiScanner) {
        try {
            ScanResult sr = makeScanResult();
            sr.BSSID = "00:00:00:00:00:11";
            sr.SSID = "test of _nomap";

            wifiScanner.mTestModeFakeScanResults.add(sr);

            Intent i = new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
            wifiScanner.onReceive(mContext, i);

            Set<String> wifis = wifiScanner.getAccessPoints(this);
            assertTrue(wifis.size() == 0);
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
            sr.SSID = "log me";

            wifiScanner.mTestModeFakeScanResults.add(sr);

            Intent i = new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
            wifiScanner.onReceive(mContext, i);

            Set<String> wifis = wifiScanner.getAccessPoints(this);
            assertTrue(wifis.size() == 1);

            cellScanner.sTestingModeCellInfoArray = new ArrayList<CellInfo>();
            cellScanner.sTestingModeCellInfoArray.add(makeCellInfo(1, 1, 60330, 1660199, 19));
            cellScanner.sTestingModeCellInfoArray.add(makeCellInfo(1, 1, -1, -1, -1));
            // now wait for cell scanner thread to run and pick up changes
            Thread.sleep(2000);
        } catch (Exception ex) {
            ex.printStackTrace();
            assertTrue(false);
        }
    }

    /** This rather fragile, and is ok for test, but is there a better way to do this? */
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

    SQLiteDatabase getStumblerDatabase() {
        String dbPath = Database.getFullPathToDb(this);
        assertTrue(dbPath != null);
        int endPos = dbPath.lastIndexOf('/');
        dbPath = dbPath.substring(0, endPos) + "/" + Database.DATABASE_NAME;
        SQLiteDatabase db = SQLiteDatabase.openDatabase(dbPath, null, 0);
        return db;
    }

    // for pausing to wait for asynctask
    final CountDownLatch signal = new CountDownLatch(1);
    public void onUploadComplete(SyncResult result) {
        signal.countDown();
    }

    public void getCellAndWifiCountFromReportsLastRow(SQLiteDatabase db, long[] cellAndWifi) {
        Cursor cursor = db.rawQuery("select * from " + Database.TABLE_REPORTS, null);
        if (cursor.getCount() < 1) {
            cursor.close();
            return;
        }
        cursor.moveToLast();
        cellAndWifi[0] = cursor.getLong(cursor.getColumnIndex(DatabaseContract.Reports.CELL_COUNT));
        cellAndWifi[1] = cursor.getLong(cursor.getColumnIndex(DatabaseContract.Reports.WIFI_COUNT));
        cursor.close();
    }

    public Map<String, Long> getStats(SQLiteDatabase db) {
        Cursor cursor = db.rawQuery("select * from " + Database.TABLE_STATS, null);
        assertTrue(cursor.getCount() > 0);
        cursor.moveToFirst();
        Map<String, Long> kv = new HashMap<String, Long>();
        while (!cursor.isAfterLast()) {
            String key = cursor.getString(cursor.getColumnIndex(DatabaseContract.Stats.KEY));
            Long value = cursor.getLong(cursor.getColumnIndex(DatabaseContract.Stats.VALUE));
            kv.put(key, value);
            cursor.moveToNext();
        }
        cursor.close();
        assertTrue(kv.size() == 4);
        return kv;
    }

    public void testPassiveService() {
        WifiScanner.sIsTestMode = true;

        StumblerService service = startPassiveService();
        SharedConstants.stumblerContentResolver= new ClientContentResolver(getContext().getContentResolver());
        try {
            SQLiteDatabase db = getStumblerDatabase();
            long[] cellAndWifiOrig = new long[2];
            getCellAndWifiCountFromReportsLastRow(db, cellAndWifiOrig);

            Field field = StumblerService.class.getDeclaredField("mScanner");
            field.setAccessible(true);
            Scanner scanner = (Scanner) field.get(service);

            sendLocation(scanner);

            WifiScanner wifiScanner = (WifiScanner) getScanner(scanner, "mWifiScanner");

            checkWifi_nomap(wifiScanner);

            CellScanner cellScanner = (CellScanner) getScanner(scanner, "mCellScanner");

            final int kNewCells = 2;
            final int kNewWifis = 1;
            sendWifiAndCell(wifiScanner, cellScanner);

            for (int i = 0; i < 3; i++) {
                sendLocation(scanner);
                Intent intent = new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
                wifiScanner.onReceive(mContext, intent);

                //sendWifiAndCell(wifiScanner, cellScanner);
            }

            Thread.sleep(3500);

            long[] cellAndWifi = new long[2];
            getCellAndWifiCountFromReportsLastRow(db, cellAndWifi);
            assertTrue(cellAndWifi[0] == cellAndWifiOrig[0] + kNewCells);
            assertTrue(cellAndWifi[1] == cellAndWifiOrig[1] + kNewWifis);

            Cursor cursor = db.rawQuery("select * from " + Database.TABLE_REPORTS, null);
            assertTrue(cursor.getCount() > 0);
            cursor.moveToLast();
            String s = cursor.getString(cursor.getColumnIndex(DatabaseContract.Reports.CELL));
            assertTrue(s.equals("[{\"asu\":19,\"radio\":\"gsm\",\"mnc\":1,\"cid\":1660199,\"mcc\":1,\"lac\":60330},{\"asu\":-1,\"mcc\":1,\"radio\":\"gsm\",\"mnc\":1}]"));
            s = cursor.getString(cursor.getColumnIndex(DatabaseContract.Reports.WIFI));
            assertTrue(s.equals("[{\"signal\":0,\"key\":\"01005e901000\",\"frequency\":0}]"));
            cursor.close();

            Map<String, Long> statsMap = getStats(db);
            long sentObsOrig = statsMap.get(DatabaseContract.Stats.KEY_OBSERVATIONS_SENT);
            long sentCellsOrig = statsMap.get(DatabaseContract.Stats.KEY_CELLS_SENT);
            long sentWifisOrig = statsMap.get(DatabaseContract.Stats.KEY_WIFIS_SENT);

            AsyncUploader upper = new AsyncUploader(this);
            upper.mShouldIgnoreWifiStatus = true;
            upper.execute();
            signal.await();
            SyncResult syncResult = upper.getSyncResult();
            assertTrue(!syncResult.hasError() && !syncResult.madeSomeProgress());

            statsMap = getStats(db);
            long sentObs = statsMap.get(DatabaseContract.Stats.KEY_OBSERVATIONS_SENT);
            long sentCells = statsMap.get(DatabaseContract.Stats.KEY_CELLS_SENT);
            long sentWifis = statsMap.get(DatabaseContract.Stats.KEY_WIFIS_SENT);

            assertTrue(sentObs - sentObsOrig > 5);
            assertTrue(sentCells - sentCellsOrig >= kNewCells);
            assertTrue(sentWifis - sentWifisOrig >= kNewWifis);

            Log.d(SharedConstants.appName, "•••••••••••••••••••• done passive scan test •••••••••••••••");

        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(false);
        }
    }
}
