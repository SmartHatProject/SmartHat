package com.team12.smarthat.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;

import androidx.room.Query;
import com.team12.smarthat.models.SensorData;

import java.util.List;
//live data to automatically notify observers , ui ,... when data change
//should keep this file sync with sensor data structure to avoid runtime issues

@Dao
// insert a new sensor reading into db

public interface SensorDataDao {
 @Insert


 void insert(SensorData data);

 @Query("SELECT * FROM sensor_data ORDER BY timestamp DESC")
     //retrive in timestamp order recent first
 //live data updates
LiveData<List<SensorData>> getAllData();
//clear db for test debug
  @Query("DELETE FROM sensor_data")
    void clearAll();

// get all threshold breaches
@Query("SELECT * FROM sensor_data WHERE (sensorType = 'dust' AND value > :dustThreshold) OR (sensorType = 'noise' AND value > :noiseThreshold) ORDER BY timestamp DESC")
LiveData<List<SensorData>> getThresholdBreaches(float dustThreshold, float noiseThreshold);

// get total count of records
@Query("SELECT COUNT(*) FROM sensor_data")
int getCount();

// delete oldest records exceeding a limit
@Query("DELETE FROM sensor_data WHERE id IN (SELECT id FROM sensor_data ORDER BY timestamp ASC LIMIT :count)")
void deleteOldest(int count);
}