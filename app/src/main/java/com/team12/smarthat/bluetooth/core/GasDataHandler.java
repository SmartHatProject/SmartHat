package com.team12.smarthat.bluetooth.core;

import android.util.Log;

import com.team12.smarthat.models.SensorData;
import com.team12.smarthat.utils.Constants;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Helper class for gas sensor data processing with enhanced robustness
 */
public class GasDataHandler {
    private static final String TAG = "GasDataHandler";
    
    // Buffer to accumulate partial JSON fragments
    private final StringBuilder jsonBuffer = new StringBuilder(256);
    
    // Track the last 5 readings for trend analysis
    private final Deque<Reading> gasReadingsHistory = new ArrayDeque<>(5);
    
    // Thread-safe reference to last valid gas value
    private final AtomicReference<Float> lastGasValue = new AtomicReference<>(0.0f);
    
    // Tracking consecutive errors
    private int consecutiveErrors = 0;
    private static final int ERROR_THRESHOLD = 5;
    
    // Max buffer size to prevent memory issues
    private static final int MAX_BUFFER_SIZE = 1024;
    
    // Track consecutive identical readings
    private int consecutiveIdenticalReadings = 0;
    private float lastReading = -1.0f;
    private static final int MAX_IDENTICAL_READINGS = 15;
    
    /**
     * Class to store a gas reading with timestamp for trend analysis
     */
    private static class Reading {
        final float value;
        final long timestamp;
        
        Reading(float value, long timestamp) {
            this.value = value;
            this.timestamp = timestamp;
        }
    }
    
    /**
     * Process raw data from BLE characteristic, handling potential fragmentation
     * @param data Raw byte data from BLE characteristic
     * @return Processed SensorData if valid, null if incomplete or invalid
     */
    public ProcessResult processGasData(byte[] data) {
        if (data == null || data.length == 0) {
            return new ProcessResult(false, null, "Empty data received");
        }
        
        // Append new data to buffer
        String dataStr = new String(data, StandardCharsets.UTF_8);
        jsonBuffer.append(dataStr);
        
        // Check if buffer is getting too large (corrupted/malformed data)
        if (jsonBuffer.length() > MAX_BUFFER_SIZE) {
            String errorMsg = "JSON buffer overflow, clearing: " + jsonBuffer.length();
            Log.w(TAG, errorMsg);
            jsonBuffer.setLength(0);
            consecutiveErrors++;
            return new ProcessResult(false, null, errorMsg);
        }
        
        // Try to parse as complete JSON
        String bufferedData = jsonBuffer.toString();
        try {
            // Attempt to parse to verify we have complete JSON
            JSONObject json = new JSONObject(bufferedData);
            
            // Successfully parsed, clear buffer
            jsonBuffer.setLength(0);
            
            // Reset consecutive errors counter
            consecutiveErrors = 0;
            
            // Process the valid JSON
            return parseGasJson(json);
            
        } catch (JSONException e) {
            // Incomplete or invalid JSON, keep in buffer for next fragment
            return new ProcessResult(false, null, "Incomplete JSON, waiting for more fragments");
        }
    }
    
    /**
     * Parse a complete JSON object for gas sensor data
     */
    private ProcessResult parseGasJson(JSONObject json) {
        try {
            // Check if this is a gas message
            String messageType = extractMessageType(json);
            if (messageType == null || !isGasMessage(messageType)) {
                return new ProcessResult(false, null, "Not a gas message: " + messageType);
            }
            
            // Extract the gas value
            double value = extractValue(json);
            
            // Extract timestamp
            long timestamp = extractTimestamp(json);
            if (timestamp == 0) {
                timestamp = System.currentTimeMillis();
            }
            
            // Validate gas value
            if (!isValidGasValue(value)) {
                String errorMsg = "Gas value out of range or invalid: " + value;
                Log.w(TAG, errorMsg);
                // Still return the last valid reading if we have one
                return new ProcessResult(false, 
                                      createGasData(lastGasValue.get(), timestamp, true), 
                                      errorMsg,
                                      ProcessResult.ANOMALY_OUT_OF_RANGE);
            }
            
            // Convert to float for storage
            float gasValue = (float) value;
            
            // Perform anomaly detection
            boolean isAnomalous = checkForAnomalies(gasValue, timestamp);
            
            // Determine anomaly type if anomalous
            int anomalyType = ProcessResult.ANOMALY_NONE;
            if (isAnomalous) {
                if (consecutiveIdenticalReadings >= MAX_IDENTICAL_READINGS) {
                    anomalyType = ProcessResult.ANOMALY_STUCK_READINGS;
                } else if (gasReadingsHistory.size() >= 2) {
                    Reading[] readings = gasReadingsHistory.toArray(new Reading[0]);
                    Reading current = readings[readings.length - 1];
                    Reading previous = readings[readings.length - 2];
                    long timeDiff = current.timestamp - previous.timestamp;
                    
                    if (timeDiff > 0) {
                        // Check for sudden changes (50%+ change in under 2 seconds)
                        float percentChange = Math.abs(current.value - previous.value) / 
                                            Math.max(0.1f, previous.value) * 100f;
                        if (percentChange > 50 && timeDiff < 2000) {
                            anomalyType = ProcessResult.ANOMALY_SUDDEN_CHANGE;
                        } else if (!isRealisticGasChange(current.value, previous.value, timeDiff)) {
                            anomalyType = ProcessResult.ANOMALY_UNREALISTIC_RATE;
                        }
                    }
                }
            }
            
            // Update last valid reading
            lastGasValue.set(gasValue);
            
            // Create sensor data 
            ProcessResult result = new ProcessResult(
                true,
                createGasData(gasValue, timestamp, isAnomalous),
                isAnomalous ? "Anomalous reading detected" : null,
                anomalyType
            );
            
            return result;
            
        } catch (Exception e) {
            consecutiveErrors++;
            
            String errorMsg = "Error parsing gas data (" + consecutiveErrors + 
                           " consecutive errors): " + e.getMessage();
            Log.e(TAG, errorMsg);
            
            // Use last valid reading as fallback
            return new ProcessResult(
                false, 
                createGasData(lastGasValue.get(), System.currentTimeMillis(), true),
                errorMsg
            );
        }
    }
    
