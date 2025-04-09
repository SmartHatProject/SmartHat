package com.team12.smarthat.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.RawQuery;
import androidx.sqlite.db.SupportSQLiteQuery;

import com.team12.smarthat.models.SensorData;

import java.util.List;
//live data to automatically notify observers when change


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
    @Query("SELECT * FROM sensor_data WHERE (sensorType = 'dust' AND value > :dustThreshold) OR (sensorType = 'noise' AND value > :noiseThreshold) OR (sensorType = 'gas' AND value > :gasThreshold) ORDER BY timestamp DESC")
    LiveData<List<SensorData>> getThresholdBreaches(float dustThreshold, float noiseThreshold, float gasThreshold);

    // get threshold breaches with date range filtering
    @Query("SELECT * FROM sensor_data WHERE ((sensorType = 'dust' AND value > :dustThreshold) OR (sensorType = 'noise' AND value > :noiseThreshold) OR (sensorType = 'gas' AND value > :gasThreshold)) AND timestamp >= :startTimestamp AND timestamp <= :endTimestamp ORDER BY timestamp DESC")
    LiveData<List<SensorData>> getThresholdBreaches(float dustThreshold, float noiseThreshold, float gasThreshold, long startTimestamp, long endTimestamp);

    // get total count of records
    @Query("SELECT COUNT(*) FROM sensor_data")
    int getCount();

    // delete oldest records exceeding a limit
    @Query("DELETE FROM sensor_data WHERE id IN (SELECT id FROM sensor_data ORDER BY timestamp ASC LIMIT :count)")
    void deleteOldest(int count);
    
    /**
     * run vacuum to optimize the database
     *
     */
    @RawQuery
    int vacuum(SupportSQLiteQuery query);
    
    // delete a specific record by its ID
    @Query("DELETE FROM sensor_data WHERE id = :id")
    void deleteById(int id);
    
    // delete multiple records by their IDs
    @Query("DELETE FROM sensor_data WHERE id IN (:ids)")
    void deleteByIds(List<Integer> ids);
    
    // delete all threshold breaches
    @Query("DELETE FROM sensor_data WHERE (sensorType = 'dust' AND value > :dustThreshold) OR (sensorType = 'noise' AND value > :noiseThreshold) OR (sensorType = 'gas' AND value > :gasThreshold)")
    int deleteAllThresholdBreaches(float dustThreshold, float noiseThreshold, float gasThreshold);
}