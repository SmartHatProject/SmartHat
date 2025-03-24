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


public class TestDataGenerator {
    private static final String TAG = "TestDataGenerator";
    //NOTE FOR HW: ADJUST OR UPDATE US FOR COMAPTI
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

    // Gas value ranges
    private static final float MIN_GAS_VALUE = 5.0f;
    private static final float MAX_GAS_VALUE = 300.0f;
    private static final float HIGH_GAS_VALUE = 180.0f;
    //NOTE FOR FUTURE: WE WILL INVENTUALLY GET RID OF SOME PARTS OF THIS (UNLESS...)
    // Test mode states
    public enum TestMode {
        OFF,            
        NORMAL,        
        HIGH_DUST,      
        HIGH_NOISE,
        HIGH_GAS,      
        RANDOM          
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
    private BluetoothGattCharacteristic gasCharacteristic;
    
    // Legacy data callback interface (for backward compatibility)
    public interface TestDataListener {
        void onTestDataGenerated(SensorData data);
    }
    
    private TestDataListener legacyListener;
    
    
    public void setMockBleManager(MockBleConnectionManager manager) {
        this.mockBleManager = manager;
        
        // Create mock characteristics when the manager is set
        createMockCharacteristics();
    }
    
    
    public void setListener(TestDataListener listener) {
        this.legacyListener = listener;
    }
   
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
    
    
    public void stopTestMode() {
        if (isRunning.getAndSet(false)) {
            handler.removeCallbacks(dataGenerationRunnable);
            Log.d(TAG, "Test mode stopped");
        }
        currentMode = TestMode.OFF;
    }
    
    
    public TestMode getCurrentMode() {
        return currentMode;
    }
    
    public boolean isTestModeActive() {
        return currentMode != TestMode.OFF && isRunning.get();
    }
    

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
            
            // Create mock characteristic for gas data
            gasCharacteristic = new BluetoothGattCharacteristic(
                    ESP32BluetoothSpec.GAS_CHARACTERISTIC_UUID,
                    BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                    BluetoothGattCharacteristic.PERMISSION_READ
            );
            
