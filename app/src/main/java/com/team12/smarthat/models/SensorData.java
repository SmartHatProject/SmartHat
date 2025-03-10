package com.team12.smarthat.models;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "sensor_data")
public class SensorData {
    // Constants for data source
    public static final String SOURCE_REAL = "REAL";
    public static final String SOURCE_TEST = "TEST";
    
    @PrimaryKey(autoGenerate = true)
    private int id;

    private String sensorType;
    private float value;
    private long timestamp;
    private String metadata; // json string
    private String source = SOURCE_REAL; // Default to real data

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
    
    /**
     * Get the source of this data (real or test)
     * @return The source identifier
     */
    public String getSource() {
        return source;
    }
    
    /**
     * Set the source of this data
     * @param source The source identifier (use SOURCE_REAL or SOURCE_TEST constants)
     */
    public void setSource(String source) {
        this.source = source;
    }
    
    /**
     * Check if this data is from a test source
     * @return true if test data, false if real data
     */
    public boolean isTestData() {
        return SOURCE_TEST.equals(source);
    }
}