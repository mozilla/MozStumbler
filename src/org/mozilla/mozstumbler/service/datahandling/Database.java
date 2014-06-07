package org.mozilla.mozstumbler.service.datahandling;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;

/** Used by Provider */
public class Database extends SQLiteOpenHelper {
    private static final String LOGTAG = Database.class.getName();
    private static final int DATABASE_VERSION = 2;
    private static final String DATABASE_NAME = "stumbler.db";
    static final String TABLE_REPORTS = "reports";
    static final String TABLE_STATS = "stats";

    public Database(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createTableReports(db);
        db.execSQL("CREATE TABLE " + TABLE_STATS + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY,"
                + DatabaseContract.StatsColumns.KEY + " VARCHAR(80) UNIQUE NOT NULL,"
                + DatabaseContract.StatsColumns.VALUE + " TEXT NOT NULL)");

        db.insertWithOnConflict(TABLE_STATS, null, DatabaseContract.Stats.values(DatabaseContract.Stats.KEY_LAST_UPLOAD_TIME, "0"), SQLiteDatabase.CONFLICT_REPLACE);
        db.insertWithOnConflict(TABLE_STATS, null, DatabaseContract.Stats.values(DatabaseContract.Stats.KEY_OBSERVATIONS_SENT, "0"), SQLiteDatabase.CONFLICT_REPLACE);
        db.insertWithOnConflict(TABLE_STATS, null, DatabaseContract.Stats.values(DatabaseContract.Stats.KEY_WIFIS_SENT, "0"), SQLiteDatabase.CONFLICT_REPLACE);
        db.insertWithOnConflict(TABLE_STATS, null, DatabaseContract.Stats.values(DatabaseContract.Stats.KEY_CELLS_SENT, "0"), SQLiteDatabase.CONFLICT_REPLACE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(LOGTAG, "onUpgrade() from " + oldVersion + " to " + newVersion);

        int version = oldVersion;
        switch (version) {
            case 1:
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_REPORTS);
                createTableReports(db);
                version = 2;
        }

        if (version != DATABASE_VERSION) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_REPORTS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_STATS);
            onCreate(db);
        }
    }

    private void createTableReports(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_REPORTS + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + DatabaseContract.ReportsColumns.TIME + " INTEGER NOT NULL,"
                + DatabaseContract.ReportsColumns.LAT + " REAL NOT NULL,"
                + DatabaseContract.ReportsColumns.LON + " REAL NOT NULL,"
                + DatabaseContract.ReportsColumns.ALTITUDE + " INTEGER,"
                + DatabaseContract.ReportsColumns.ACCURACY + " INTEGER,"
                + DatabaseContract.ReportsColumns.RADIO + " VARCHAR(8) NOT NULL,"
                + DatabaseContract.ReportsColumns.CELL + " TEXT NOT NULL,"
                + DatabaseContract.ReportsColumns.WIFI + " TEXT NOT NULL,"
                + DatabaseContract.ReportsColumns.CELL_COUNT + " INTEGER NOT NULL,"
                + DatabaseContract.ReportsColumns.WIFI_COUNT + " INTEGER NOT NULL,"
                + DatabaseContract.ReportsColumns.RETRY_NUMBER + " INTEGER NOT NULL DEFAULT 0)");
    }
}
