package org.mozilla.mozstumbler.tests;

import android.content.ContentValues;
import android.content.SyncResult;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Message;
import android.test.ApplicationTestCase;
import org.mozilla.mozstumbler.client.MainApp;
import org.mozilla.mozstumbler.service.datahandling.Database;
import org.mozilla.mozstumbler.service.datahandling.DatabaseContract;

import java.util.concurrent.CountDownLatch;

public class AppTest extends ApplicationTestCase<MainApp> {
    public AppTest() {
        super(MainApp.class);
    }

    SQLiteDatabase getStumblerDatabase() {
        String dbPath = Database.getFullPathToDb(this);
        assertTrue(dbPath != null);
        int endPos = dbPath.lastIndexOf('/');
        dbPath = dbPath.substring(0, endPos) + "/" + Database.DATABASE_NAME;
        SQLiteDatabase db = SQLiteDatabase.openDatabase(dbPath, null, 0);
        return db;
    }

    @Override
    protected void setUp() throws Exception {
        createApplication();
        MainApp app = getApplication();
    }

    // for pausing to wait for asynctask
    final CountDownLatch signal = new CountDownLatch(1);

    public void testManyReportEntries() {
        // make sure every thing has time to load
        mHandler.sendMessageDelayed(mHandler.obtainMessage(), 250);

        try {
            signal.await();
        } catch (Exception e) {
        }
    }

    private Handler mHandler = new Handler() {
        public void handleMessage(Message m) {
            SQLiteDatabase db = getStumblerDatabase();
            ContentValues values = new ContentValues();
            values.put(DatabaseContract.Reports.TIME, 666);
            values.put(DatabaseContract.Reports.LAT, 0.0);
            values.put(DatabaseContract.Reports.LON, 0.0);
            values.put(DatabaseContract.Reports.ALTITUDE, 0);
            values.put(DatabaseContract.Reports.ACCURACY, 0);
            values.put(DatabaseContract.Reports.RADIO, "gsm");
            values.put(DatabaseContract.Reports.CELL, "[{\"asu\":18,\"radio\":\"umts\",\"mnc\":720,\"psc\":426,\"cid\":2906773,\"mcc\":302,\"lac\":60013},{\"asu\":-81,\"radio\":\"umts\",\"mnc\":720,\"psc\":274,\"mcc\":302}]");
            values.put(DatabaseContract.Reports.WIFI, "[{\"signal\":-76,\"key\":\"001e58eb0dcf\",\"frequency\":2437},{\"signal\":-86,\"key\":\"00223f026f0a\",\"frequency\":2437},{\"signal\":-55,\"key\":\"60a44cc82952\",\"frequency\":2417},{\"signal\":-88,\"key\":\"001e582594bf\",\"frequency\":2452},{\"signal\":-89,\"key\":\"0018f8f3d1c2\",\"frequency\":2437},{\"signal\":-83,\"key\":\"0023f89aebdf\",\"frequency\":2437},{\"signal\":-61,\"key\":\"00265ac972ef\",\"frequency\":2457}]");
            values.put(DatabaseContract.Reports.CELL_COUNT, 2);
            values.put(DatabaseContract.Reports.WIFI_COUNT, 7);

            for (int i = 0; i < 100; i++) {
                db.insert("reports", null, values);
                values.put(DatabaseContract.Reports.TIME, 666 + i);
            }
            db.close();
            signal.countDown();
        }
    };
}
