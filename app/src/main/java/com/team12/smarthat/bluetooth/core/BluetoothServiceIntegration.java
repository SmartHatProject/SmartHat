package com.team12.smarthat.bluetooth.core;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;

import com.team12.smarthat.bluetooth.devices.esp32.ESP32BluetoothSpec;
import com.team12.smarthat.models.SensorData;
import com.team12.smarthat.utils.Constants;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * integration class 
 * 
 * sensor data updates through a listener pattern and handles
 * 
 * ESP32 Fallback Behaviors (for reference):
 * - JSON creation failure → valid JSON with defaults 
 * - invalid sound readings → 30.0 dB
 * - dust below 0.6V → 0.0  (clean air)
 * -non zero initial values: 40.0 dB (sound), 10.0 (dust)
 */
@SuppressLint("MissingPermission")
public class BluetoothServiceIntegration implements 
        BleConnectionManager.CharacteristicChangeListener {
    private static final String TAG = "BtServiceIntegration";
    
    // constants for sensor types - use sensordata constants for consistency
    public static final String SENSOR_TYPE_DUST = SensorData.TYPE_DUST;
    public static final String SENSOR_TYPE_NOISE = SensorData.TYPE_NOISE;
    
    //sensor value bounds for validation
    private static final float MAX_DUST_VALUE = 1000.0f;
    private static final float MAX_NOISE_VALUE = 150.0f;
    
    private final BleConnectionManager connectionManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    // listeners for sensor data
    private final List<SensorDataListener> dataListeners = new CopyOnWriteArrayList<>();
    
    // uuids of characteristics we're monitoring 
    private final UUID dustCharacteristicUuid;
    private final UUID soundCharacteristicUuid;
    
    // prevent multiple initialization attempts
    private final AtomicBoolean notificationsSetup = new AtomicBoolean(false);
    
    // observer for connection state changes
    private Observer<BleConnectionManager.ConnectionState> connectionStateObserver;
    
    /**
     * interface for sensor data listener simplified
     */
    public interface SensorDataListener {
        /**
         * called when sensor data is received
         * @param data the sensor data object
         * @param sensorType the type of sensor (use sensor_type_* constants)
         */
        void onSensorData(SensorData data, String sensorType);
    }
    
    /**
     * constructor that takes just the connection manager
     */
    public BluetoothServiceIntegration(BleConnectionManager connectionManager) {
        if (connectionManager == null) {
            throw new IllegalArgumentException("BleConnectionManager cannot be null");
        }
        this.connectionManager = connectionManager;
        
        // initialize uuids from esp32 spec
        this.dustCharacteristicUuid = ESP32BluetoothSpec.DUST_CHARACTERISTIC_UUID;
        this.soundCharacteristicUuid = ESP32BluetoothSpec.SOUND_CHARACTERISTIC_UUID;
        
        // register as a characteristic change listener
        connectionManager.setCharacteristicChangeListener(this);
        
        Log.d(TAG, "BluetoothServiceIntegration initialized for ESP32");
    }
    
    /**
     * observe connection state changes from the connection manager
     * @param lifecycleOwner the lifecycle owner to bind the observer to
     */
    public void observeConnectionState(LifecycleOwner lifecycleOwner) {
        if (lifecycleOwner == null) {
            Log.e(TAG, "Cannot observe connection state - lifecycleOwner is null");
            return;
        }
        
        // remove any existing observer first to prevent leaks
        if (connectionStateObserver != null) {
            connectionManager.getConnectionState().removeObserver(connectionStateObserver);
            connectionStateObserver = null;
        }
        
        // create and register a new observer
        connectionStateObserver = state -> {
            if (state == BleConnectionManager.ConnectionState.CONNECTED) {
                // set up notifications when connected
                setupNotifications();
            } else if (state == BleConnectionManager.ConnectionState.DISCONNECTED) {
                // reset notification setup flag when disconnected so we'll set up again on reconnect
                notificationsSetup.set(false);
            }
        };
        
        // observe connection state changes with lifecycle awareness
        connectionManager.getConnectionState().observe(lifecycleOwner, connectionStateObserver);
        
        Log.d(TAG, "Connection state observer registered with lifecycle");
    }
    
    /**
     * add a sensor data listener
     * thread safe 
     */
    public void addSensorDataListener(SensorDataListener listener) {
        if (listener == null) {
            Log.w(TAG, "Attempted to add null sensor data listener");
            return;
        }
        
        if (!dataListeners.contains(listener)) {
            dataListeners.add(listener);
            Log.d(TAG, "Sensor data listener added, total: " + dataListeners.size());
        }
    }
    
    /**
     * remove a sensor data listener
     * thread safe implementation
     */
    public void removeSensorDataListener(SensorDataListener listener) {
        if (listener == null) {
            return;
        }
        
        dataListeners.remove(listener);
        Log.d(TAG, "Sensor data listener removed, remaining: " + dataListeners.size());
    }
    
    /**
     * implementation of CharacteristicChangeListener interface
     */
    @Override
    public void onCharacteristicChanged(BluetoothGattCharacteristic characteristic) {
        UUID uuid = characteristic.getUuid();
        
        try {
            // parse sensor data from characteristic
            byte[] data = characteristic.getValue();
            if (data == null || data.length == 0) {
                Log.w(TAG, "Empty characteristic data received");
                return;
            }
            
            // use utf-8 encoding all versions 
            String stringData = new String(data, java.nio.charset.StandardCharsets.UTF_8);
            
            //logs
            Log.d(TAG, "Raw data from ESP32: " + stringData);
            Log.d(TAG, "Characteristic UUID: " + uuid.toString());
            Log.d(TAG, "Data length: " + data.length + " bytes");
            
            // sensor type from uuid with null safety check
            String sensorType = null;
            if (dustCharacteristicUuid.equals(uuid)) {
                sensorType = SENSOR_TYPE_DUST;
                Log.d(TAG, "Identified as DUST sensor data");
            } else if (soundCharacteristicUuid.equals(uuid)) {
                sensorType = SENSOR_TYPE_NOISE;
                Log.d(TAG, "Identified as NOISE sensor data");
            } else {
                Log.d(TAG, "Unknown characteristic changed: " + uuid);
                return;
            }
            
            // process the data only pass normalized types to ensure consistency throughout the app
            parseSensorData(stringData, sensorType);
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing characteristic data: " + e.getMessage(), e);
        }
    }
    
    /**
     * set up notifications for esp32 sensor characteristics
     */
    private void setupNotifications() {
        // check if notifications are already set up - use atomic boolean for thread safety
        if (notificationsSetup.getAndSet(true)) {
            Log.d(TAG, "Notifications already set up, skipping");
            return;
        }
        
        // get gatt but perform early return if null to avoid null pointer exceptions
        final BluetoothGatt gatt = connectionManager.getBluetoothGatt();
        if (gatt == null) {
            Log.e(TAG, "Cannot setup notifications - GATT is null");
            notificationsSetup.set(false); // reset so we can try again
            return;
        }
        
        // find the service first to avoid multiple service lookups which can be expensive
        final BluetoothGattService service = gatt.getService(ESP32BluetoothSpec.SERVICE_UUID);
        if (service == null) {
            Log.e(TAG, "ESP32 service not found");
            notificationsSetup.set(false); // reset so we can try again
            return;
        }
        
        // run on main thread to avoid ble threading issues 
        mainHandler.post(() -> {
            try {
                Log.d(TAG, "Setting up notifications for ESP32 sensors");
                
                // find characteristics for both sensors
                BluetoothGattCharacteristic dustChar = 
                    service.getCharacteristic(dustCharacteristicUuid);
                BluetoothGattCharacteristic soundChar = 
                    service.getCharacteristic(soundCharacteristicUuid);
                
                // check if both characteristics exist before proceeding
                if (dustChar == null && soundChar == null) {
                    Log.e(TAG, "No sensor characteristics found in ESP32 service");
                    notificationsSetup.set(false);
                    return;
                }
                
                // track overall success to know if we need to retry later
                boolean overallSuccess = true;
                
                // enable dust sensor notifications if available
                if (dustChar != null) {
                    boolean dustSuccess = executeGattOperationSafely(gatt, () -> {
                        return enableNotification(gatt, dustChar);
                    });
                    Log.d(TAG, "Dust sensor notification setup: " + (dustSuccess ? "success" : "failed"));
                    overallSuccess &= dustSuccess;
                }
                
                // enable sound sensor notifications if available
                if (soundChar != null) {
                    boolean soundSuccess = executeGattOperationSafely(gatt, () -> {
                        return enableNotification(gatt, soundChar);
                    });
                    Log.d(TAG, "Sound sensor notification setup: " + (soundSuccess ? "success" : "failed"));
                    overallSuccess &= soundSuccess;
                }
                
                // if both failed, reset the notification setup flag so we can try again
                if (!overallSuccess) {
                    Log.w(TAG, "Failed to set up all notifications, will try again on next connection");
                    notificationsSetup.set(false);
                } else {
                    Log.d(TAG, "ESP32 sensor notifications successfully enabled");
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error setting up notifications: " + e.getMessage(), e);
                notificationsSetup.set(false); // reset so we can try again
            }
        });
    }
    
    /**
     * execute a gatt operation safely with error handling
     */
    private boolean executeGattOperationSafely(BluetoothGatt gatt, GattOperation operation) {
        if (gatt == null) {
            Log.e(TAG, "Cannot execute GATT operation - GATT is null");
            return false;
        }
        
        if (operation == null) {
            Log.e(TAG, "Cannot execute GATT operation - operation is null");
            return false;
        }
        
        try {
            return operation.execute();
        } catch (SecurityException se) {
            // this is specifically for android 12's stricter permission requirements
            Log.e(TAG, "Security exception during GATT operation: " + se.getMessage(), se);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error executing GATT operation: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * functional interface for gatt operations
     */
    private interface GattOperation {
        /**
         * execute the gatt operation
         * @return true if successful, false otherwise
         * @throws Exception if an error occurs during execution
         */
        boolean execute() throws Exception;
    }
    
    /**
     * enable notifications for a characteristic
     * 
     * @param gatt the bluetoothgatt connection
     * @param characteristic the characteristic to enable notifications for
     * @return true if the request was initiated successfully, false otherwise
     */
    private boolean enableNotification(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        if (gatt == null || characteristic == null) {
            Log.e(TAG, "Cannot enable notification - parameters are null");
            return false;
        }
        
        final UUID charUuid = characteristic.getUuid();
        
        try {
            // step 1: enable local notifications
            // tells the Android BluetoothGatt system we want characteristic changed callbacks for this characteristic
            boolean setCharResult;
            
           
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // 13+ spec
                try {
                    // call via reflection to maintain backward compatibility
                    Method setCharNotificationMethod = gatt.getClass().getMethod(
                            "setCharacteristicNotification", 
                            BluetoothGattCharacteristic.class, 
                            boolean.class);
                    setCharResult = (boolean) setCharNotificationMethod.invoke(gatt, characteristic, true);
                } catch (Exception e) {
                    // fallback to standard method if reflection fails
                    Log.w(TAG, "Reflection failed, using standard method: " + e.getMessage());
                    setCharResult = gatt.setCharacteristicNotification(characteristic, true);
                }
            } else {
                // pre-android 13 using the standard api
                setCharResult = gatt.setCharacteristicNotification(characteristic, true);
            }
            
            if (!setCharResult) {
                Log.e(TAG, "Failed to set local notification for " + charUuid);
                return false;
            }
            
            // step 2 descriptor to enable notifications
            // find CCCD
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    ESP32BluetoothSpec.CLIENT_CONFIG_DESCRIPTOR_UUID);
            
            if (descriptor == null) {
                Log.e(TAG, "CCCD not found for " + charUuid);
                return false;
            }
            
            // value for enabling notifications
            byte[] enableValue;
            
            // get the latest recommended value for enabling notifications
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // android 13+ using BluetoothGattDescriptor constants
                try {
                    // use reflection to get the ENABLE_NOTIFICATION_VALUE constant
                    Field enableNotificationField = BluetoothGattDescriptor.class.getField("ENABLE_NOTIFICATION_VALUE");
                    enableValue = (byte[]) enableNotificationField.get(null);
                } catch (Exception e) {
                    //fallback to hardcoded value if reflection fails only
                    Log.w(TAG, "Reflection failed, using hardcoded value: " + e.getMessage());
                    enableValue = new byte[]{0x01, 0x00}; // Standard value for notifications
                }
            } else {
                // 13-
                enableValue = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
            }
            
            //descriptor value
            if (!descriptor.setValue(enableValue)) {
                Log.e(TAG, "Failed to set descriptor value for " + charUuid);
                return false;
            }
            
            // queue the descriptor write operation
            if (!gatt.writeDescriptor(descriptor)) {
                Log.e(TAG, "Failed to queue descriptor write for " + charUuid);
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error enabling notification for " + charUuid + ": " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * parse sensor data from json string
     */
    private void parseSensorData(String jsonData, String sensorType) {
        // early return if no listeners or invalid data
        if (dataListeners.isEmpty()) {
            return;
        }
        
        if (jsonData == null || jsonData.isEmpty()) {
            Log.w(TAG, "Empty JSON data received");
            return;
        }
        
        if (sensorType == null || sensorType.isEmpty()) {
            Log.w(TAG, "Invalid sensor type received");
            return;
        }
        
        try {
            // parse json data
            JSONObject json = new JSONObject(jsonData);
            
            // log full json for debugging purposes
            Log.d(TAG, "Parsing JSON: " + jsonData);
            
            // normalize sensor type to lowercase for consistency across the app
            final String normalizedType = sensorType.toLowerCase();
            
            // extract the data value with bounds checking
            // handle both esp32 hardware format ("data") and test mode format ("value")
            float value;
            if (json.has("data")) {
                // esp32 hardware format used when connected to real device
                value = (float) json.optDouble("data", 0.0);
                Log.d(TAG, "Found 'data' field in JSON: " + value);
            } else if (json.has("value")) {
                // test mode format (sim mode only)
                value = (float) json.optDouble("value", 0.0);
                Log.d(TAG, "Found 'value' field in JSON: " + value);
            } else {
                Log.w(TAG, "No data/value field found in JSON: " + jsonData);
                value = 0.0f;
            }
            
            // add logging for successful non zero values 
            if (value > 0) {
                Log.i(TAG, "Successfully parsed non-zero value: " + value + " for sensor type: " + normalizedType);
            }
            
            // validate and normalize values based on sensor type to ensure readings are within expected ranges
            if (SENSOR_TYPE_DUST.equals(normalizedType)) {
                if (value < 0 || value > MAX_DUST_VALUE) {
                    Log.w(TAG, "Suspicious dust value received: " + value + ", clamping to valid range");
                    value = Math.max(0, Math.min(value, MAX_DUST_VALUE));
                }
            } else if (SENSOR_TYPE_NOISE.equals(normalizedType)) {
                if (value < 0 || value > MAX_NOISE_VALUE) {
                    Log.w(TAG, "Suspicious noise value received: " + value + ", clamping to valid range");
                    value = Math.max(0, Math.min(value, MAX_NOISE_VALUE));
                }
            } else {
                Log.w(TAG, "Unknown sensor type: " + normalizedType);
                return;
            }
            
            // get timestamp or use current time
            // handle both esp32 format ("timeStamp") and test mode format ("timestamp")
            long timestamp;
            if (json.has("timeStamp")) {
                // esp32 hardware format with capital 'S' in timeStamp
                timestamp = json.optLong("timeStamp", System.currentTimeMillis());
                Log.d(TAG, "Found 'timeStamp' field in JSON: " + timestamp);
            } else if (json.has("timestamp")) {
                // test mode format with lowercase 's' in timestamp
                timestamp = json.optLong("timestamp", System.currentTimeMillis());
                Log.d(TAG, "Found 'timestamp' field in JSON: " + timestamp);
            } else {
                Log.w(TAG, "No timestamp field found in JSON: " + jsonData);
                timestamp = System.currentTimeMillis();
            }
            
            // check for messageType field esp32 forma to verify data source
            if (json.has("messageType")) {
                String messageType = json.optString("messageType");
                Log.d(TAG, "Found 'messageType' field in JSON: " + messageType);
                
                // verify the messageType matches the expected type for this characteristic
                if (messageType.equals("DUST_SENSOR_DATA") && !SENSOR_TYPE_DUST.equals(normalizedType)) {
                    Log.w(TAG, "MessageType mismatch: Expected dust sensor data for dust characteristic");
                } else if (messageType.equals("SOUND_SENSOR_DATA") && !SENSOR_TYPE_NOISE.equals(normalizedType)) {
                    Log.w(TAG, "MessageType mismatch: Expected sound sensor data for noise characteristic");
                }
            }
            
            // create sensor data object with normalized type
            final SensorData data = new SensorData(normalizedType, value, timestamp);
            
            // check for test data flag in json
            if (json.has("test") && json.optBoolean("test", false)) {
                data.setSource(SensorData.SOURCE_TEST);
            }
            
            // store any additional metadata
            if (json.length() > 3) { // More than just type, value, timestamp
                data.setMetadata(jsonData);
            }
            
            // use a local copy of the list to prevent concurrent modification issues
            final List<SensorDataListener> listeners = new ArrayList<>(dataListeners);
            final float finalValue = value;
            
            // post to main thread to avoid blocking ble operations
            mainHandler.post(() -> {
                for (SensorDataListener listener : listeners) {
                    try {
                        // pass the same normalized type that was used in the SensorData constructor
                        listener.onSensorData(data, normalizedType);
                    } catch (Exception e) {
                        // prevent one bad listener from breaking others
                        Log.e(TAG, "Error in sensor data listener: " + e.getMessage());
                    }
                }
                
                // log data for debugging
                if (data.isTestData()) {
                    Log.d(TAG, "Test data: " + normalizedType + "=" + finalValue);
                } else {
                    Log.d(TAG, "Sensor data: " + normalizedType + "=" + finalValue);
                }
            });
            
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing sensor JSON: " + e.getMessage() + ", Raw data: " + jsonData);
        } catch (Exception e) {
            Log.e(TAG, "Error processing sensor data: " + e.getMessage() + ", Raw data: " + jsonData);
        }
    }
    
    /**
     * cleanup resources used by this class
     */
    public void cleanup() {
        Log.d(TAG, "Cleaning up BluetoothServiceIntegration");
        
        try {
           
            // but we need to clean up references to prevent potential memory leaks
            if (connectionStateObserver != null) {
                try {
                 
                    //removes our reference from the LiveData
                    connectionManager.getConnectionState().removeObserver(connectionStateObserver);
                    Log.d(TAG, "Removed connection state observer");
                } catch (Exception e) {
                    Log.w(TAG, "Error removing connection state observer: " + e.getMessage());
                }
                connectionStateObserver = null;
            }
            
            // unregister characteristic change listener
            try {
                connectionManager.setCharacteristicChangeListener(null);
                Log.d(TAG, "Unregistered characteristic change listener");
            } catch (Exception e) {
                Log.w(TAG, "Error unregistering characteristic change listener: " + e.getMessage());
            }
            
            // clear all data listeners
            if (!dataListeners.isEmpty()) {
                dataListeners.clear();
                Log.d(TAG, "Cleared all sensor data listeners");
            }
            
            // reset notification setup flag
            notificationsSetup.set(false);
            
           
            if (mainHandler != null) {
                mainHandler.removeCallbacksAndMessages(null);
                Log.d(TAG, "Cleared all pending handler callbacks");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during cleanup: " + e.getMessage(), e);
        }
    }
}