    /**
     * Extract message type with case-insensitive handling
     */
    private String extractMessageType(JSONObject json) {
        // Check for messageType with various capitalizations
        if (json.has("messageType")) {
            return json.optString("messageType");
        } else if (json.has("MessageType")) {
            return json.optString("MessageType");
        } else if (json.has("MESSAGETYPE")) {
            return json.optString("MESSAGETYPE");
        }
        return null;
    }
    
    /**
     * Check if the message type indicates gas data
     */
    private boolean isGasMessage(String messageType) {
        // Case-insensitive check for gas messages
        return messageType != null && (
               messageType.equalsIgnoreCase("GAS_SENSOR_DATA") ||
               messageType.equalsIgnoreCase("GAS_DATA") ||
               messageType.startsWith("GAS_"));
    }
    
    /**
     * Extract value with support for different field names
     */
    private double extractValue(JSONObject json) {
        // Try different field names for the value
        if (json.has("data")) {
            return json.optDouble("data", 0.0);
        } else if (json.has("value")) {
            return json.optDouble("value", 0.0);
        } else if (json.has("gasValue")) {
            return json.optDouble("gasValue", 0.0);
        }
        return 0.0;
    }
    
    /**
     * Extract timestamp with support for different field names and formats
     */
    private long extractTimestamp(JSONObject json) {
        // Check both capitalization variants
        if (json.has("timeStamp")) {
            return json.optLong("timeStamp", 0);
        } else if (json.has("timestamp")) {
            return json.optLong("timestamp", 0);
        } else if (json.has("TimeStamp")) {
            return json.optLong("TimeStamp", 0);
        } else if (json.has("time")) {
            return json.optLong("time", 0);
        }
        return System.currentTimeMillis(); // Fallback to current time
    }
    
    /**
     * Validate gas value for range and special values
     */
    private boolean isValidGasValue(double value) {
        return !Double.isNaN(value) && 
               !Double.isInfinite(value) && 
               value >= Constants.GAS_MIN_VALUE && 
               value <= Constants.GAS_MAX_VALUE;
    }
    
    /**
     * Check for anomalies in gas readings
     */
    private boolean checkForAnomalies(float currentValue, long timestamp) {
        // Store last 5 readings for trend analysis
        if (gasReadingsHistory.size() >= 5) {
            gasReadingsHistory.removeFirst();
        }
        
        gasReadingsHistory.addLast(new Reading(currentValue, timestamp));
        
        // Only check for anomalies if we have at least 2 readings
        if (gasReadingsHistory.size() < 2) {
            return false;
        }
        
        // Get the two most recent readings
        Reading[] readings = gasReadingsHistory.toArray(new Reading[0]);
        Reading current = readings[readings.length - 1];
        Reading previous = readings[readings.length - 2];
        
        // Check for anomalies based on various criteria
        boolean isAnomalous = false;
        
        // Check for sudden jumps (more than 50% change in less than 2 seconds)
        float percentChange = Math.abs(current.value - previous.value) / Math.max(0.1f, previous.value) * 100f;
        long timeDiff = current.timestamp - previous.timestamp;
        
        if (percentChange > 50 && timeDiff < 2000 && timeDiff > 0) {
            Log.w(TAG, "Detected possible anomalous gas reading: " + 
                  percentChange + "% change in " + timeDiff + "ms");
            isAnomalous = true;
        }
        
        // Use our new method to check if the change rate is realistic
        if (timeDiff > 0 && !isRealisticGasChange(current.value, previous.value, timeDiff)) {
            isAnomalous = true;
        }
        
        // Check for identical consecutive values which could indicate a stuck sensor
        // Only consider this anomalous after 5+ identical readings, to avoid false positives
        if (current.value == previous.value) {
            // Track identical readings
            if (current.value == lastReading) {
                consecutiveIdenticalReadings++;
                // Consider anomalous if we have too many identical readings in a row
                if (consecutiveIdenticalReadings >= MAX_IDENTICAL_READINGS) {
                    Log.w(TAG, "Detected possible stuck gas sensor: " + 
                          consecutiveIdenticalReadings + " identical readings of " + current.value);
                    isAnomalous = true;
                }
            } else {
                // Reset counter for new value
                consecutiveIdenticalReadings = 1;
                lastReading = current.value;
            }
        } else {
            // Different reading, reset counter
            consecutiveIdenticalReadings = 1;
            lastReading = current.value;
        }
        
        return isAnomalous;
    }
    
