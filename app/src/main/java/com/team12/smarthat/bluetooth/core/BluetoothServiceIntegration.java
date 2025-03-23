package com.team12.smarthat.bluetooth.core;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;


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
    
    // Notification timeout constants - using values from ESP32BluetoothSpec
    private static final long NOTIFICATION_TIMEOUT_MS = ESP32BluetoothSpec.NotificationParams.NOTIFICATION_TIMEOUT_MS;
    private static final long NOTIFICATION_CHECK_INTERVAL_MS = 2000; // Check every 2 seconds
    private static final int MAX_VERIFICATION_ATTEMPTS = 3; // Try to verify notifications three times before reconnecting
    
    // Queue management constants
    private static final int MAX_QUEUE_SIZE = 100; // Maximum number of notifications to queue
    private static final long MAX_TIMESTAMP_DEVIATION = ESP32BluetoothSpec.NotificationParams.MAX_TIMESTAMP_DEVIATION_MS;
    private static final int BATCH_PROCESSING_SIZE = 10; // Process notifications in batches for better efficiency
    
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
    
    // Notification timeout tracking
    private long lastNotificationTimestamp = 0;
    private int verificationAttempts = 0;
    private final Runnable timeoutCheckRunnable = new Runnable() {
        @Override
        public void run() {
            checkNotificationTimeout();
            // Schedule next check
            mainHandler.postDelayed(this, NOTIFICATION_CHECK_INTERVAL_MS);
        }
    };
    
    // Track the last processed timestamps by sensor type to detect out-of-order
    private final Map<String, Long> lastProcessedTimestamps = new HashMap<>();
    
    // Queue for notification processing
    private final ConcurrentLinkedQueue<NotificationData> notificationQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean processingQueue = new AtomicBoolean(false);
    private final Handler backgroundHandler;
    
    // Last valid readings for each sensor type to use as fallbacks
    private float lastValidDustReading = ESP32BluetoothSpec.NotificationParams.INITIAL_DUST_VALUE; // Default to clean air value
    private float lastValidNoiseReading = ESP32BluetoothSpec.NotificationParams.INITIAL_SOUND_VALUE; // Default to quiet room value
    
    // Class to store notification data
    private static class NotificationData {
        public final BluetoothGattCharacteristic characteristic;
        public final long receivedTimestamp;
        
        public NotificationData(BluetoothGattCharacteristic characteristic) {
            this.characteristic = characteristic;
            this.receivedTimestamp = System.currentTimeMillis();
        }
    }
    
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
        
        // Initialize background handler with a dedicated thread
        HandlerThread handlerThread = new HandlerThread("NotificationProcessingThread");
        handlerThread.start();
        this.backgroundHandler = new Handler(handlerThread.getLooper());
        
        // register as a characteristic change listener
        connectionManager.setCharacteristicChangeListener(this);
        
        // Start notification timeout check
        mainHandler.postDelayed(timeoutCheckRunnable, NOTIFICATION_TIMEOUT_MS);
        
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
        
        if (dataListeners.remove(listener)) {
            Log.d(TAG, "Sensor data listener removed, total: " + dataListeners.size());
        }
    }
    
    /**
     * parse and process sensor data from json string
     */
    private void parseSensorData(String jsonData, String sensorType) {
        if (jsonData == null || jsonData.isEmpty()) {
            Log.e(TAG, "Empty JSON data received");
            return;
        }
        
        if (sensorType == null) {
            Log.e(TAG, "Sensor type is null");
            return;
        }
        
        try {
            JSONObject json = new JSONObject(jsonData);
            
            // extract message type
            String messageType = json.optString("messageType", "");
            if (messageType.isEmpty()) {
                Log.w(TAG, "Missing messageType in JSON: " + jsonData);
            } else {
                Log.d(TAG, "Message type: " + messageType);
            }
            
            // extract data value - check both "data" (hardware format) and "value" (test mode format)
            double value = 0.0;
            if (json.has("data")) {
                value = json.optDouble("data", 0.0);
            } else if (json.has("value")) {
                value = json.optDouble("value", 0.0);
            }
            
            long timestamp = 0;
            if (json.has("timeStamp")) {
                timestamp = json.optLong("timeStamp", 0);
            } else if (json.has("timestamp")) {
                timestamp = json.optLong("timestamp", 0);
            }
            
            Log.d(TAG, "Parsed sensor data - type: " + sensorType + ", value: " + value + ", timestamp: " + timestamp);
            
            // validate data based on sensor type
            if (SENSOR_TYPE_DUST.equals(sensorType)) {
                if (value < 0 || value > MAX_DUST_VALUE) {
                    Log.w(TAG, "Dust sensor value out of range: " + value);
                    // we'll still create and dispatch the data 
                } else {
                    // Only update last valid reading if the value is valid
                    lastValidDustReading = (float)value;
                }
            } else if (SENSOR_TYPE_NOISE.equals(sensorType)) {
                if (value < 0 || value > MAX_NOISE_VALUE) {
                    Log.w(TAG, "Noise sensor value out of range: " + value);
                    // we'll still create and dispatch the data 
                } else {
                    // Only update last valid reading if the value is valid
                    lastValidNoiseReading = (float)value;
                }
            }
            
            // create sensor data object
            SensorData sensorData = new SensorData(sensorType, (float)value, timestamp);
            
            // notify listeners on main thread
            notifyListeners(sensorData, sensorType);
            
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing JSON: " + e.getMessage());
            Log.e(TAG, "Invalid JSON: " + jsonData);
            
          
            float fallbackValue = SENSOR_TYPE_DUST.equals(sensorType) ? lastValidDustReading : lastValidNoiseReading;
            Log.w(TAG, "Using last valid reading as fallback: " + fallbackValue + " for " + sensorType);
            
            // Create sensor data with fallback value and current timestamp
            SensorData fallbackData = new SensorData(sensorType, fallbackValue, System.currentTimeMillis());
            notifyListeners(fallbackData, sensorType);
        }
    }
    
    /**
     * notify all registered listeners on the main thread
     */
    private void notifyListeners(final SensorData data, final String sensorType) {
        if (data == null || sensorType == null) {
            return;
        }
        
        // dispatch on main thread
        mainHandler.post(() -> {
            for (SensorDataListener listener : dataListeners) {
                try {
                    listener.onSensorData(data, sensorType);
                } catch (Exception e) {
                    Log.e(TAG, "Error notifying listener: " + e.getMessage(), e);
                }
            }
        });
    }
    
    /**
     * callback when a characteristic changes (notification received)
     */
    @Override
    public void onCharacteristicChanged(BluetoothGattCharacteristic characteristic) {
        // Update the last notification timestamp
        lastNotificationTimestamp = System.currentTimeMillis();
        
        // Reset verification attempts when we receive a notification
        verificationAttempts = 0;
        
        // Manage queue size before adding new notifications
        manageQueueSize();
        
        // Queue the notification for processing
        notificationQueue.add(new NotificationData(characteristic));
        
        // Start processing the queue if not already processing
        processNotificationQueue();
    }
    
    /**
     * Manage the notification queue size to prevent memory issues
     */
    private void manageQueueSize() {
        // If queue is already at or exceeding max size, remove oldest notifications
        if (notificationQueue.size() >= MAX_QUEUE_SIZE) {
            Log.w(TAG, "Notification queue reached maximum size (" + MAX_QUEUE_SIZE + 
                  "), removing oldest notifications");
            
            int toRemove = notificationQueue.size() - MAX_QUEUE_SIZE + 1; // +1 to make room for the new one
            for (int i = 0; i < toRemove; i++) {
                notificationQueue.poll(); // Remove oldest
            }
        }
    }
    
    /**
     * process all notifications in the queue with timestamp-based ordering
     */
    private void processNotificationQueue() {
        // Only one thread should process the queue at a time
        if (processingQueue.getAndSet(true)) {
            return;
        }
        
        // Process in a background thread to avoid blocking
        backgroundHandler.post(() -> {
            try {
                // Process all notifications in the queue
                List<NotificationData> notifications = new ArrayList<>();
                NotificationData notification;
                
                
                // to prevent processing too many at once if the queue is very large
                int count = 0;
                while ((notification = notificationQueue.poll()) != null && count < BATCH_PROCESSING_SIZE) {
                    notifications.add(notification);
                    count++;
                }
                
                // If no notifications to process, return
                if (notifications.isEmpty()) {
                    processingQueue.set(false);
                    return;
                }
                
                // Sort notifications by characteristic timestamp if available
                Collections.sort(notifications, (a, b) -> {
                    long aTimestamp = extractTimestamp(a.characteristic);
                    long bTimestamp = extractTimestamp(b.characteristic);
                    
                    // If timestamps are the same, sort by received time
                    if (aTimestamp == bTimestamp) {
                        return Long.compare(a.receivedTimestamp, b.receivedTimestamp);
                    }
                    
                    return Long.compare(aTimestamp, bTimestamp);
                });
                
                // Process each notification with enhanced timestamp validation
                for (NotificationData data : notifications) {
                    processCharacteristicWithTimestampValidation(data.characteristic);
                }
                
                // Release the processing lock
                processingQueue.set(false);
                
                // If there are more notifications to process, trigger another processing cycle
                if (!notificationQueue.isEmpty()) {
                    processNotificationQueue();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing notification queue: " + e.getMessage(), e);
                processingQueue.set(false);
                
                // Even if there was an error, try to process remaining notifications
                if (!notificationQueue.isEmpty()) {
                    // Small delay before retrying to avoid tight loop in case of persistent errors
                    backgroundHandler.postDelayed(this::processNotificationQueue, 100);
                }
            }
        });
    }
    
    /**
     * Process a characteristic with timestamp validation to handle out-of-order notifications
     */
    private void processCharacteristicWithTimestampValidation(BluetoothGattCharacteristic characteristic) {
        if (characteristic == null) {
            return;
        }
        
        UUID uuid = characteristic.getUuid();
        String sensorType = null;
        
        // determine sensor type from characteristic uuid
        if (uuid.equals(dustCharacteristicUuid)) {
            sensorType = SENSOR_TYPE_DUST;
        } else if (uuid.equals(soundCharacteristicUuid)) {
            sensorType = SENSOR_TYPE_NOISE;
        } else {
            Log.w(TAG, "Unknown characteristic UUID: " + uuid);
            return;
        }
        
        // extract timestamp for validation
        long timestamp = extractTimestamp(characteristic);
        
        // Check if this might be an out-of-order notification
        if (timestamp > 0 && lastProcessedTimestamps.containsKey(sensorType)) {
            long lastTimestamp = lastProcessedTimestamps.get(sensorType);
            long timeDifference = timestamp - lastTimestamp;
            
            
            if (timeDifference < 0 && Math.abs(timeDifference) < MAX_TIMESTAMP_DEVIATION) {
                Log.w(TAG, "Processing out-of-order notification for " + sensorType + 
                      ", time difference: " + timeDifference + "ms");
                // Continue processing even though it's out of order
            } else if (timeDifference < 0) {
                // This is a significantly older notification, log and skip
                Log.w(TAG, "Skipping significantly out-of-order notification for " + sensorType + 
                      ", time difference: " + timeDifference + "ms (exceeds max deviation of " + 
                      MAX_TIMESTAMP_DEVIATION + "ms)");
                return;
            }
        }
        
        // process the characteristic data
        try {
            byte[] data = characteristic.getValue();
            if (data == null || data.length == 0) {
                Log.e(TAG, "Empty characteristic data");
                return;
            }
            
            String stringData = new String(data, java.nio.charset.StandardCharsets.UTF_8);
            Log.d(TAG, "Characteristic data (" + sensorType + "): " + stringData);
            
            // parse and process the data - this notifies listeners
            parseSensorData(stringData, sensorType);
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing characteristic data: " + e.getMessage(), e);
            
            // Even if processing fails, update the timestamp record to maintain proper ordering
            if (timestamp > 0) {
                Long currentLastTimestamp = lastProcessedTimestamps.get(sensorType);
                if (currentLastTimestamp == null || timestamp > currentLastTimestamp) {
                    lastProcessedTimestamps.put(sensorType, timestamp);
                }
            }
        }
        
        // Update the last processed timestamp if this was a valid one
        if (timestamp > 0) { // Only update if we have a valid timestamp
            Long currentLastTimestamp = lastProcessedTimestamps.get(sensorType);
            if (currentLastTimestamp == null || timestamp > currentLastTimestamp) {
                lastProcessedTimestamps.put(sensorType, timestamp);
            }
        }
    }
    
    /**
     * extract timestamp from a characteristic
     */
    private long extractTimestamp(BluetoothGattCharacteristic characteristic) {
        try {
            byte[] data = characteristic.getValue();
            if (data == null || data.length == 0) {
                return 0;
            }
            
            String stringData = new String(data, java.nio.charset.StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(stringData);
            
            // Try both timeStamp (ESP32) and timestamp (test mode) formats
            if (json.has("timeStamp")) {
                // This is the primary format used by ESP32 hardware
                long timeStamp = json.optLong("timeStamp", 0);
                Log.d(TAG, "Found ESP32 hardware timeStamp format: " + timeStamp);
                return timeStamp;
            } else if (json.has("timestamp")) {
                // This is the secondary format used in test mode
                long timestamp = json.optLong("timestamp", 0);
                Log.d(TAG, "Found alternative timestamp format: " + timestamp);
                return timestamp;
            } else {
                Log.w(TAG, "No timestamp found in JSON data: " + stringData);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error extracting timestamp: " + e.getMessage());
        }
        
        return 0;
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
                    // Reset last notification timestamp to give time for initial notifications
                    lastNotificationTimestamp = System.currentTimeMillis();
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
            
            
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    ESP32BluetoothSpec.CLIENT_CONFIG_DESCRIPTOR_UUID);
            
            if (descriptor == null) {
                Log.e(TAG, "CCCD not found for " + charUuid);
                return false;
            }
            
            // value for enabling notifications
            byte[] enableValue;
            
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
               
                try {
                    
                    Field enableNotificationField = BluetoothGattDescriptor.class.getField("ENABLE_NOTIFICATION_VALUE");
                    enableValue = (byte[]) enableNotificationField.get(null);
                    
                   
                    StringBuilder sb = new StringBuilder();
                    for (byte b : enableValue) {
                        sb.append(String.format("0x%02X ", b));
                    }
                    Log.d(TAG, "Using descriptor value: " + sb.toString().trim() + " for notifications");
                } catch (Exception e) {
                    
                    Log.w(TAG, "Reflection failed, using hardcoded value: " + e.getMessage());
                    enableValue = new byte[]{0x01, 0x00}; 
                    Log.d(TAG, "Using hardcoded descriptor value: 0x01 0x00 for notifications");
                }
            } else {
                // will remove later when cleaning code
                enableValue = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
                
                Log.d(TAG, "Using standard descriptor value: 0x01 0x00 for notifications");
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
            Log.e(TAG, "Error enabling notifications: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * check if we haven't received notifications for too long
     */
    private void checkNotificationTimeout() {
        // Skip check if we're not connected
        if (connectionManager.getCurrentState() != BleConnectionManager.ConnectionState.CONNECTED) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - lastNotificationTimestamp;
        
        
        if (lastNotificationTimestamp > 0 && elapsedTime < NOTIFICATION_TIMEOUT_MS) {
            return;
        }
        
        Log.w(TAG, "No notifications received for " + elapsedTime + "ms");
        
        
        if (verificationAttempts < MAX_VERIFICATION_ATTEMPTS) {
            Log.d(TAG, "Attempting to verify notifications (attempt " + (verificationAttempts + 1) + ")");
            verificationAttempts++;
            
           
            notificationsSetup.set(false);
            
           
            setupNotifications();
        } else {
            Log.e(TAG, "Notification verification failed after " + MAX_VERIFICATION_ATTEMPTS + " attempts");
            

            if (connectionManager.getCurrentState() == BleConnectionManager.ConnectionState.CONNECTED) {
                Log.w(TAG, "Attempting to reconnect due to notification timeout");
                
                
                verificationAttempts = 0;
                
               
                connectionManager.reconnect();
            }
        }
    }
    
    /**
     * clean up resources
     */
    public void cleanup() {
        // Remove timeout checker
        mainHandler.removeCallbacks(timeoutCheckRunnable);
        
        // Remove listeners
        dataListeners.clear();
        
        // Clear notification queue
        notificationQueue.clear();
        
        // Clear timestamp tracking
        lastProcessedTimestamps.clear();
        
        // Clean up background handler
        if (backgroundHandler != null) {
            backgroundHandler.getLooper().quit();
        }
        
        Log.d(TAG, "BluetoothServiceIntegration cleanup completed");
    }
}