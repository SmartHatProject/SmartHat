package com.team12.smarthat.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.team12.smarthat.models.SensorData;

//room database config class
//database version,entities,access point definition
// ble case: will update our sensordata model
@Database(entities = {SensorData.class}, version = 2, exportSchema = false)
public abstract class SensorDatabase extends RoomDatabase {
public abstract SensorDataDao sensorDataDao(); // dao interface

private static volatile SensorDatabase INSTANCE;
//to avoid multiple db instances
 public static SensorDatabase getInstance(Context context) {
 if (INSTANCE == null) {
     //one instance at atime
    synchronized (SensorDatabase.class) {
if (INSTANCE == null) {
 INSTANCE = Room.databaseBuilder( //build room db instance
   context.getApplicationContext(),
   SensorDatabase.class,
"sensor_readings.db").fallbackToDestructiveMigration()
                            .build();
   }}
 }
 return INSTANCE;}}