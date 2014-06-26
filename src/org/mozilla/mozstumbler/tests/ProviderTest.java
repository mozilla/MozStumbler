package org.mozilla.mozstumbler.tests;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.test.ProviderTestCase2;

import org.json.JSONArray;
import org.mozilla.mozstumbler.service.datahandling.DatabaseContract;
import org.mozilla.mozstumbler.client.datahandling.Provider;

public class ProviderTest extends ProviderTestCase2<Provider> {

    public ProviderTest() {
        super(Provider.class, DatabaseContract.CONTENT_AUTHORITY);
    }

    public void testInsert() {
        final int time = 123456;
        final double lat = 99.12345;
        final double lng = 199;

        ContentValues values = new ContentValues();
        values.put(DatabaseContract.Reports.TIME, time);
        values.put(DatabaseContract.Reports.LAT, lat);
        values.put(DatabaseContract.Reports.LON, lng);

        values.put(DatabaseContract.Reports.RADIO, "");
        values.put(DatabaseContract.Reports.CELL, "");
        values.put(DatabaseContract.Reports.CELL_COUNT, 0);

        JSONArray wifis = new JSONArray();
        values.put(DatabaseContract.Reports.WIFI, wifis.toString());
        values.put(DatabaseContract.Reports.WIFI_COUNT, wifis.length());

        Uri uri = DatabaseContract.Reports.CONTENT_URI;
        Uri resultingUri = getMockContentResolver().insert(uri, values);

        assertNotNull(resultingUri);

        long id = ContentUris.parseId(resultingUri);
        assertTrue(id > 0);

        values.put(DatabaseContract.Reports.TIME, time-100);
        int rowsUpdated = getMockContentResolver().update(DatabaseContract.Reports.CONTENT_URI, values, "_id=" + id, null);
        assertTrue(rowsUpdated == 1);

        String[] projection = { DatabaseContract.Reports.TIME };

        Uri singleUri = ContentUris.withAppendedId(DatabaseContract.Reports.CONTENT_URI, id);
        Cursor cursor = getMockContentResolver().query(singleUri, projection, null, null, null);
        assertTrue(cursor.getCount() == 1);
        int idx = cursor.getColumnIndex(DatabaseContract.Reports.TIME);
        assertTrue(idx > -1);
        cursor.moveToFirst();
        long time2 = cursor.getLong(idx);
        assertTrue(time2 == time-100);

        cursor.close();

       // int deleted = getMockContentResolver().delete(singleUri, null, null);
       // assertTrue(deleted == 1);
    }

}