            Log.d(TAG, "Mock characteristics created");
        } catch (Exception e) {
            Log.e(TAG, "Error creating mock characteristics: " + e.getMessage(), e);
        }
    }
    
    
    private final Runnable dataGenerationRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRunning.get()) {
                return;
            }
            
            try {
                // Generate sensor data (alternating between all three sensors)
                long timestamp = System.currentTimeMillis();
                int sensorType = (int)(timestamp % 3); // 0 = dust, 1 = noise, 2 = gas
                
                switch (sensorType) {
                    case 0: // Dust
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
                        break;
                        
                    case 1: // Noise
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
                        break;
                        
                    case 2: // Gas
                        // Generate gas data
                        SensorData gasData = generateGasData();
                        
                        // Send data through mock BLE manager if available
                        if (mockBleManager != null && gasCharacteristic != null) {
                            // Set the value on the characteristic
                            String jsonStr = gasData.getMetadata();
                            gasCharacteristic.setValue(jsonStr.getBytes());
                            
                            // Simulate characteristic change event
                            mockBleManager.simulateCharacteristicChange(gasCharacteristic);
                            Log.d(TAG, "Simulated gas characteristic change");
                        }
                        
                        // Also send through legacy direct callback if set
                        if (legacyListener != null) {
                            legacyListener.onTestDataGenerated(gasData);
                        }
                        break;
                }
                
                // Schedule next generation based on mode
                long interval = currentMode == TestMode.RANDOM ? RAPID_INTERVAL_MS : NORMAL_INTERVAL_MS;
                handler.postDelayed(this, interval);
                
            } catch (Exception e) {
                Log.e(TAG, "Error generating test data: " + e.getMessage(), e);
                
                // Reschedule despite error
                handler.postDelayed(this, NORMAL_INTERVAL_MS);
            }
        }
    };
    
    
    private SensorData generateDustData() {
        float value;
        
        switch (currentMode) {
            case HIGH_DUST:
                
                value = HIGH_DUST_VALUE + (random.nextFloat() * 50.0f);
                break;
                
            case RANDOM:
                
                value = MIN_DUST_VALUE + (random.nextFloat() * (MAX_DUST_VALUE - MIN_DUST_VALUE));
                break;
                
            case NORMAL:
            default:
                
                value = MIN_DUST_VALUE + (random.nextFloat() * (Constants.DUST_THRESHOLD - MIN_DUST_VALUE - 20.0f));
                break;
        }
        
        
        JSONObject json = new JSONObject();
        try {
            json.put("type", SensorData.TYPE_DUST);
            json.put("value", value);
            json.put("timestamp", System.currentTimeMillis());
        } catch (JSONException e) {
            Log.e(TAG, "Error creating JSON for test data", e);
        }
        
        
        SensorData data = new SensorData(SensorData.TYPE_DUST, value, System.currentTimeMillis());
        data.setSource(SensorData.SOURCE_TEST);
        data.setMetadata(json.toString());
        
        return data;
    }
    
    
    private SensorData generateNoiseData() {
        float value;
        
        switch (currentMode) {
            case HIGH_NOISE:
                
                value = HIGH_NOISE_VALUE + (random.nextFloat() * 20.0f);
                break;
                
            case RANDOM:
                
                value = MIN_NOISE_VALUE + (random.nextFloat() * (MAX_NOISE_VALUE - MIN_NOISE_VALUE));
                break;
                
            case NORMAL:
            default:
                
                value = MIN_NOISE_VALUE + (random.nextFloat() * (Constants.NOISE_THRESHOLD - MIN_NOISE_VALUE - 10.0f));
                break;
        }
        
       
        JSONObject json = new JSONObject();
        try {
            json.put("type", SensorData.TYPE_NOISE);
            json.put("value", value);
            json.put("timestamp", System.currentTimeMillis());
        } catch (JSONException e) {
            Log.e(TAG, "Error creating JSON for test data", e);
        }
        
        
        SensorData data = new SensorData(SensorData.TYPE_NOISE, value, System.currentTimeMillis());
        data.setSource(SensorData.SOURCE_TEST);
        data.setMetadata(json.toString());
        
        return data;
    }
    
    private SensorData generateGasData() {
        float value;
        
        switch (currentMode) {
            case HIGH_GAS:
                // High gas values
                value = HIGH_GAS_VALUE + (random.nextFloat() * 50.0f);
                break;
                
            case RANDOM:
                // Random values across entire range
                value = MIN_GAS_VALUE + (random.nextFloat() * (MAX_GAS_VALUE - MIN_GAS_VALUE));
                break;
                
            case NORMAL:
            default:
                // Normal values - below dangerous levels
                value = MIN_GAS_VALUE + (random.nextFloat() * 80.0f);
                break;
        }
        
        // Create metadata JSON
        JSONObject json = new JSONObject();
        try {
            json.put("type", SensorData.TYPE_GAS);
            json.put("value", value);
            json.put("timestamp", System.currentTimeMillis());
        } catch (JSONException e) {
            Log.e(TAG, "Error creating JSON for gas test data", e);
        }
        
        // Create sensor data object
        SensorData data = new SensorData(SensorData.TYPE_GAS, value, System.currentTimeMillis());
        data.setSource(SensorData.SOURCE_TEST);
        data.setMetadata(json.toString());
        
        return data;
    }
    
    public void cleanup() {
        stopTestMode();
        handler.removeCallbacksAndMessages(null);
        legacyListener = null;
        mockBleManager = null;
        dustCharacteristic = null;
        noiseCharacteristic = null;
        gasCharacteristic = null;
    }
    
   
    public void setCurrentMode(TestMode mode) {
        this.currentMode = mode;
    }
} 