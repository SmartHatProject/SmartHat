package com.team12.smarthat.models;

/**
 * Mock implementation of SensorData for testing
 */
public class SensorData {
    private String sensorType;
    private float value;
    private long timestamp;
    private boolean isAnomalous;

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

    public boolean isAnomalous() {
        return isAnomalous;
    }

    public void setAnomalous(boolean anomalous) {
        isAnomalous = anomalous;
    }
} 