    /**
     * Create a SensorData object for gas data
     */
    public SensorData createGasData(float value, long timestamp, boolean isAnomalous) {
        // Check for identical consecutive readings
        if (value == lastReading && value > 0) {
            consecutiveIdenticalReadings++;
            if (consecutiveIdenticalReadings > MAX_IDENTICAL_READINGS) {
                // If we have an excessive number of identical readings, mark as anomalous
                isAnomalous = true;
            }
        } else {
            // Reset counter when we get a different value
            consecutiveIdenticalReadings = 0;
            lastReading = value;
        }
        
        // Create the data object
        SensorData data = new SensorData();
        data.setSensorType("gas");
        data.setValue(value);
        data.setTimestamp(timestamp);
        data.setAnomalous(isAnomalous);
        return data;
    }
    
    /**
     * Get the last valid gas value
     */
    public float getLastValidGasValue() {
        return lastGasValue.get();
    }
    
    /**
     * Check if there is a potential gas sensor error
     */
    public boolean hasPotentialSensorError() {
        return consecutiveErrors >= ERROR_THRESHOLD;
    }
    
    /**
     * Reset error counter to avoid repeated error notifications
     */
    public void resetErrorCounter() {
        consecutiveErrors = 0;
        Log.d(TAG, "Reset gas sensor error counter");
    }
    
    /**
     * Reset all counters for a clean start
     */
    public void resetAllCounters() {
        consecutiveErrors = 0;
        consecutiveIdenticalReadings = 0;
        lastReading = -1.0f;
        jsonBuffer.setLength(0);
        gasReadingsHistory.clear();
        Log.d(TAG, "Reset all gas sensor counters");
    }
    
    /**
     * Get the count of consecutive identical readings
     * @return Number of consecutive identical readings
     */
    public int getConsecutiveIdenticalReadings() {
        return consecutiveIdenticalReadings;
    }
    
    /**
     * Reset identical readings counter
     */
    public void resetIdenticalReadingsCounter() {
        consecutiveIdenticalReadings = 0;
    }
    
    /**
     * 
     * @param newValue The new gas value
     * @param oldValue The previous gas value
     * @param timeDiffMs Time difference in milliseconds
     * @return true if within realistic limits, false if change appears unrealistic
     */
    protected boolean isRealisticGasChange(float newValue, float oldValue, long timeDiffMs) {
        // Skip check if time difference is too large (could be a legitimate change)
        if (timeDiffMs > 5000) {
            return true;
        }
        
        // Skip check if values are very small (minor fluctuations)
        if (newValue < 10 && oldValue < 10) {
            return true;
        }
        
        // Calculate rate of change per second
        float absoluteChange = Math.abs(newValue - oldValue);
        float percentChange = (absoluteChange / Math.max(0.1f, oldValue)) * 100f;
        float changePerSecond = percentChange / (timeDiffMs / 1000f);
    
        boolean isRealistic = changePerSecond <= 30f;
        
        if (!isRealistic) {
            Log.w(TAG, String.format(
                "Potentially unrealistic gas change: %.1f to %.1f (%.1f%% change) in %dms (%.1f%%/sec)",
                oldValue, newValue, percentChange, timeDiffMs, changePerSecond));
        }
        
        return isRealistic;
    }
    
    /**
     * Result object for gas data processing
     */
    public class ProcessResult {
        public final boolean success;
        public final SensorData data;
        public final String message;
        public final int anomalyType;
        
        // Anomaly type constants
        public static final int ANOMALY_NONE = 0;
        public static final int ANOMALY_SUDDEN_CHANGE = 1;
        public static final int ANOMALY_UNREALISTIC_RATE = 2;
        public static final int ANOMALY_STUCK_READINGS = 3;
        public static final int ANOMALY_OUT_OF_RANGE = 4;
        
        /**
         * Constructor for ProcessResult
         * @param success true if processing was successful
         * @param data SensorData object (may be null if processing failed)
         * @param message Informational or error message
         */
        public ProcessResult(boolean success, SensorData data, String message) {
            this(success, data, message, ANOMALY_NONE);
        }
        
        /**
         * Constructor with anomaly type
         * @param success true if processing was successful
         * @param data SensorData object (may be null if processing failed)
         * @param message Informational or error message
         * @param anomalyType The type of anomaly detected, if any
         */
        public ProcessResult(boolean success, SensorData data, String message, int anomalyType) {
            this.success = success;
            this.data = data;
            this.message = message;
            this.anomalyType = anomalyType;
        }
    }
} 