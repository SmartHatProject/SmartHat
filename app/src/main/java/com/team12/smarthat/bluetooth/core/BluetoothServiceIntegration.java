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
 * integration class that bridges bleconnectionmanager with sensor data handling
 * 
 * this class provides sensor data updates through a listener pattern and handles
 * esp32-specific ble operations. optimized for android 12 on pixel 4a devices.
 */
@SuppressLint("MissingPermission")
public class BluetoothServiceIntegration implements 
        BleConnectionManager.CharacteristicChangeListener {
    private static final String TAG = "BtServiceIntegration";
    
    // constants for sensor types - use sensordata constants for consistency
    public static final String SENSOR_TYPE_DUST = SensorData.TYPE_DUST;
    public static final String SENSOR_TYPE_NOISE = SensorData.TYPE_NOISE;
    
    // sensor value bounds for validation
    private static final float MAX_DUST_VALUE = 1000.0f;
    private static final float MAX_NOISE_VALUE = 150.0f;
    
    private final BleConnectionManager connectionManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    // listeners for sensor data - thread-safe collection
    private final List<SensorDataListener> dataListeners = new CopyOnWriteArrayList<>();
    
    // uuids of characteristics we're monitoring - final for better memory safety
    private final UUID dustCharacteristicUuid;
    private final UUID soundCharacteristicUuid;
    
    // prevent multiple initialization attempts
    private final AtomicBoolean notificationsSetup = new AtomicBoolean(false);
    
    // observer for connection state changes
    private Observer<BleConnectionManager.ConnectionState> connectionStateObserver;
    
    /**
     * interface for sensor data listeners
     * simplified to reduce redundant code and improve extensibility
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
     * optimized for android 12 on pixel 4a
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
     * thread-safe implementation
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
     * thread-safe implementation
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
     * optimized for android 12 on pixel 4a with esp32
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
            
            String stringData = new String(data);
            
            // determine sensor type from uuid with null safety
            String sensorType = null;
            if (dustCharacteristicUuid.equals(uuid)) {
                sensorType = SENSOR_TYPE_DUST;
            } else if (soundCharacteristicUuid.equals(uuid)) {
                sensorType = SENSOR_TYPE_NOISE;
            } else {
                Log.d(TAG, "Unknown characteristic changed: " + uuid);
                return;
            }
            
            // process the data - only pass normalized types to ensure consistency
            parseSensorData(stringData, sensorType);
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing characteristic data: " + e.getMessage(), e);
        }
    }
    
    /**
     * set up notifications for esp32 sensor characteristics
     * optimized for android 12 on pixel 4a with esp32
     */
    private void setupNotifications() {
        // check if notifications are already set up - use atomic boolean for thread safety
        if (notificationsSetup.getAndSet(true)) {
            Log.d(TAG, "Notifications already set up, skipping");
            return;
        }
        
        // get gatt but perform early return if null
        final BluetoothGatt gatt = connectionManager.getBluetoothGatt();
        if (gatt == null) {
            Log.e(TAG, "Cannot setup notifications - GATT is null");
            notificationsSetup.set(false); // reset so we can try again
            return;
        }
        
        // find the service first to avoid multiple service lookups
        final BluetoothGattService service = gatt.getService(ESP32BluetoothSpec.SERVICE_UUID);
        if (service == null) {
            Log.e(TAG, "ESP32 service not found");
            notificationsSetup.set(false); // reset so we can try again
            return;
        }
        
        // run on main thread to avoid ble threading issues on android 12
        mainHandler.post(() -> {
            try {
                Log.d(TAG, "Setting up notifications for ESP32 sensors");
                
                // find characteristics
                BluetoothGattCharacteristic dustChar = 
                    service.getCharacteristic(dustCharacteristicUuid);
                BluetoothGattCharacteristic soundChar = 
                    service.getCharacteristic(soundCharacteristicUuid);
                
                // check if both characteristics exist
                if (dustChar == null && soundChar == null) {
                    Log.e(TAG, "No sensor characteristics found in ESP32 service");
                    notificationsSetup.set(false);
                    return;
                }
                
                // track overall success
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
                
                // if both failed, reset the notification setup flag
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
     * this is especially important for android 12 which has stricter ble requirements
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
     * allows for lambda-based execution of gatt operations with consistent error handling
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
     * optimized for esp32 devices on android 12
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
            // this step is important because it tells the Android BluetoothGatt system
            // that we want characteristic changed callbacks for this characteristic
            boolean setCharResult;
            
            // on android 12, we must use the new api if available
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // android 13+ (api 33+) using the new api
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
            
            // step 2: write the descriptor to enable remote notifications
            // find the Client Characteristic Configuration Descriptor (CCCD)
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
                    // fallback to hardcoded value if reflection fails
                    Log.w(TAG, "Reflection failed, using hardcoded value: " + e.getMessage());
                    enableValue = new byte[]{0x01, 0x00}; // Standard value for notifications
                }
            } else {
                // pre-android 13
                enableValue = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
            }
            
            // write the descriptor value
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
     * optimized for esp32 sensor data format
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
            
            // normalize sensor type to lowercase for consistency
            final String normalizedType = sensorType.toLowerCase();
            
            // extract the data value with bounds checking
            float value = (float) json.optDouble("value", 0.0);
            
            // validate and normalize values based on sensor type
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
            long timestamp = json.optLong("timestamp", System.currentTimeMillis());
            
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
            Log.e(TAG, "Error parsing sensor JSON: " + e.getMessage(), e);
        } catch (Exception e) {
            Log.e(TAG, "Error processing sensor data: " + e.getMessage(), e);
        }
    }
    
    /**
     * cleanup resources used by this class
     * this should be called when the component is no longer needed
     * optimized for android 12 on pixel 4a
     */
    public void cleanup() {
        Log.d(TAG, "Cleaning up BluetoothServiceIntegration");
        
        // first unregister all listeners to prevent callback during cleanup
        try {
            // clear the connection state observer if it exists - observer pattern is already lifecycle-aware
            // but we need to clean up references to prevent potential memory leaks
            if (connectionStateObserver != null) {
                try {
                    // this is safe to call even if the observer is lifecycle-bound
                    // as it will remove our reference from the LiveData
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
            
            // clear all handler callbacks to prevent delayed execution after cleanup
            if (mainHandler != null) {
                mainHandler.removeCallbacksAndMessages(null);
                Log.d(TAG, "Cleared all pending handler callbacks");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during cleanup: " + e.getMessage(), e);
        }
    }
}