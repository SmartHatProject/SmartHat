package com.team12.smarthat.utils;

import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.team12.smarthat.bluetooth.core.MockBleConnectionManager;
import com.team12.smarthat.bluetooth.devices.esp32.ESP32BluetoothSpec;
import com.team12.smarthat.models.SensorData;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Utility class to generate test data for the SmartHat app
 * 
 * Optimized for Android 12 on Pixel 4a
 */
public class TestDataGenerator {
    private static final String TAG = "TestDataGenerator";
    
    // Generation frequency
    private static final long NORMAL_INTERVAL_MS = 5000; // 5 seconds
    private static final long RAPID_INTERVAL_MS = 1000;  // 1 second (for threshold testing)
    
    // Value ranges
    private static final float MIN_DUST_VALUE = 10.0f;
    private static final float MAX_DUST_VALUE = 300.0f;
    private static final float HIGH_DUST_VALUE = Constants.DUST_THRESHOLD + 50.0f;
    
    private static final float MIN_NOISE_VALUE = 40.0f;
    private static final float MAX_NOISE_VALUE = 120.0f;
    private static final float HIGH_NOISE_VALUE = Constants.NOISE_THRESHOLD + 20.0f;
    
    // Test mode states
    public enum TestMode {
        OFF,            // Test mode disabled
        NORMAL,         // Normal readings within safe range
        HIGH_DUST,      // High dust readings to test alerts
        HIGH_NOISE,     // High noise readings to test alerts
        RANDOM          // Random readings (may trigger alerts)
    }
    
    private TestMode currentMode = TestMode.OFF;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    
    // Mock connection manager for test mode simulation
    private MockBleConnectionManager mockBleManager;
    
    // Simulated characteristic objects for test data
    private BluetoothGattCharacteristic dustCharacteristic;
    private BluetoothGattCharacteristic noiseCharacteristic;
    
    // Legacy data callback interface (for backward compatibility)
    public interface TestDataListener {
        void onTestDataGenerated(SensorData data);
    }
    
    private TestDataListener legacyListener;
    
    /**
     * Set the mock BLE manager for connection simulation
     */
    public void setMockBleManager(MockBleConnectionManager manager) {
        this.mockBleManager = manager;
        
        // Create mock characteristics when the manager is set
        createMockCharacteristics();
    }
    
    /**
     * Set the legacy listener that will receive test data directly
     * Note: This is for backward compatibility. Prefer using the mock connection path.
     */
    public void setListener(TestDataListener listener) {
        this.legacyListener = listener;
    }
    
    /**
     * Start generating test data with the specified mode
     */
    public void startTestMode(TestMode mode) {
        if (mode == TestMode.OFF) {
            stopTestMode();
            return;
        }
        
        // If already running, stop first
        if (isRunning.getAndSet(true)) {
            handler.removeCallbacks(dataGenerationRunnable);
        }
        
        this.currentMode = mode;
        Log.d(TAG, "Starting test mode: " + mode.name());
        
        // Start data generation immediately and then at intervals
        handler.post(dataGenerationRunnable);
    }
    
    /**
     * Stop generating test data
     */
    public void stopTestMode() {
        if (isRunning.getAndSet(false)) {
            handler.removeCallbacks(dataGenerationRunnable);
            Log.d(TAG, "Test mode stopped");
        }
        currentMode = TestMode.OFF;
    }
    
    /**
     * Get the current test mode
     */
    public TestMode getCurrentMode() {
        return currentMode;
    }
    
    /**
     * Check if test mode is active
     */
    public boolean isTestModeActive() {
        return currentMode != TestMode.OFF && isRunning.get();
    }
    
    /**
     * Create mock characteristic objects for dust and noise
     */
    private void createMockCharacteristics() {
        try {
            // Create mock characteristic for dust data
            dustCharacteristic = new BluetoothGattCharacteristic(
                    ESP32BluetoothSpec.DUST_CHARACTERISTIC_UUID,
                    BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                    BluetoothGattCharacteristic.PERMISSION_READ
            );
            
            // Create mock characteristic for noise data
            noiseCharacteristic = new BluetoothGattCharacteristic(
                    ESP32BluetoothSpec.SOUND_CHARACTERISTIC_UUID,
                    BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                    BluetoothGattCharacteristic.PERMISSION_READ
            );
            
            Log.d(TAG, "Mock characteristics created");
        } catch (Exception e) {
            Log.e(TAG, "Error creating mock characteristics: " + e.getMessage(), e);
        }
    }
    
