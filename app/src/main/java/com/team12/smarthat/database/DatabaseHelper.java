package com.team12.smarthat.database;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.team12.smarthat.models.SensorData;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**safe database access*/
public class DatabaseHelper {
 private final SensorDataDao dao;
    private final Executor executor = Executors.newSingleThreadExecutor();
//init the database and dao
 public DatabaseHelper(Context context) {
        SensorDatabase db = SensorDatabase.getInstance(context);
        dao = db.sensorDataDao();
    }

    // ble note: will update sensordata and dao methods
    public void insertData(SensorData data) {
        executor.execute(() -> dao.insert(data));
    }
    // if ble: will update dao query here, liveData for changes
    public LiveData<List<SensorData>> getAllReadings() {
        return dao.getAllData();

    }}

