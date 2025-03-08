package com.team12.smarthat.database;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.team12.smarthat.models.SensorData;
import com.team12.smarthat.utils.Constants;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**safe database access*/
public class DatabaseHelper {
 private final SensorDataDao dao;
    private final Executor executor = Executors.newSingleThreadExecutor();
    // max records# in db
    public static final int MAX_RECORDS = 10000;
    
//init the database and dao
 public DatabaseHelper(Context context) {
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
    // if ble: will update dao query here, liveData for changes
    public LiveData<List<SensorData>> getAllReadings() {
        return dao.getAllData();
    }
    
    // get all threshold breaches
    public LiveData<List<SensorData>> getThresholdBreaches(float dustThreshold, float noiseThreshold) {
        return dao.getThresholdBreaches(dustThreshold, noiseThreshold);
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
}

