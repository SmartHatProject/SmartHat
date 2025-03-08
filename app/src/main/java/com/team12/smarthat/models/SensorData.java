package com.team12.smarthat.models;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "sensor_data")
public class SensorData {
    @PrimaryKey(autoGenerate = true)
    private int id;

    private String sensorType;
    private float value;
    private long timestamp;
    private String metadata; // json string

    public SensorData(String sensorType, float value) {
        this.sensorType = sensorType;
        this.value = value;
        this.timestamp = System.currentTimeMillis();
        this.metadata = null;
    }
    
    /**
     * Constructor with additional metadata support
     * @param sensorType The type of sensor
     * @param value The sensor reading value
     * @param metadata Additional JSON data as string
     */
    @Ignore
    public SensorData(String sensorType, float value, String metadata) {
        this.sensorType = sensorType;
        this.value = value;
        this.timestamp = System.currentTimeMillis();
        this.metadata = metadata;
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
    
    public void setSensorType(String sensorType) {
        this.sensorType = sensorType;
    }
    
    public float getValue() {
        return value;
    }
    
    public void setValue(float value) {
        this.value = value;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getMetadata() {
        return metadata;
    }
    
    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }
}