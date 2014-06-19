package org.mozilla.mozstumbler.service.datahandling;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.test.AndroidTestCase;
import android.util.Log;

/** Used by Provider */
public class Database extends SQLiteOpenHelper {
    private static final String LOGTAG = Database.class.getName();
    public static final int DATABASE_VERSION = 2;
    public static final String DATABASE_NAME = "stumbler.db";
    public static final String TABLE_REPORTS = "reports";
    static final String TABLE_STATS = "stats";

    static String sFullPathToDbForTest;
    public static String getFullPathToDb(AndroidTestCase restricted) { return sFullPathToDbForTest; }

    public Database(Context context) {
        super(context, 
                // for dev use to get db in public location 
                // (debug)? Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) + "/" + DATABASE_NAME :
                DATABASE_NAME,
                null, DATABASE_VERSION);

        sFullPathToDbForTest = context.getDatabasePath(DATABASE_NAME).toString();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createTableReports(db);
        createTableStats(db);
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

    public static void createTableReports(SQLiteDatabase db) {
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

    public static void createTableStats(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_STATS + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY,"
                + DatabaseContract.StatsColumns.KEY + " VARCHAR(80) UNIQUE NOT NULL,"
                + DatabaseContract.StatsColumns.VALUE + " TEXT NOT NULL)");

        db.insertWithOnConflict(TABLE_STATS, null, DatabaseContract.Stats.values(DatabaseContract.Stats.KEY_LAST_UPLOAD_TIME, "0"), SQLiteDatabase.CONFLICT_REPLACE);
        db.insertWithOnConflict(TABLE_STATS, null, DatabaseContract.Stats.values(DatabaseContract.Stats.KEY_OBSERVATIONS_SENT, "0"), SQLiteDatabase.CONFLICT_REPLACE);
        db.insertWithOnConflict(TABLE_STATS, null, DatabaseContract.Stats.values(DatabaseContract.Stats.KEY_WIFIS_SENT, "0"), SQLiteDatabase.CONFLICT_REPLACE);
        db.insertWithOnConflict(TABLE_STATS, null, DatabaseContract.Stats.values(DatabaseContract.Stats.KEY_CELLS_SENT, "0"), SQLiteDatabase.CONFLICT_REPLACE);
    }

}
