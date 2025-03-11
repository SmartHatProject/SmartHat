package com.team12.smarthat.models;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import android.util.Log;

/**
 * Model class for sensor data
 * Enhanced with validation for Android 12 on Pixel 4a
 */
@Entity(tableName = "sensor_data")
public class SensorData {
    private static final String TAG = "SensorData";
    
    // Constants for data source
    public static final String SOURCE_REAL = "REAL";
    public static final String SOURCE_TEST = "TEST";
    
    // Constants for sensor types (matching BluetoothServiceIntegration)
    public static final String TYPE_DUST = "dust";
    public static final String TYPE_NOISE = "noise";
    
    // Validation constants
    private static final float MAX_DUST_VALUE = 1000.0f;
    private static final float MAX_NOISE_VALUE = 150.0f;
    private static final long MAX_FUTURE_TIMESTAMP = 60000; // 1 minute in the future
    
    @PrimaryKey(autoGenerate = true)
    private int id;

    private String sensorType;
    private float value;
    private long timestamp;
    private String metadata; // json string
    private String source = SOURCE_REAL; // Default to real data

    public SensorData(String sensorType, float value) {
        this.sensorType = normalizeSensorType(sensorType);
        this.value = validateValue(this.sensorType, value);
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
        this.sensorType = normalizeSensorType(sensorType);
        this.value = validateValue(this.sensorType, value);
        this.timestamp = System.currentTimeMillis();
        this.metadata = metadata;
    }

    /**
     * Constructor with timestamp
     * @param sensorType The type of sensor
     * @param value The sensor reading value
     * @param timestamp The timestamp of the reading
     */
    @Ignore
    public SensorData(String sensorType, float value, long timestamp) {
        this.sensorType = normalizeSensorType(sensorType);
        this.value = validateValue(this.sensorType, value);
        this.timestamp = validateTimestamp(timestamp);
        this.metadata = null;
    }

    /**
     * Normalize sensor type to lowercase for consistency
     * @param type The sensor type to normalize
     * @return Normalized sensor type
     */
    private String normalizeSensorType(String type) {
        if (type == null || type.isEmpty()) {
            Log.w(TAG, "Empty sensor type provided, defaulting to 'unknown'");
            return "unknown";
        }
        return type.toLowerCase();
    }
    
    /**
     * Validate and normalize sensor value based on type
     * @param type The sensor type
     * @param value The value to validate
     * @return Validated and normalized value
     */
    private float validateValue(String type, float value) {
        if (TYPE_DUST.equals(type)) {
            if (value < 0 || value > MAX_DUST_VALUE) {
                Log.w(TAG, "Invalid dust value: " + value + ", clamping to valid range");
                return Math.max(0, Math.min(value, MAX_DUST_VALUE));
            }
        } else if (TYPE_NOISE.equals(type)) {
            if (value < 0 || value > MAX_NOISE_VALUE) {
                Log.w(TAG, "Invalid noise value: " + value + ", clamping to valid range");
                return Math.max(0, Math.min(value, MAX_NOISE_VALUE));
            }
        }
        return value;
    }
    
    /**
     * Validate timestamp to prevent future timestamps
     * @param timestamp The timestamp to validate
     * @return Validated timestamp
     */
    private long validateTimestamp(long timestamp) {
        long currentTime = System.currentTimeMillis();
        
        // Check if timestamp is too far in the future
        if (timestamp > currentTime + MAX_FUTURE_TIMESTAMP) {
            Log.w(TAG, "Future timestamp detected: " + timestamp + ", using current time");
            return currentTime;
        }
        
        // Check if timestamp is too far in the past (1 day)
        if (timestamp < currentTime - 86400000) {
            Log.w(TAG, "Very old timestamp detected: " + timestamp + ", might be incorrect");
        }
        
        return timestamp;
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
        this.sensorType = normalizeSensorType(sensorType);
    }
    
    public float getValue() {
        return value;
    }
    
    public void setValue(float value) {
        this.value = validateValue(this.sensorType, value);
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = validateTimestamp(timestamp);
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
    
    /**
     * Check if this is dust sensor data
     * @return true if dust data
     */
    public boolean isDustData() {
        return TYPE_DUST.equals(sensorType);
    }
    
    /**
     * Check if this is noise sensor data
     * @return true if noise data
     */
    public boolean isNoiseData() {
        return TYPE_NOISE.equals(sensorType);
    }
    
    /**
     * Format the value with appropriate units based on sensor type
     * @return Formatted value with units
     */
    public String getFormattedValue() {
        if (TYPE_DUST.equals(sensorType)) {
            return String.format("%.1f µg/m³", value);
        } else if (TYPE_NOISE.equals(sensorType)) {
            return String.format("%.1f dB", value);
        } else {
            return String.format("%.1f", value);
        }
    }
}