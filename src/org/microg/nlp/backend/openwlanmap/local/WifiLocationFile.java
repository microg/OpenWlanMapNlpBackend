package org.microg.nlp.backend.openwlanmap.local;

import java.io.File;

import org.microg.nlp.backend.openwlanmap.Configuration;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.util.Log;
import android.util.LruCache;

public class WifiLocationFile {
    private static final String TABLE_APS = "APs";
    private static final String COL_BSSSID = "bssid";
    private static final String COL_LATITUDE = "latitude";
    private static final String COL_LONGITUDE = "longitude";
    private static File file;
    private SQLiteDatabase database;

    protected String TAG = WifiLocationFile.class.getName();


    public WifiLocationFile() {
    	openDatabase();
    }

    /**
     * DB negative query cache (not found in db).
     */
    private LruCache<String, Boolean> queryResultNegativeCache =
            new LruCache<String, Boolean>(1000);
    /**
     * DB positive query cache (found in the db).
     */
    private LruCache<String, Location> queryResultCache =
            new LruCache<String, Location>(1000);


    private void openDatabase() {
        if (database == null) {
            file = new File(Configuration.dbLocation);
            if (file.exists() && file.canRead()) {
                database = SQLiteDatabase.openDatabase(file.getAbsolutePath(),
                                                       null,
                                                       SQLiteDatabase.NO_LOCALIZED_COLLATORS);
            } else {
            	Log.e(TAG, "Could not open database at " + Configuration.dbLocation);
                database = null;
            }
        }
    }

    public void close() {
        if (database != null) {
            database.close();
            database = null;
        }
    }

    public boolean exists() {
        return file.exists() && file.canRead();
    }

    public String getPath() {
        return file.getAbsolutePath();
    }

    private void checkForNewDb() {
        File newDbFile = new File(Configuration.dbLocation + ".new");
        if (newDbFile.exists() && newDbFile.canRead()) {
            Log.d(TAG, "New database file detected.");
            this.close();
            queryResultCache = new LruCache<String, Location>(1000);
            queryResultNegativeCache = new LruCache<String, Boolean>(1000);
            file.renameTo(new File(Configuration.dbLocation + ".bak"));
            newDbFile.renameTo(new File(Configuration.dbLocation));
            openDatabase();
        }
    }

    public synchronized Location query(final String bssid) {

        checkForNewDb();

    	String normalizedBssid = bssid.replace(":", "");

    	Log.d(TAG, "Searching for BSSID '" + normalizedBssid + "'");

        Boolean negative = queryResultNegativeCache.get(normalizedBssid);
        if (negative != null && negative.booleanValue()) return null;

        Location cached = queryResultCache.get(normalizedBssid);
        if (cached != null) return cached;

        //openDatabase();
        if (database == null) {
            Log.d(TAG, "Unable to open wifi database file.");
            return null;
        }

        Location result = null;

        Cursor cursor =
                database.query(TABLE_APS,
                               new String[]{COL_LATITUDE,
                                            COL_LONGITUDE},
                               COL_BSSSID + "=?",
                               new String[]{normalizedBssid},
                               null,
                               null,
                               null);
        if (cursor != null) {
            Log.d(TAG,"Database contains " + cursor.getCount() + " entries");
            try {
                if (cursor.getCount() > 0) {
                        cursor.moveToNext();

                        result = new Location("owm");
                        result.setLatitude(cursor.getDouble(cursor.getColumnIndexOrThrow(COL_LATITUDE)));
                        result.setLongitude(cursor.getDouble(cursor.getColumnIndexOrThrow(COL_LONGITUDE)));
                        result.setAccuracy(Configuration.assumedAccuracy);

                        if (result.getLatitude() == 0 || result.getLongitude() == 0) {
                        	Log.d(TAG, "BSSID '" + bssid + "' returns 0 values for lat or long. Skipped.");
                        	queryResultNegativeCache.put(normalizedBssid, true);
                        	return null;
                        }

                        queryResultCache.put(normalizedBssid, result);
                        Log.d(TAG,"Wifi info found for: " + normalizedBssid);

                        return result;
                }
            } finally {
                cursor.close();
            }


        }
        Log.d(TAG,"No Wifi info found for: " + normalizedBssid);
        queryResultNegativeCache.put(normalizedBssid, true);
        return null;
    }
}
