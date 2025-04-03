package com.team12.smarthat.bluetooth.core;

/**
 * Interface for components that need to be notified of sensor errors
 */
public interface SensorErrorListener {
    /**
     * Called when a sensor error occurs
     * @param sensorType The type of sensor experiencing the error
     * @param errorMessage A descriptive error message
     */
    void onSensorError(String sensorType, String errorMessage);
} 