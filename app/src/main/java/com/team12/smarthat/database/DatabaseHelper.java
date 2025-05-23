package com.team12.smarthat.database;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteQuery;

import com.team12.smarthat.models.SensorData;
import com.team12.smarthat.utils.Constants;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *db access using singleton pattern
 */
public class DatabaseHelper {
    private static volatile DatabaseHelper instance;
    private static Context appContext;
    
    private final SensorDataDao dao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    // max records# in db
    public static final int MAX_RECORDS = 10000;
    
    /**
     * initialize the DatabaseHelper with application context
     * call this method once in AppController.onCreate()
     * @param context application context
     */
    public static synchronized void initialize(Context context) {
        if (appContext == null) {
            appContext = context.getApplicationContext();
        }
    }
    
    /**
     * get the singleton instance of DatabaseHelper
     * @return the DatabaseHelper instance
     * @throws IllegalStateException if initialize() not called before
     */
    public static DatabaseHelper getInstance() {
        if (instance == null) {
            synchronized (DatabaseHelper.class) {
                if (instance == null) {
                    if (appContext == null) {
                        // track where the error occurs for easier debugging
                        String callerInfo = "";
                        try {
                            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
                            if (stackTrace.length >= 3) {
                                callerInfo = " (called from " + stackTrace[3].getClassName() + 
                                           "." + stackTrace[3].getMethodName() + ":" + 
                                           stackTrace[3].getLineNumber() + ")";
                            }
                        } catch (Exception e) {
                            // ignore stack trace processing errors
                        }
                        
                        throw new IllegalStateException("DatabaseHelper not initialized! Call initialize() first." + callerInfo);
                    }
                    
                    // create singleton instance
                    instance = new DatabaseHelper(appContext);
                    Log.d(Constants.TAG_DATABASE, "DatabaseHelper singleton instance created");
                }
            }
        }
        
        return instance;
    }
    
    /**
     * private constructor to enforce singleton pattern
     * never call directly
     * @param context application context for database creation
     */
    private DatabaseHelper(Context context) {
        SensorDatabase db = SensorDatabase.getInstance(context);
        dao = db.sensorDataDao();
        // schedule initial cleanup
        scheduleCleanup();
    }

    // ble note: will update sensordata and dao methods
    public void insertData(SensorData data) {
        executor.execute(() -> {
            dao.insert(data);
            // check if cleanup is needed after insert
            checkAndCleanupDatabase();
        });
    }
    
    /**
     * insert sensor data into the database
     * @param data the sensor data to insert
     */
    public void insertSensorData(SensorData data) {
        insertData(data);
    }
    
    /**
     * save the current app state
     * @param state the state to save
     */
    public void saveAppState(String state) {
        Log.d(Constants.TAG_DATABASE, "Saving app state: " + state);
        // implementation can be expanded as needed
    }
    
    // if ble: will update dao query here, liveData for changes
    public LiveData<List<SensorData>> getAllReadings() {
        return dao.getAllData();
    }
    
    // get all threshold breaches
    public LiveData<List<SensorData>> getThresholdBreaches(float dustThreshold, float noiseThreshold, float gasThreshold) {
        return dao.getThresholdBreaches(dustThreshold, noiseThreshold, gasThreshold);
    }
    
    /**
     * periodic database cleanup
     */
    private void scheduleCleanup() {
        executor.execute(this::checkAndCleanupDatabase);
    }
    
    /**
     * dbcleanup check
     */
    private void checkAndCleanupDatabase() {
        try {
            int count = dao.getCount();
            if (count > MAX_RECORDS) {
                Log.d(Constants.TAG_DATABASE, "Database cleanup: removing " + (count - MAX_RECORDS) + " old records");
                dao.deleteOldest(count - MAX_RECORDS);
            }
        } catch (Exception e) {
            Log.e(Constants.TAG_DATABASE, "Error during database cleanup: " + e.getMessage());
        }
    }
    
    /**
     * clear up db
     */
    public void clearAllData() {
        executor.execute(dao::clearAll);
    }
    
    /**
     * db maintenance operations
     *
     */
    public void performMaintenance() {
        executor.execute(() -> {
            checkAndCleanupDatabase();
            

            try {
                Log.d(Constants.TAG_DATABASE, "Performing additional database maintenance");
                androidx.sqlite.db.SimpleSQLiteQuery vacuumQuery = new androidx.sqlite.db.SimpleSQLiteQuery("VACUUM");
                dao.vacuum(vacuumQuery);
                Log.d(Constants.TAG_DATABASE, "Database VACUUM completed successfully");
            } catch (Exception e) {
                Log.e(Constants.TAG_DATABASE, "Error during database maintenance: " + e.getMessage());
            }
        });
    }
    