    /**
     * Runnable that generates and sends test data
     */
    private final Runnable dataGenerationRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRunning.get()) {
                return;
            }
            
            try {
                // Generate both dust and noise data (alternating)
                boolean generateDust = System.currentTimeMillis() % 2 == 0;
                
                if (generateDust) {
                    // Generate dust data
                    SensorData dustData = generateDustData();
                    
                    // Send data through mock BLE manager if available
                    if (mockBleManager != null && dustCharacteristic != null) {
                        // Set the value on the characteristic
                        String jsonStr = dustData.getMetadata();
                        dustCharacteristic.setValue(jsonStr.getBytes());
                        
                        // Simulate characteristic change event
                        mockBleManager.simulateCharacteristicChange(dustCharacteristic);
                        Log.d(TAG, "Simulated dust characteristic change");
                    }
                    
                    // Also send through legacy direct callback if set
                    if (legacyListener != null) {
                        legacyListener.onTestDataGenerated(dustData);
                    }
                } else {
                    // Generate noise data
                    SensorData noiseData = generateNoiseData();
                    
                    // Send data through mock BLE manager if available
                    if (mockBleManager != null && noiseCharacteristic != null) {
                        // Set the value on the characteristic
                        String jsonStr = noiseData.getMetadata();
                        noiseCharacteristic.setValue(jsonStr.getBytes());
                        
                        // Simulate characteristic change event
                        mockBleManager.simulateCharacteristicChange(noiseCharacteristic);
                        Log.d(TAG, "Simulated noise characteristic change");
                    }
                    
                    // Also send through legacy direct callback if set
                    if (legacyListener != null) {
                        legacyListener.onTestDataGenerated(noiseData);
                    }
                }
                
                // Schedule next generation based on mode
                long interval = currentMode == TestMode.RANDOM ? RAPID_INTERVAL_MS : NORMAL_INTERVAL_MS;
                handler.postDelayed(this, interval);
                
            } catch (Exception e) {
                Log.e(TAG, "Error generating test data: " + e.getMessage(), e);
                
                // Try again after a delay to recover from errors
                handler.postDelayed(this, NORMAL_INTERVAL_MS);
            }
        }
    };
    
    /**
     * Generate test dust sensor data based on current mode
     */
    private SensorData generateDustData() {
        float value;
        
        switch (currentMode) {
            case HIGH_DUST:
                // Generate high dust value to trigger alerts
                value = HIGH_DUST_VALUE + (random.nextFloat() * 50.0f);
                break;
                
            case RANDOM:
                // Generate random value across entire range
                value = MIN_DUST_VALUE + (random.nextFloat() * (MAX_DUST_VALUE - MIN_DUST_VALUE));
                break;
                
            case NORMAL:
            default:
                // Generate normal safe value
                value = MIN_DUST_VALUE + (random.nextFloat() * (Constants.DUST_THRESHOLD - MIN_DUST_VALUE - 20.0f));
                break;
        }
        
        // Create JSON data matching the expected format from real sensors
        JSONObject json = new JSONObject();
        try {
            json.put("type", SensorData.TYPE_DUST);
            json.put("value", value);
            json.put("timestamp", System.currentTimeMillis());
        } catch (JSONException e) {
            Log.e(TAG, "Error creating JSON for test data", e);
        }
        
        // Create a sensor data object marked as test data
        SensorData data = new SensorData(SensorData.TYPE_DUST, value, System.currentTimeMillis());
        data.setSource(SensorData.SOURCE_TEST);
        data.setMetadata(json.toString());
        
        return data;
    }
    
    /**
     * Generate test noise sensor data based on current mode
     */
    private SensorData generateNoiseData() {
        float value;
        
        switch (currentMode) {
            case HIGH_NOISE:
                // Generate high noise value to trigger alerts
                value = HIGH_NOISE_VALUE + (random.nextFloat() * 20.0f);
                break;
                
            case RANDOM:
                // Generate random value across entire range
                value = MIN_NOISE_VALUE + (random.nextFloat() * (MAX_NOISE_VALUE - MIN_NOISE_VALUE));
                break;
                
            case NORMAL:
            default:
                // Generate normal safe value
                value = MIN_NOISE_VALUE + (random.nextFloat() * (Constants.NOISE_THRESHOLD - MIN_NOISE_VALUE - 10.0f));
                break;
        }
        
        // Create JSON data matching the expected format from real sensors
        JSONObject json = new JSONObject();
        try {
            json.put("type", SensorData.TYPE_NOISE);
            json.put("value", value);
            json.put("timestamp", System.currentTimeMillis());
        } catch (JSONException e) {
            Log.e(TAG, "Error creating JSON for test data", e);
        }
        
        // Create a sensor data object marked as test data
        SensorData data = new SensorData(SensorData.TYPE_NOISE, value, System.currentTimeMillis());
        data.setSource(SensorData.SOURCE_TEST);
        data.setMetadata(json.toString());
        
        return data;
    }
    
    /**
     * Clean up resources
     */
    public void cleanup() {
        stopTestMode();
        handler.removeCallbacksAndMessages(null);
        legacyListener = null;
        mockBleManager = null;
        dustCharacteristic = null;
        noiseCharacteristic = null;
    }
    
    /**
     * Set the current test mode without starting generation
     * This allows updating the mode for menu UI without affecting data generation
     */
    public void setCurrentMode(TestMode mode) {
        this.currentMode = mode;
    }
} 