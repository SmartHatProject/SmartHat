package com.team12.smarthat.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "sensor_data")
public class SensorData {
    @PrimaryKey(autoGenerate = true)
 private int id;

private String sensorType;
private float value;
private long timestamp;

 public SensorData(String sensorType, float value) {
   this.sensorType = sensorType;
 this.value = value;
  this.timestamp = System.currentTimeMillis();
    }

    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }
    public String getSensorType() {
        return sensorType;
    }
    public float getValue() {
        return value;
    }
    public long getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}