    /**
     * Get the user's custom dust threshold value
     * @param context Context for accessing shared preferences
     * @return The custom threshold value or the default if not set
     */
    public float getCustomDustThreshold(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getFloat(Constants.PREF_DUST_THRESHOLD, Constants.DUST_THRESHOLD);
    }
    
    /**
     * Get the user's custom noise threshold value
     * @param context Context for accessing shared preferences
     * @return The custom threshold value or the default if not set
     */
    public float getCustomNoiseThreshold(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getFloat(Constants.PREF_NOISE_THRESHOLD, Constants.NOISE_THRESHOLD);
    }
    
    /**
     * Get the user's custom gas threshold value
     * @param context Context for accessing shared preferences
     * @return The custom threshold value or the default if not set
     */
    public float getCustomGasThreshold(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getFloat(Constants.PREF_GAS_THRESHOLD, Constants.GAS_THRESHOLD);
    }
    
    /**
     * Get threshold breaches with custom thresholds
     * @param context Context for accessing shared preferences
     * @return LiveData list of threshold breaches based on custom thresholds
     */
    public LiveData<List<SensorData>> getThresholdBreachesWithCustomThresholds(Context context) {
        float dustThreshold = getCustomDustThreshold(context);
        float noiseThreshold = getCustomNoiseThreshold(context);
        float gasThreshold = getCustomGasThreshold(context);
        
        // Use the version that supports filtering
        if(com.team12.smarthat.utils.DataFilterHelper.getInstance().getCurrentFilter() != null) {
            return getThresholdBreaches(dustThreshold, noiseThreshold, gasThreshold, context);
        }
        
        // If no filtering needed, use original version with gas threshold
        return getThresholdBreaches(dustThreshold, noiseThreshold, gasThreshold);
    }
    
    /**
     * Get threshold breaches with filter handling support
     * @param dustThreshold Custom dust threshold
     * @param noiseThreshold Custom noise threshold
     * @param gasThreshold Custom gas threshold
     * @param context Context for accessing shared preferences
     * @return LiveData list of threshold breaches with filter support
     */
    public LiveData<List<SensorData>> getThresholdBreaches(float dustThreshold, float noiseThreshold, float gasThreshold, Context context) {
        // Check if date filtering is needed
        com.team12.smarthat.models.DataFilter filter = com.team12.smarthat.utils.DataFilterHelper.getInstance().getCurrentFilter();
        if (filter != null) {
            // Apply date range filtering
            return dao.getThresholdBreaches(
                dustThreshold, noiseThreshold, gasThreshold, 
                filter.getStartTimestamp(), filter.getEndTimestamp());
        }
        
        // No date filtering
        return dao.getThresholdBreaches(dustThreshold, noiseThreshold, gasThreshold);
    }
    
    /**
     * Delete a specific threshold breach by its ID
     * @param id ID of the record to delete
     */
    public void deleteThresholdBreach(int id) {
        executor.execute(() -> {
            dao.deleteById(id);
            Log.d(Constants.TAG_DATABASE, "Deleted threshold breach with ID: " + id);
        });
    }
    
    /**
     * Delete multiple threshold breaches by their IDs
     * @param ids List of IDs to delete
     */
    public void deleteThresholdBreaches(List<Integer> ids) {
        executor.execute(() -> {
            dao.deleteByIds(ids);
            Log.d(Constants.TAG_DATABASE, "Deleted " + ids.size() + " threshold breaches");
        });
    }
    
    /**
     * Delete all threshold breaches based on custom thresholds
     * @param context Context for accessing shared preferences
     */
    public void deleteAllThresholdBreaches(Context context) {
        float dustThreshold = getCustomDustThreshold(context);
        float noiseThreshold = getCustomNoiseThreshold(context);
        float gasThreshold = getCustomGasThreshold(context);
        
        executor.execute(() -> {
            int count = dao.deleteAllThresholdBreaches(dustThreshold, noiseThreshold, gasThreshold);
            Log.d(Constants.TAG_DATABASE, "Deleted all threshold breaches: " + count + " records");
        });
    }
}

