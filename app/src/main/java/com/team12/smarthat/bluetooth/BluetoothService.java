package com.team12.smarthat.bluetooth;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.team12.smarthat.models.SensorData;
import com.team12.smarthat.utils.Constants;
import com.team12.smarthat.viewmodels.BluetoothViewModel;
import com.team12.smarthat.utils.PermissionUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class BluetoothService {
    // ble connection
    private BluetoothGatt bluetoothGatt;
    // updates ui state,sensor data,errors
    private final BluetoothViewModel bluetoothViewModel;
    private final com.team12.smarthat.bluetooth.BluetoothManager bluetoothManager;
    
    // Connection timeout handler and retry mechanism
    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private static final long CONNECTION_TIMEOUT = 10000; // 10 seconds
    private int connectionRetries = 0;
    private static final int MAX_RETRIES = 3;
    private BluetoothDevice lastConnectedDevice = null;
    
    // Connection metrics tracking
    private long connectionStartTime = 0;
    
    // Scan callback for BLE device discovery
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            try {
                BluetoothDevice device = result.getDevice();
                String deviceName = null;
                
                if (ContextCompat.checkSelfPermission(bluetoothManager.getContext(), 
                        Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    deviceName = device.getName();
                } else {
                    Log.e(Constants.TAG_BLUETOOTH, "Bluetooth connect permission not granted for accessing device name");
                    bluetoothViewModel.handleError("Permission issue when scanning");
                    return;
                }
                
                // Check if device is our SmartHat - MODIFIED FOR DEMO
                // Accept a wider range of device names for demo purposes on Android 12
                if (deviceName != null && 
                    (deviceName.equals("SmartHat") || 
                     deviceName.contains("ESP32") || 
                     deviceName.contains("esp") || 
                     deviceName.contains("BLE") || 
                     deviceName.equals("BLE_DEVICE"))) {
                     
                    Log.d(Constants.TAG_BLUETOOTH, "Found potential SmartHat device: " + deviceName + " (MAC: " + device.getAddress() + ")");
                    
                    if (ContextCompat.checkSelfPermission(bluetoothManager.getContext(), 
                            Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                        bluetoothManager.stopScan();
                        connect(device);
                    } else {
                        Log.e(Constants.TAG_BLUETOOTH, "Bluetooth scan permission not granted for stopping scan");
                        bluetoothViewModel.handleError("Permission issue when connecting to device");
                    }
                } else if (deviceName != null) {
                    // Log other devices found for troubleshooting
                    Log.d(Constants.TAG_BLUETOOTH, "Found non-SmartHat device: " + deviceName + " (MAC: " + device.getAddress() + ")");
                }
            } catch (SecurityException e) {
                Log.e(Constants.TAG_BLUETOOTH, "Security exception in scan result: " + e.getMessage());
                bluetoothViewModel.handleError("Permission denied while processing scan result");
            } catch (Exception e) {
                Log.e(Constants.TAG_BLUETOOTH, "Error processing scan result: " + e.getMessage());
                bluetoothViewModel.handleError("Error processing scan result");
            }
        }
        
        @Override
        public void onScanFailed(int errorCode) {
            String errorMsg = "Scan failed: ";
            switch (errorCode) {
                case ScanCallback.SCAN_FAILED_ALREADY_STARTED:
                    errorMsg += "already started";
                    break;
                case ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                    errorMsg += "app registration failed";
                    break;
                case ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED:
                    errorMsg += "BLE not supported";
                    break;
                case ScanCallback.SCAN_FAILED_INTERNAL_ERROR:
                    errorMsg += "internal error";
                    break;
                default:
                    errorMsg += "unknown error";
            }
            bluetoothViewModel.handleError(errorMsg);
        }
    };
    
    // test mode fields
    private boolean testModeEnabled = false;
    private Handler testModeHandler;
    private final Runnable testDataRunnable = new Runnable() {
        @Override
        public void run() {
            // random sensor data
            generateMockSensorData();
            
            // schedule next data gen
            if (testModeEnabled) {
                // Random delay between 2-5 seconds (increased from 1-3 for slower generation)
                int randomDelay = 2000 + (int)(Math.random() * 3000);
                testModeHandler.postDelayed(this, randomDelay);
            }
        }
    };
    // endregion
    
    // Add reference to operation queue
    private BleOperationQueue operationQueue;
    
    public BluetoothService(BluetoothViewModel bluetoothViewModel, com.team12.smarthat.bluetooth.BluetoothManager bluetoothManager) {
        this.bluetoothViewModel = bluetoothViewModel;
        this.bluetoothManager = bluetoothManager;
        
        if (bluetoothViewModel == null) {
            Log.e(Constants.TAG_BLUETOOTH, "BluetoothViewModel is null in BluetoothService constructor!");
            return;
        }
        
        if (bluetoothManager == null) {
            Log.e(Constants.TAG_BLUETOOTH, "BluetoothManager is null in BluetoothService constructor!");
            bluetoothViewModel.handleError("Bluetooth initialization failed");
            return;
        }
        
        //callbacks
        bluetoothManager.setViewModel(bluetoothViewModel);
        bluetoothManager.setGattCallback(gattCallback);
        
        // Get the operation queue from BluetoothManager
        operationQueue = bluetoothManager.getOperationQueue();
        
        // initialize test mode handler
        testModeHandler = new Handler(Looper.getMainLooper());
        
        // hw specifications validations
        validateHardwareSpecs();
    }
    // endregion
    
    // region scanning and connection
  // track timing
    // private long connectionStartTime
    
    /**
     * Start scanning for the SmartHat ESP32 device
     */
    public void startScan() {
        // Record the start time
        connectionStartTime = System.currentTimeMillis();
        
        // If test mode is enabled, just simulate a connection
        if (testModeEnabled) {
            Log.i(Constants.TAG_BLUETOOTH, "Test mode active - simulating connection");
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                updateViewModelSafely(() -> bluetoothViewModel.updateConnectionState(Constants.STATE_CONNECTED));
                startMockDataGeneration();
            }, 1000);
            return;
        }
        
        // Update UI to connecting state
        updateViewModelSafely(() -> bluetoothViewModel.updateConnectionState(Constants.STATE_CONNECTING));
        
        // Get context for permission checks
        final Context context = bluetoothManager.getContext();
        
        // Queue scanning operation with permission handling
        operationQueue.queue(() -> {
            // Use the permission utils to safely execute the scan
            PermissionUtils.runWithBluetoothPermission(
                context,
                () -> {
                    Log.i(Constants.TAG_BLUETOOTH, "Starting scan for SmartHat devices");
                    
                    // Set up a timeout to stop scanning after SCAN_PERIOD
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        // Check if we're still scanning
                        if (bluetoothManager != null) {
                            // Use permission utils for the timeout handler too
                            PermissionUtils.runWithBluetoothPermission(
                                context,
                                () -> {
                                    Log.d(Constants.TAG_BLUETOOTH, "Scan timeout reached");
                                    bluetoothManager.stopScan();
                                    
                                    // If we're still in CONNECTING state, update to DISCONNECTED
                                    if (bluetoothViewModel.getConnectionState().getValue() == Constants.STATE_CONNECTING) {
                                        Log.e(Constants.TAG_BLUETOOTH, "No devices found during scan");
                                        bluetoothViewModel.updateConnectionState(Constants.STATE_DISCONNECTED);
                                        bluetoothViewModel.handleError("No SmartHat devices found");
                                    }
                                },
                                () -> {
                                    Log.e(Constants.TAG_BLUETOOTH, "Permission denied, cannot stop scan");
                                    operationQueue.operationComplete();
                                },
                                false, // Does not require BLUETOOTH_CONNECT 
                                true   // Requires BLUETOOTH_SCAN
                            );
                        }
                    }, Constants.SCAN_PERIOD);
                    
                    // Start the actual scan
                    bluetoothManager.scanForDevices(scanCallback);
                },
                () -> {
                    Log.e(Constants.TAG_BLUETOOTH, "Permission denied, cannot start scan");
                    bluetoothViewModel.handleError("Bluetooth permission denied");
                    operationQueue.operationComplete();
                    bluetoothViewModel.updateConnectionState(Constants.STATE_DISCONNECTED);
                },
                false, // Does not require BLUETOOTH_CONNECT
                true   // Requires BLUETOOTH_SCAN
            );
        });
    }
    

    /**
     * Connect to the specified Bluetooth device
     * @param device The device to connect to
     */
    public void connect(BluetoothDevice device) {
        if (device == null) {
            Log.e(Constants.TAG_BLUETOOTH, "Cannot connect to null device");
            bluetoothViewModel.handleError("Invalid device");
            return;
        }
        
        // Ensure test mode is disabled before attempting real connection
        if (testModeEnabled) {
            Log.i(Constants.TAG_BLUETOOTH, "Disabling test mode before connecting to real device");
            setTestMode(false);
            
            // Small delay to let test mode cleanup complete
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                performDeviceConnection(device);
            }, 300);
        } else {
            performDeviceConnection(device);
        }
    }
    
    /**
     * Actually perform the device connection after any test mode cleanup
     * @param device The device to connect to
     */
    private void performDeviceConnection(BluetoothDevice device) {
        // Store this for potential reconnection attempts
        lastConnectedDevice = device;
        
        // Update UI immediately to show we're connecting
        updateViewModelSafely(() -> bluetoothViewModel.updateConnectionState(Constants.STATE_CONNECTING));
        
        // Start tracking connection time
        connectionStartTime = System.currentTimeMillis();
        
        // Start the connection timeout
        startConnectionTimeout();
        
        // Use PermissionUtils to safely handle Bluetooth connection
        final Context context = bluetoothManager.getContext();
        
        PermissionUtils.runWithBluetoothPermission(
            context,
            () -> {
                Log.i(Constants.TAG_BLUETOOTH, "Connecting to " + device.getAddress());
                bluetoothManager.connectToBleDevice(device);
            },
            () -> {
                Log.e(Constants.TAG_BLUETOOTH, "Permission denied, cannot connect to device");
                bluetoothViewModel.handleError("Bluetooth permission denied");
                updateViewModelSafely(() -> bluetoothViewModel.updateConnectionState(Constants.STATE_DISCONNECTED));
            },
            true,  // Requires BLUETOOTH_CONNECT
            false  // Doesn't require BLUETOOTH_SCAN
        );
    }
    
    /**
     * Enable notifications for all sensor characteristics
     * @param gatt The connected BluetoothGatt
     * @param service The service containing sensor characteristics
     */
    private void enableSensorNotifications(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattService service) {
        if (gatt == null || service == null) {
            Log.e(Constants.TAG_BLUETOOTH, "Null gatt or service in enableSensorNotifications");
            bluetoothViewModel.handleError("Error enabling sensor notifications: invalid parameters");
            return;
        }

        Log.d(Constants.TAG_BLUETOOTH, "Setting up notifications for SmartHat sensors");
        
        // Use PermissionUtils to safely handle Bluetooth operations
        final Context context = bluetoothManager.getContext();
        
        PermissionUtils.runWithBluetoothPermission(
            context,
            () -> {
                // Get characteristics
                BluetoothGattCharacteristic dustChar = service.getCharacteristic(Constants.DUST_CHARACTERISTIC_UUID);
                BluetoothGattCharacteristic soundChar = service.getCharacteristic(Constants.SOUND_CHARACTERISTIC_UUID);
                
                boolean anyCharacteristicEnabled = false;
                
                // Enable notifications for dust sensor
                if (dustChar != null) {
                    Log.d(Constants.TAG_BLUETOOTH, "Found dust characteristic, enabling notifications");
                    if (enableCharacteristicNotification(gatt, dustChar)) {
                        anyCharacteristicEnabled = true;
                    }
                } else {
                    Log.w(Constants.TAG_BLUETOOTH, "Dust characteristic not found");
                }
                
                // Enable notifications for sound sensor
                if (soundChar != null) {
                    Log.d(Constants.TAG_BLUETOOTH, "Found sound characteristic, enabling notifications");
                    if (enableCharacteristicNotification(gatt, soundChar)) {
                        anyCharacteristicEnabled = true;
                    }
                } else {
                    Log.w(Constants.TAG_BLUETOOTH, "Sound characteristic not found");
                }
                
                // Warn user if no characteristics were enabled
                if (!anyCharacteristicEnabled) {
                    Log.e(Constants.TAG_BLUETOOTH, "Failed to enable notifications for any characteristics");
                    bluetoothViewModel.handleError("Failed to enable sensor notifications");
                }
            },
            () -> {
                Log.e(Constants.TAG_BLUETOOTH, "Bluetooth permission denied, cannot enable sensor notifications");
                bluetoothViewModel.handleError("Permission denied: Cannot enable sensor notifications");
            },
            true,  // Requires BLUETOOTH_CONNECT
            false  // Doesn't require BLUETOOTH_SCAN
        );
    }
    
    /**
     * Enable notification for a characteristic
     * @param gatt The BluetoothGatt connection
     * @param characteristic The characteristic to enable notifications for
     * @return true if successful, false otherwise
     */
    private boolean enableCharacteristicNotification(@NonNull BluetoothGatt gatt, 
                                               @NonNull BluetoothGattCharacteristic characteristic) {
        try {
            // Identify the characteristic type for better logging
            final String characteristicType;
            if (characteristic.getUuid().equals(Constants.DUST_CHARACTERISTIC_UUID)) {
                characteristicType = "Dust";
            } else if (characteristic.getUuid().equals(Constants.SOUND_CHARACTERISTIC_UUID)) {
                characteristicType = "Sound";
            } else {
                characteristicType = "Unknown";
            }
            
            Log.d(Constants.TAG_BLUETOOTH, "Setting up notifications for " + characteristicType + " characteristic: " + characteristic.getUuid());
            
            // Check if the characteristic has the notify property
            if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0) {
                Log.e(Constants.TAG_BLUETOOTH, characteristicType + " characteristic does not support notifications");
                return false;
            }
            
            // Get context for permission checks
            final Context context = bluetoothManager.getContext();
            
            // Use the permission utility to safely enable notifications
            final boolean[] success = { false }; // Need to use array for final variable in lambda
            
            PermissionUtils.runWithBluetoothPermission(
                context,
                () -> {
                    // This is the first critical step - enable notifications at the GATT level
                    boolean result = gatt.setCharacteristicNotification(characteristic, true);
                    if (!result) {
                        Log.e(Constants.TAG_BLUETOOTH, "Failed to set " + characteristicType + " characteristic notification");
                        return;
                    }
                    Log.d(Constants.TAG_BLUETOOTH, "Successfully enabled GATT notification for " + characteristicType);
                    success[0] = true;
                    
                    // The second part is to write to the Client Characteristic Configuration Descriptor
                    final BluetoothGattDescriptor descriptor = characteristic.getDescriptor(Constants.CLIENT_CONFIG_DESCRIPTOR_UUID);
                    
                    // If we couldn't find the exact descriptor, try to find any descriptor that might work
                    if (descriptor == null) {
                        Log.w(Constants.TAG_BLUETOOTH, "Standard client characteristic config descriptor not found for " + characteristicType);
                        Log.d(Constants.TAG_BLUETOOTH, "Looking for alternative descriptors...");
                        
                        // Get all descriptors for this characteristic
                        List<BluetoothGattDescriptor> descriptors = characteristic.getDescriptors();
                        if (descriptors.size() > 0) {
                            Log.d(Constants.TAG_BLUETOOTH, "Found " + descriptors.size() + " descriptors on " + characteristicType + " characteristic");
                            
                            // Log all descriptors
                            for (BluetoothGattDescriptor desc : descriptors) {
                                Log.d(Constants.TAG_BLUETOOTH, "  Descriptor: " + desc.getUuid());
                            }
                            
                            // Try to use the first descriptor available
                            final BluetoothGattDescriptor alternativeDescriptor = descriptors.get(0);
                            Log.d(Constants.TAG_BLUETOOTH, "Using first available descriptor: " + alternativeDescriptor.getUuid());
                            
                            // Use delayed descriptor write for better reliability on Android 12+
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                // Need another permission check here for the delayed operation
                                PermissionUtils.runWithBluetoothPermission(
                                    context,
                                    () -> {
                                        alternativeDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                        boolean writeSuccess = false;
                                        try {
                                            // This operation requires BLUETOOTH_CONNECT permission
                                            writeSuccess = gatt.writeDescriptor(alternativeDescriptor);
                                        } catch (SecurityException e) {
                                            Log.e(Constants.TAG_BLUETOOTH, "Security exception during descriptor write: " + e.getMessage());
                                        }
                                        
                                        if (!writeSuccess) {
                                            Log.e(Constants.TAG_BLUETOOTH, "Failed to write descriptor for " + characteristicType);
                                        } else {
                                            Log.d(Constants.TAG_BLUETOOTH, "Successfully queued descriptor write for " + characteristicType);
                                        }
                                    },
                                    () -> Log.e(Constants.TAG_BLUETOOTH, "Permission denied for delayed descriptor write"),
                                    true, false
                                );
                            }, 300); // 300ms delay for Android 12+ reliability
                        } else {
                            Log.w(Constants.TAG_BLUETOOTH, "No descriptors found on " + characteristicType + " characteristic");
                            Log.d(Constants.TAG_BLUETOOTH, "setCharacteristicNotification succeeded, so notifications might still work");
                        }
                    } else {
                        Log.d(Constants.TAG_BLUETOOTH, "Found standard client characteristic config descriptor");
                        
                        // Use delayed descriptor write for better reliability on Android 12+
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            // Need another permission check here for the delayed operation
                            PermissionUtils.runWithBluetoothPermission(
                                context,
                                () -> {
                                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                    boolean writeSuccess = false;
                                    try {
                                        // This operation requires BLUETOOTH_CONNECT permission
                                        writeSuccess = gatt.writeDescriptor(descriptor);
                                    } catch (SecurityException e) {
                                        Log.e(Constants.TAG_BLUETOOTH, "Security exception during descriptor write: " + e.getMessage());
                                    }
                                    
                                    if (!writeSuccess) {
                                        Log.e(Constants.TAG_BLUETOOTH, "Failed to write descriptor for " + characteristicType);
                                    } else {
                                        Log.d(Constants.TAG_BLUETOOTH, "Successfully queued descriptor write for " + characteristicType);
                                    }
                                },
                                () -> Log.e(Constants.TAG_BLUETOOTH, "Permission denied for delayed descriptor write"),
                                true, false
                            );
                        }, 300); // 300ms delay for Android 12+ reliability
                    }
                },
                () -> {
                    Log.e(Constants.TAG_BLUETOOTH, "Permission denied for enabling notifications");
                    success[0] = false;
                },
                true, // requires BLUETOOTH_CONNECT
                false // doesn't require BLUETOOTH_SCAN
            );
            
            return success[0];
        } catch (Exception e) {
            Log.e(Constants.TAG_BLUETOOTH, "Error enabling characteristic notification: " + e.getMessage());
            return false;
        }
    }
    
    /**
     *  support for multiple json formats 
     * @param rawData the json string to parse
     * @param fallbackSensorType the sensor type to use if not specified in json
     */
    private void processJsonData(String rawData, String fallbackSensorType) throws JSONException {
        // validate json
        if (!validateSensorJson(rawData)) {
            Log.e(Constants.TAG_BLUETOOTH, "Invalid JSON format: " + rawData);
            bluetoothViewModel.handleError("Invalid sensor data format received");
            return;
        }
        
        // parse json string
        JSONObject json = new JSONObject(rawData);
        //ATTENTION ?!:
        // hw team's format -> {"messageType": "DUST_SENSOR_DATA", "data": 25.4, "timeStamp": 123456789}
        if (json.has("messageType") && json.has("data")) {
            String messageType = json.getString("messageType");
            float value = (float) json.getDouble("data");
            
            //reading range validations
            if (!isValidSensorValue(messageType, value)) {
                Log.w(Constants.TAG_BLUETOOTH, "Ignoring out-of-range sensor value: " + value + " for type " + messageType);
                return;
            }
            
            // their message types -> our sensor types
            String sensorType;
            if (messageType.equals(Constants.MESSAGE_TYPE_DUST)) {
                sensorType = "dust";
            } else if (messageType.equals(Constants.MESSAGE_TYPE_SOUND)) {
                sensorType = "noise";
            } else {
                // case unknown type
                Log.w(Constants.TAG_BLUETOOTH, "Unknown messageType received: " + messageType);
                return;
            }
            
            SensorData data = new SensorData(sensorType, value);
            bluetoothViewModel.handleNewData(data);
            return;
        }
        //ATTENTION WILL finalize when hw code ready
        // support multiple json formats from the arduino
        //might remove/change when sensor repo is ready
        // format 1 -> {"sensor": "dust", "value": 25.4}
        if (json.has("sensor") && json.has("value")) {
            SensorData data = new SensorData(
                    json.getString("sensor"),
                    (float) json.getDouble("value")
            );
            bluetoothViewModel.handleNewData(data);
            return;
        }
        
        // format 2 -> {"dust": 25.4} or {"noise": 65.7}
        if (json.has("dust")) {
            SensorData data = new SensorData("dust", (float) json.getDouble("dust"));
            bluetoothViewModel.handleNewData(data);
            return;
        }
        
        if (json.has("noise")) {
            SensorData data = new SensorData("noise", (float) json.getDouble("noise"));
            bluetoothViewModel.handleNewData(data);
            return;
        }
        
        // format 3 -> {"type": "dust", "reading": 25.4}
        if (json.has("type") && json.has("reading")) {
            SensorData data = new SensorData(
                    json.getString("type"),
                    (float) json.getDouble("reading")
            );
            bluetoothViewModel.handleNewData(data);
            return;
        }
        
        // format 4 -> {"value": 25.4} - use fallback sensor type from characteristic uuid
        if (json.has("value")) {
            SensorData data = new SensorData(
                    fallbackSensorType,
                    (float) json.getDouble("value")
            );
            bluetoothViewModel.handleNewData(data);
            return;
        }
        
        //case the json format wasn't recognized
        throw new JSONException("unrecognized json format: " + rawData);
    }
    
    /**
     * Disconnect from the current device
     */
    public void disconnect() {
        Log.d(Constants.TAG_BLUETOOTH, "Disconnecting from device");
        
        // First update UI to avoid lag
        updateViewModelSafely(() -> bluetoothViewModel.updateConnectionState(Constants.STATE_DISCONNECTED));
        
        // Cancel any test mode
        if (testModeEnabled) {
            setTestMode(false);
        }
        
        // Then actually disconnect from GATT
        if (bluetoothGatt != null) {
            Context context = bluetoothManager.getContext();
            
            // Use our permission utility to safely run the disconnect operation
            PermissionUtils.runWithBluetoothPermission(
                context,
                () -> {
                    try {
                        Log.d(Constants.TAG_BLUETOOTH, "Disconnecting and closing GATT");
                        bluetoothGatt.disconnect();
                        bluetoothGatt.close();
                        bluetoothGatt = null;
                    } catch (SecurityException e) {
                        Log.e(Constants.TAG_BLUETOOTH, "Security exception during disconnect: " + e.getMessage());
                    } catch (Exception e) {
                        Log.e(Constants.TAG_BLUETOOTH, "Error during disconnect: " + e.getMessage());
                    }
                },
                () -> {
                    Log.e(Constants.TAG_BLUETOOTH, "Permission denied for disconnect - cleaning up anyway");
                    bluetoothGatt = null;
                },
                true, // requires BLUETOOTH_CONNECT
                false // doesn't require BLUETOOTH_SCAN
            );
        } else {
            Log.d(Constants.TAG_BLUETOOTH, "No active GATT connection to disconnect");
        }
        
        // Signal completion
        if (operationQueue != null) {
            operationQueue.operationComplete();
        }
    }
    // endregion
    
    // region test mode
    
    /**
     * Set test mode
     * @param enabled true to enable test mode, false to disable
     */
    public void setTestMode(boolean enabled) {
        this.testModeEnabled = enabled;
        
        // Update ViewModel
        updateViewModelSafely(() -> bluetoothViewModel.updateTestModeStatus(enabled));
        
        if (enabled) {
            Log.i(Constants.TAG_BLUETOOTH, "Test mode enabled");
            startMockDataGeneration();
        } else {
            Log.i(Constants.TAG_BLUETOOTH, "Test mode disabled");
            stopMockDataGeneration();
        }
    }
    
    /**
     * ble data log
     * @param characteristic characteristic coming from which data
     * @param value string received
     */
    private void logBleData(BluetoothGattCharacteristic characteristic, String value) {
        String characteristicName = "Unknown";
        if (characteristic.getUuid().equals(Constants.DUST_CHARACTERISTIC_UUID)) {
            characteristicName = "Dust";
        } else if (characteristic.getUuid().equals(Constants.SOUND_CHARACTERISTIC_UUID)) {
            characteristicName = "Noise";
        }
        
        StringBuilder logMessage = new StringBuilder();
        logMessage.append("BLE Data Received:\n");
        logMessage.append("  Characteristic: ").append(characteristicName).append("\n");
        logMessage.append("  UUID: ").append(characteristic.getUuid()).append("\n");
        logMessage.append("  Value: ").append(value).append("\n");
        
        if (value.trim().startsWith("{")) {
            logMessage.append("  Format: JSON\n");
        } else {
            try {
                Float.parseFloat(value.trim());
                logMessage.append("  Format: Numeric\n");
            } catch (NumberFormatException e) {
                logMessage.append("  Format: Text (not numeric)\n");
            }
        }
        
        Log.d(Constants.TAG_BLUETOOTH, logMessage.toString());
    }
    
    /**
     * @return true case test mode
     */
    public boolean isTestModeEnabled() {
        return testModeEnabled;
    }
    
    /**
     * Start generating mock sensor data for test mode
     */
    public void startMockDataGeneration() {
        if (testModeHandler == null) {
            testModeHandler = new Handler(Looper.getMainLooper());
        }
        
        // Cancel any previous callbacks to avoid duplicates
        stopMockDataGeneration();
        
        Log.i(Constants.TAG_BLUETOOTH, "Starting mock data generation");
        testModeHandler.postDelayed(testDataRunnable, 1000);
        
        // Also update connection state if not already connected
        if (bluetoothViewModel.getConnectionState().getValue() != Constants.STATE_CONNECTED) {
            updateViewModelSafely(() -> bluetoothViewModel.updateConnectionState(Constants.STATE_CONNECTED));
        }
    }
    
    /**
     * Stop generating mock sensor data
     */
    public void stopMockDataGeneration() {
        if (testModeHandler != null) {
            Log.i(Constants.TAG_BLUETOOTH, "Stopping mock data generation");
            testModeHandler.removeCallbacks(testDataRunnable);
        }
    }
    
    /**
     * Generate mock sensor data for test mode
     */
    private void generateMockSensorData() {
        if (!testModeEnabled) {
            Log.e(Constants.TAG_BLUETOOTH, "Test mode not enabled, cannot generate mock data");
            return;
        }
        
        Log.d(Constants.TAG_BLUETOOTH, "Generating mock sensor data in test mode");
        
        try {
            // Use separate random generators for dust and noise
            // This ensures we test both sensors even if one is high
            
            // Dust sensor data (25% chance of high reading)
            boolean highDustReading = Math.random() < 0.25;
            float dustValue;
            
            if (highDustReading) {
                // Generate high dust reading (above threshold)
                dustValue = Constants.DUST_THRESHOLD + (float)(Math.random() * 70);
                Log.d(Constants.TAG_BLUETOOTH, "HIGH DUST generated: " + dustValue + " μg/m³");
            } else {
                // Generate normal dust reading
                dustValue = 5f + (float)(Math.random() * 40);
                Log.d(Constants.TAG_BLUETOOTH, "Normal dust generated: " + dustValue + " μg/m³");
            }
            
            // Create dust sensor data object
            final SensorData dustData = new SensorData("dust", dustValue);
            dustData.setSource(SensorData.SOURCE_TEST); // Mark as test data
            
            // Noise sensor data (25% chance of high reading)
            boolean highNoiseReading = Math.random() < 0.25;
            float noiseValue;
            
            if (highNoiseReading) {
                // Generate high noise reading (above threshold)
                noiseValue = Constants.NOISE_THRESHOLD + (float)(Math.random() * 35);
                Log.d(Constants.TAG_BLUETOOTH, "HIGH NOISE generated: " + noiseValue + " dB");
            } else {
                // Generate normal noise reading
                noiseValue = 40f + (float)(Math.random() * 40);
                Log.d(Constants.TAG_BLUETOOTH, "Normal noise generated: " + noiseValue + " dB");
            }
            
            // Create noise sensor data object
            final SensorData noiseData = new SensorData("noise", noiseValue);
            noiseData.setSource(SensorData.SOURCE_TEST); // Mark as test data
            
            // Send data on main thread
            Handler mainHandler = new Handler(Looper.getMainLooper());
            
            // Slightly delay the second reading to avoid race conditions
            mainHandler.post(() -> {
                try {
                    bluetoothViewModel.handleNewData(dustData);
                    Log.d(Constants.TAG_BLUETOOTH, "Dust data sent to ViewModel: " + dustValue);
                } catch (Exception e) {
                    Log.e(Constants.TAG_BLUETOOTH, "Error sending dust data: " + e.getMessage(), e);
                }
            });
            
            mainHandler.postDelayed(() -> {
                try {
                    bluetoothViewModel.handleNewData(noiseData);
                    Log.d(Constants.TAG_BLUETOOTH, "Noise data sent to ViewModel: " + noiseValue);
                } catch (Exception e) {
                    Log.e(Constants.TAG_BLUETOOTH, "Error sending noise data: " + e.getMessage(), e);
                }
            }, 500); // 500ms delay between dust and noise data
            
        } catch (Exception e) {
            Log.e(Constants.TAG_BLUETOOTH, "Error generating mock data: " + e.getMessage(), e);
        }
    }
    
    /**
     * Force generate test data with high values to test notifications
     */
    public void forceGenerateTestData() {
        if (!testModeEnabled) {
            Log.d(Constants.TAG_BLUETOOTH, "Test mode not enabled, ignoring forceGenerateTestData()");
            return;
        }
        
        Log.d(Constants.TAG_BLUETOOTH, "Generating HIGH test values to trigger notifications");
        
        // Generate high dust reading
        float highDustValue = Constants.DUST_THRESHOLD + 20.0f + (float) (Math.random() * 30.0f);
        SensorData dustData = new SensorData("dust", highDustValue);
        dustData.setSource(SensorData.SOURCE_TEST); // Mark as test data
        
        Log.d(Constants.TAG_BLUETOOTH, "Generated HIGH DUST test data: " + highDustValue);
        bluetoothViewModel.handleNewData(dustData);
        
        // Generate high noise reading with a slight delay
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            float highNoiseValue = Constants.NOISE_THRESHOLD + 10.0f + (float) (Math.random() * 20.0f);
            SensorData noiseData = new SensorData("noise", highNoiseValue);
            noiseData.setSource(SensorData.SOURCE_TEST); // Mark as test data
            
            Log.d(Constants.TAG_BLUETOOTH, "Generated HIGH NOISE test data: " + highNoiseValue);
            bluetoothViewModel.handleNewData(noiseData);
        }, 500); // 500ms delay
    }
    
    /**
     * bursttest many readings/sec
     * @param durationSeconds how long run??
     * @param readingsPerSecond #readings /sec?
     */
    public void runBurstTest(int durationSeconds, int readingsPerSecond) {
        if (!testModeEnabled) {
            Log.d(Constants.TAG_BLUETOOTH, "Test mode not enabled, ignoring runBurstTest()");
            return;
        }
        
        // Apply safety limits to prevent unrealistic load
        if (readingsPerSecond > 50) {
            Log.w(Constants.TAG_BLUETOOTH, "Readings/sec at 50 for safety");
            readingsPerSecond = 50;
        }
        
        if (durationSeconds > 60) {
            Log.w(Constants.TAG_BLUETOOTH, "Duration at 60 seconds for safety");
            durationSeconds = 60;
        }
        
        // copies for inner class based on logcat errors
        final int finalDurationSeconds = durationSeconds;
        final int finalReadingsPerSecond = readingsPerSecond;
        
        Log.d(Constants.TAG_BLUETOOTH, "Starting burst test: " + finalReadingsPerSecond + 
              " readings/sec for " + finalDurationSeconds + " seconds");
        
        final long interval = 1000 / finalReadingsPerSecond;
        final long endTime = System.currentTimeMillis() + (finalDurationSeconds * 1000);
        final Handler handler = new Handler(Looper.getMainLooper());
        
        // runnable repeatition
        final Runnable burstRunnable = new Runnable() {
            @Override
            public void run() {
                //random mock
                generateMockSensorData();
                
                // before end time
                if (System.currentTimeMillis() < endTime) {
                    handler.postDelayed(this, interval);
                } else {
                    Log.d(Constants.TAG_BLUETOOTH, "Burst test completed");
                    //notify user
                    bluetoothViewModel.handleError("Burst test completed: " + 
                                        (finalDurationSeconds * finalReadingsPerSecond) + " readings generated");
                }
            }
        };
        
        // start burst test
        handler.post(burstRunnable);
    }
    
    /**
     *sim crc error
     * testing error handling
     */
    public void simulateCrcError() {
        if (!testModeEnabled) {
            Log.d(Constants.TAG_BLUETOOTH, "Test mode not enabled, ignoring simulateCrcError()");
            return;
        }
        
        // sim invalid json
        simulateCharacteristicData(Constants.DUST_CHARACTERISTIC_UUID, "{\"messageType\":\"DUST_SENSOR_DATA\",\"data\":\"not_a_number\"}");
        
        // sim missing fields
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            simulateCharacteristicData(Constants.SOUND_CHARACTERISTIC_UUID, "{\"messageType\":\"SOUND_SENSOR_DATA\"}");
        }, 500);
    }
    
    /**
     * test sim receiving hw data in different formats
     * CALL FROM MAINACTIVITY for no hw test
     */
    public void testDataFormats() {
        if (!testModeEnabled) {
            Log.e(Constants.TAG_BLUETOOTH, "Cannot test formats when test mode is disabled");
            return;
        }
        
        Log.d(Constants.TAG_BLUETOOTH, "Testing different data formats...");
        
        try {
            // hw team format test
            simulateCharacteristicData(
                Constants.DUST_CHARACTERISTIC_UUID,
                "{\"messageType\":\"" + Constants.MESSAGE_TYPE_DUST + "\",\"data\":42.5,\"timeStamp\":123456789}"
            );
            
            simulateCharacteristicData(
                Constants.SOUND_CHARACTERISTIC_UUID,
                "{\"messageType\":\"" + Constants.MESSAGE_TYPE_SOUND + "\",\"data\":75.8,\"timeStamp\":123456790}"
            );
            
            // test plain number
            simulateCharacteristicData(
                Constants.DUST_CHARACTERISTIC_UUID,
                "45.2"
            );
            
            simulateCharacteristicData(
                Constants.SOUND_CHARACTERISTIC_UUID,
                "78.5"
            );
            
            // other json formats sim
            simulateCharacteristicData(
                Constants.DUST_CHARACTERISTIC_UUID,
                "{\"sensor\":\"dust\",\"value\":48.9}"
            );
            
            simulateCharacteristicData(
                Constants.SOUND_CHARACTERISTIC_UUID,
                "{\"noise\":80.1}"
            );
            
            // high reading sim
            simulateCharacteristicData(
                Constants.DUST_CHARACTERISTIC_UUID,
                String.valueOf(Constants.DUST_THRESHOLD + 10.0f)
            );
            
            simulateCharacteristicData(
                Constants.SOUND_CHARACTERISTIC_UUID,
                String.valueOf(Constants.NOISE_THRESHOLD + 5.0f)
            );
            
            Log.d(Constants.TAG_BLUETOOTH, "All test formats processed");
        } catch (Exception e) {
            Log.e(Constants.TAG_BLUETOOTH, "Error testing data formats: " + e.getMessage());
        }
    }
    
    /**
     * sim data coming from a characteristic
     */
    //UUID match when hw ready
    private void simulateCharacteristicData(UUID characteristicUuid, String value) {
        try {
            Log.d(Constants.TAG_BLUETOOTH, "Simulating data for " + 
                (characteristicUuid.equals(Constants.DUST_CHARACTERISTIC_UUID) ? "DUST" : "NOISE") +
                " characteristic: " + value);
            
            // mock
            BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(
                characteristicUuid, 
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ
            );
            
            // received from ble
            characteristic.setValue(value.getBytes(StandardCharsets.UTF_8));
            
            // sensor type from uuid
            String sensorType;
            if (characteristicUuid.equals(Constants.DUST_CHARACTERISTIC_UUID)) {
                sensorType = "dust";
            } else if (characteristicUuid.equals(Constants.SOUND_CHARACTERISTIC_UUID)) {
                sensorType = "noise";
            } else {
                Log.e(Constants.TAG_BLUETOOTH, "Unknown characteristic UUID: " + characteristicUuid);
                return;
            }
            
            // lod sim data
            logBleData(characteristic, value);

            if (value.trim().startsWith("{")) {
                processJsonData(value, sensorType);
            } else {
                try {
                    float floatValue = Float.parseFloat(value.trim());
                    SensorData sensorData = new SensorData(sensorType, floatValue);
                    bluetoothViewModel.handleNewData(sensorData);
                } catch (NumberFormatException e) {
                    Log.e(Constants.TAG_BLUETOOTH, "Invalid number format in test: " + value);
                }
            }
            
            // delay for sim INTENTIONAL
            Thread.sleep(500);
        } catch (Exception e) {
            Log.e(Constants.TAG_BLUETOOTH, "Error simulating characteristic data: " + e.getMessage());
        }
    }
    // endregion

    /**
     * Start a timeout for connection attempts
     */
    private void startConnectionTimeout() {
        // Cancel any existing timeout
        timeoutHandler.removeCallbacksAndMessages(null);
        
        // Set a new timeout
        timeoutHandler.postDelayed(() -> {
            Log.e(Constants.TAG_BLUETOOTH, "Connection timeout after " + Constants.CONNECTION_TIMEOUT + "ms");
            
            // Only handle timeout if we're still in connecting state
            if (bluetoothViewModel.getConnectionState().getValue() == Constants.STATE_CONNECTING) {
                // Update UI
                updateViewModelSafely(() -> {
                    bluetoothViewModel.handleError("Connection timeout. Please try again.");
                    bluetoothViewModel.updateConnectionState(Constants.STATE_DISCONNECTED);
                });
                
                // Clean up any partial connection
                if (bluetoothGatt != null) {
                    try {
                        bluetoothGatt.disconnect();
                        bluetoothGatt.close();
                    } catch (SecurityException e) {
                        Log.e(Constants.TAG_BLUETOOTH, "Security exception during disconnect: " + e.getMessage());
                    } catch (Exception e) {
                        Log.e(Constants.TAG_BLUETOOTH, "Error closing GATT during timeout: " + e.getMessage());
                    }
                    bluetoothGatt = null;
                }
            }
        }, Constants.CONNECTION_TIMEOUT);
    }
    
    /**
     * Handle connection errors with retry logic
     */
    private void handleConnectionError(String error) {
        if (connectionRetries < MAX_RETRIES && lastConnectedDevice != null) {
            connectionRetries++;
            // Update: exponential backoff 1s, 2s, 4s
            long delay = (long) Math.pow(2, connectionRetries - 1) * 1000;
            
            Log.d(Constants.TAG_BLUETOOTH, "Attempting reconnection, retry " + 
                  connectionRetries + " of " + MAX_RETRIES + 
                  " in " + delay + "ms");
            
            // Reconnect after delay
            timeoutHandler.postDelayed(() -> {
                if (lastConnectedDevice != null) {
                    Log.d(Constants.TAG_BLUETOOTH, "Reconnecting to device: " + 
                          lastConnectedDevice.getAddress());
                    connect(lastConnectedDevice);
                }
            }, delay);
        } else {
            // Reset counter when retries are exhausted
            connectionRetries = 0;
            
            // Notify user that connection failed
            bluetoothViewModel.handleError("Connection failed after " + MAX_RETRIES + 
                                 " attempts: " + error);
            
            // Update UI to disconnected state
            bluetoothViewModel.updateConnectionState(Constants.STATE_DISCONNECTED);
        }
    }

    /**
     * json format validation
     * @param rawData JSON string to validate
     * @return true if valid else false
     */
    private boolean validateSensorJson(String rawData) {
        if (rawData == null || rawData.trim().isEmpty()) {
            return false;
        }
        
        try {
            JSONObject json = new JSONObject(rawData);
            
            // test mode allow all allowed formats
            if (testModeEnabled) {
                return validateAnyFormat(json);
            } else {
                //not test mode HW team format only
                return validateHardwareTeamFormat(json);
            }
        } catch (JSONException e) {
            Log.e(Constants.TAG_BLUETOOTH, "JSON validation error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * only hw json validation for not test mode
     * @param json the json object to validate
     * @return true if valid
     */
    private boolean validateHardwareTeamFormat(JSONObject json) {
        try {
            if (!json.has("messageType") || !json.has("data")) {
                return false;
            }
            
            String messageType = json.getString("messageType");
            return messageType.equals(Constants.MESSAGE_TYPE_DUST) || 
                   messageType.equals(Constants.MESSAGE_TYPE_SOUND);
        } catch (JSONException e) {
            return false;
        }
    }
    
    /**
     * test mode json format validation
     * @param json json object
     * @return true if valid
     */
    private boolean validateAnyFormat(JSONObject json) {
        // format 1: hw team format (messageType + data)
        //UPDATE WHEN HW CODE READY!
        if (json.has("messageType") && json.has("data")) {
            return true;
        }
        
        // format 2: sensor + value
        if (json.has("sensor") && json.has("value")) {
            return true;
        }
        
        // format 3: direct properties
        if (json.has("dust") || json.has("noise")) {
            return true;
        }
        
        // format4: type + reading
        if (json.has("type") && json.has("reading")) {
            return true;
        }
        
        // format5: value only (with characteristic UUID context)
        return json.has("value");
    }
    
    /**
     * sensor values range validation
     * @param sensorType dust/noise
     * @param value reading value
     * @return true if valid
     */
    private boolean isValidSensorValue(String sensorType, float value) {

        if (sensorType.equals(Constants.MESSAGE_TYPE_DUST)) {
            return value >= 0 && value <= 1000;
        }

        else if (sensorType.equals(Constants.MESSAGE_TYPE_SOUND)) {
            return value >= 0 && value <= 140;
        }
        
        return false; // unknown type
    }

    /**
     *uuid validation
     * WILL REMOVE IF HW CODE READY AND DIRECT
     */
    private void validateHardwareSpecs() {
        // service uuid
        if (!Constants.SERVICE_UUID.toString().equals("12345678-1234-5678-1234-56789abcdef0")) {
            Log.e(Constants.TAG_BLUETOOTH, "ERROR: SERVICE_UUID mismatch with hardware specs!");
            throw new RuntimeException("SERVICE_UUID mismatch with hardware specs!");
        }
        
        // characteristic uuid
        if (!Constants.DUST_CHARACTERISTIC_UUID.toString().equals("dcba4321-8765-4321-8765-654321fedcba")) {
            Log.e(Constants.TAG_BLUETOOTH, "ERROR: DUST_CHARACTERISTIC_UUID mismatch with hardware specs!");
            throw new RuntimeException("DUST_CHARACTERISTIC_UUID mismatch with hardware specs!");
        }
        
        if (!Constants.SOUND_CHARACTERISTIC_UUID.toString().equals("abcd1234-5678-1234-5678-abcdef123456")) {
            Log.e(Constants.TAG_BLUETOOTH, "ERROR: SOUND_CHARACTERISTIC_UUID mismatch with hardware specs!");
            throw new RuntimeException("SOUND_CHARACTERISTIC_UUID mismatch with hardware specs!");
        }
        
        Log.d(Constants.TAG_BLUETOOTH, "Hardware specs validation successful");
    }

    /**
     * log
     */
    private void logConnectionMetrics() {
        long duration = System.currentTimeMillis() - connectionStartTime;
        Log.d(Constants.TAG_BLUETOOTH, "Connection established in " + duration + "ms");
        
        // will add more

    }

    /**
     * log
     * @param gatt bt conection
     */
    private void logEnhancedConnectionMetrics(BluetoothGatt gatt) {
        long duration = System.currentTimeMillis() - connectionStartTime;
        
        // stringbuilder
        StringBuilder metrics = new StringBuilder();
        metrics.append("Connection Metrics:\n");
        metrics.append("- Duration: ").append(duration).append("ms\n");
        metrics.append("- Retry Attempts: ").append(connectionRetries).append("\n");
        
        // device info
        String deviceName = "Unknown";
        String deviceAddress = "Unknown";
        String bondState = "Unknown";
        
        // get device info
        if (gatt != null && gatt.getDevice() != null) {
            BluetoothDevice device = gatt.getDevice();
            try {
                // permission check
                if (ContextCompat.checkSelfPermission(bluetoothManager.getContext(), 
                        Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    deviceName = device.getName() != null ? device.getName() : "Unknown";
                    deviceAddress = device.getAddress();
                    bondState = bondStateToString(device.getBondState());
                    
                    metrics.append("- Device Name: ").append(deviceName).append("\n");
                    metrics.append("- Device Address: ").append(deviceAddress).append("\n");
                    metrics.append("- Bond State: ").append(bondState).append("\n");
                }
            } catch (SecurityException e) {
                metrics.append("- Device Info: [Permission Denied]\n");
            }
        }
        
        // log
        Log.d(Constants.TAG_BLUETOOTH, metrics.toString());
        
        // store in db
        if (bluetoothViewModel != null && bluetoothViewModel.getDatabaseHelper() != null) {
            try {

                JSONObject metricsJson = new JSONObject();
                metricsJson.put("duration_ms", duration);
                metricsJson.put("retry_attempts", connectionRetries);
                metricsJson.put("device_name", deviceName);
                metricsJson.put("device_address", deviceAddress);
                metricsJson.put("bond_state", bondState);
                metricsJson.put("timestamp", System.currentTimeMillis());

                SensorData metricsData = new SensorData("connection_metrics", 
                    duration, metricsJson.toString());
                
                bluetoothViewModel.handleNewData(metricsData);
            } catch (Exception e) {
                Log.e(Constants.TAG_BLUETOOTH, "Error storing connection metrics: " + e.getMessage());
            }
        }
    }
    
    /**
     * bonded integer to string
     */
    private String bondStateToString(int bondState) {
        switch (bondState) {
            case BluetoothDevice.BOND_NONE:
                return "Not Bonded";
            case BluetoothDevice.BOND_BONDING:
                return "Bonding";
            case BluetoothDevice.BOND_BONDED:
                return "Bonded";
            default:
                return "Unknown";
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String deviceAddress = gatt.getDevice().getAddress();
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(Constants.TAG_BLUETOOTH, "Successfully connected to " + deviceAddress);
                    bluetoothGatt = gatt;
                    lastConnectedDevice = gatt.getDevice();
                    connectionRetries = 0; // Reset connection retries
                    
                    // Log connection time
                    long connectionTime = System.currentTimeMillis() - connectionStartTime;
                    Log.d(Constants.TAG_BLUETOOTH, "Connection established in " + connectionTime + "ms");
                    
                    timeoutHandler.removeCallbacksAndMessages(null); // Remove any pending timeouts
                    
                    updateViewModelSafely(() -> bluetoothViewModel.updateConnectionState(Constants.STATE_CONNECTED));
                    
                    // Discover services after connection (on main thread to prevent deadlocks)
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (bluetoothGatt != null) {
                            Log.d(Constants.TAG_BLUETOOTH, "Starting service discovery");
                            boolean discovering = bluetoothGatt.discoverServices();
                            if (!discovering) {
                                Log.e(Constants.TAG_BLUETOOTH, "Failed to start service discovery");
                                disconnect();
                            }
                        }
                    });
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i(Constants.TAG_BLUETOOTH, "Successfully disconnected from " + deviceAddress);
                    updateViewModelSafely(() -> bluetoothViewModel.updateConnectionState(Constants.STATE_DISCONNECTED));
                    
                    // Clean up resources
                    if (bluetoothGatt != null) {
                        bluetoothGatt.close();
                        bluetoothGatt = null;
                    }
                }
            } else {
                // Connection error
                Log.e(Constants.TAG_BLUETOOTH, "Connection error: status=" + status + " for " + deviceAddress);
                
                // Known error codes with better descriptions
                String errorMessage;
                switch (status) {
                    case 8: // Connection timeout
                        errorMessage = "Connection timeout. Device might be out of range.";
                        break;
                    case 22: // Connection parameter rejected
                        errorMessage = "Connection parameter rejected by the device.";
                        break;
                    case 133: // GATT_ERROR
                        errorMessage = "GATT error (133). This is often temporary - retrying...";
                        break;
                    case 257: // Unsupported transport
                        errorMessage = "Connection failed: Unsupported transport.";
                        break;
                    default:
                        errorMessage = "Connection error: " + status;
                }
                
                Log.e(Constants.TAG_BLUETOOTH, errorMessage);
                
                // Retry for certain errors
                if (status == 133 && connectionRetries < MAX_RETRIES) {
                    connectionRetries++;
                    Log.i(Constants.TAG_BLUETOOTH, "Retrying connection (attempt " + connectionRetries + ")");
                    
                    // Close and retry after a delay
                    if (gatt != null) {
                        gatt.close();
                    }
                    
                    // Wait a moment before retrying
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (lastConnectedDevice != null) {
                            Log.d(Constants.TAG_BLUETOOTH, "Reconnecting to " + lastConnectedDevice.getAddress());
                            bluetoothManager.connectToBleDevice(lastConnectedDevice);
                        }
                    }, Constants.RETRY_DELAY);
                    
                    return;
                }
                
                // Update UI with error
                updateViewModelSafely(() -> {
                    bluetoothViewModel.handleError(errorMessage);
                    bluetoothViewModel.updateConnectionState(Constants.STATE_DISCONNECTED);
                });
                
                // Clean up
                if (gatt != null) {
                    gatt.close();
                }
                bluetoothGatt = null;
            }
            
            // Signal completion to operation queue
            if (operationQueue != null) {
                operationQueue.operationComplete();
            }
        }
        
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(Constants.TAG_BLUETOOTH, "Services discovered successfully");
                
                // Use PermissionUtils to safely handle service discovery operations
                final Context context = bluetoothManager.getContext();
                
                PermissionUtils.runWithBluetoothPermission(
                    context,
                    () -> {
                        // Get the list of services
                        List<BluetoothGattService> services = gatt.getServices();
                        Log.d(Constants.TAG_BLUETOOTH, "Found " + services.size() + " services");
                        
                        // Look for our target service
                        BluetoothGattService targetService = gatt.getService(Constants.SERVICE_UUID);
                        
                        // If service discovery debug is enabled and we didn't find our target service
                        if (Constants.ENABLE_SERVICE_DISCOVERY_DEBUG && targetService == null) {
                            Log.d(Constants.TAG_BLUETOOTH, "Target service " + Constants.SERVICE_UUID + " not found");
                            Log.d(Constants.TAG_BLUETOOTH, "Logging all available services and characteristics:");
                            
                            // Highest-scoring service will be chosen if needed
                            BluetoothGattService bestMatchService = null;
                            int highestScore = 0;
                            
                            // Look at all available services
                            for (BluetoothGattService service : services) {
                                UUID serviceUuid = service.getUuid();
                                Log.d(Constants.TAG_BLUETOOTH, "Service: " + serviceUuid);
                                
                                // Score this service based on characteristics
                                int score = 0;
                                
                                // Check each characteristic in this service
                                List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                                for (BluetoothGattCharacteristic characteristic : characteristics) {
                                    UUID charUuid = characteristic.getUuid();
                                    int properties = characteristic.getProperties();
                                    
                                    Log.d(Constants.TAG_BLUETOOTH, "  Characteristic: " + charUuid);
                                    Log.d(Constants.TAG_BLUETOOTH, "    Properties: 0x" + 
                                            Integer.toHexString(properties));
                                    
                                    // Give points for notify property
                                    if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                                        Log.d(Constants.TAG_BLUETOOTH, "    Has NOTIFY property!");
                                        score += 1;
                                    }
                                }
                                
                                Log.d(Constants.TAG_BLUETOOTH, "  Service score: " + score);
                                
                                // Update best match if this service scores higher
                                if (score > highestScore) {
                                    highestScore = score;
                                    bestMatchService = service;
                                }
                            }
                            
                            // If we found a reasonably good service, use it
                            if (bestMatchService != null && highestScore > 0) {
                                Log.i(Constants.TAG_BLUETOOTH, "Using best matched service: " + 
                                        bestMatchService.getUuid() + " (score: " + highestScore + ")");
                                targetService = bestMatchService;
                            }
                        }
                        
                        // Did we find our target service?
                        if (targetService != null) {
                            Log.i(Constants.TAG_BLUETOOTH, "Found SmartHat sensor service");
                            enableSensorNotifications(gatt, targetService);
                            // Connection state will be updated once notifications are set up
                        } else {
                            Log.e(Constants.TAG_BLUETOOTH, "SmartHat sensor service not found");
                            updateViewModelSafely(() -> {
                                bluetoothViewModel.handleError("SmartHat service not found on device");
                                bluetoothViewModel.updateConnectionState(Constants.STATE_DISCONNECTED);
                            });
                        }
                        
                        // Log connection metrics
                        logEnhancedConnectionMetrics(gatt);
                        
                        // Signal operation complete
                        if (operationQueue != null) {
                            operationQueue.operationComplete();
                        }
                    },
                    () -> {
                        Log.e(Constants.TAG_BLUETOOTH, "Bluetooth permission denied, cannot discover services");
                        updateViewModelSafely(() -> {
                            bluetoothViewModel.handleError("Permission denied: Cannot discover services");
                            bluetoothViewModel.updateConnectionState(Constants.STATE_DISCONNECTED);
                        });
                        
                        // Signal operation complete even on error
                        if (operationQueue != null) {
                            operationQueue.operationComplete();
                        }
                    },
                    true,  // Requires BLUETOOTH_CONNECT
                    false  // Doesn't require BLUETOOTH_SCAN
                );
            } else {
                // Handle errors in service discovery
                Log.e(Constants.TAG_BLUETOOTH, "Service discovery failed with status: " + status);
                updateViewModelSafely(() -> {
                    bluetoothViewModel.handleError("Error discovering services: " + status);
                    bluetoothViewModel.updateConnectionState(Constants.STATE_DISCONNECTED);
                });
                
                // Signal operation complete
                if (operationQueue != null) {
                    operationQueue.operationComplete();
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.d(Constants.TAG_BLUETOOTH, "Characteristic changed: " + characteristic.getUuid());
            processCharacteristicData(characteristic, characteristic.getValue());
            
            // Signal completion to operation queue
            if (operationQueue != null) {
                operationQueue.operationComplete();
            }
        }
        
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(Constants.TAG_BLUETOOTH, "Characteristic read successfully: " + characteristic.getUuid());
                processCharacteristicData(characteristic, characteristic.getValue());
            } else {
                Log.e(Constants.TAG_BLUETOOTH, "Characteristic read failed: " + status);
            }
            
            // Signal completion to operation queue
            if (operationQueue != null) {
                operationQueue.operationComplete();
            }
        }
    };

    /**
     * Processes data received from a characteristic
     * @param characteristic The characteristic from which data was received
     * @param value The raw value as a byte array
     */
    private void processCharacteristicData(@NonNull BluetoothGattCharacteristic characteristic, 
                                            @NonNull byte[] value) {
        // Check which characteristic this is
        UUID characteristicUuid = characteristic.getUuid();
        String sensorType = null;
        
        // Set default sensor type based on characteristic UUID
        if (characteristicUuid.equals(Constants.DUST_CHARACTERISTIC_UUID)) {
            sensorType = "dust";
        } else if (characteristicUuid.equals(Constants.SOUND_CHARACTERISTIC_UUID)) {
            sensorType = "noise";
        } else {
            Log.e(Constants.TAG_BLUETOOTH, "Unknown characteristic UUID: " + characteristicUuid);
            return;
        }
        
        try {
            if (value == null || value.length == 0) {
                Log.e(Constants.TAG_BLUETOOTH, "Received null or empty value for " + sensorType + " sensor");
                return;
            }
            
            // Convert bytes to string
            String stringValue = new String(value, StandardCharsets.UTF_8).trim();
            Log.d(Constants.TAG_BLUETOOTH, "Received " + sensorType + " value: " + stringValue);
            
            // Try as JSON first (this is the expected format from ESP32)
            try {
                // Validate and parse JSON
                if (!validateSensorJson(stringValue)) {
                    Log.e(Constants.TAG_BLUETOOTH, "JSON validation failed: " + stringValue);
                    return;
                }
                
                JSONObject json = new JSONObject(stringValue);
                
                // Primary format - messageType/data fields
                if (json.has("messageType") && json.has("data")) {
                    String messageType = json.getString("messageType");
                    float sensorValue = (float) json.getDouble("data");
                    
                    // Verify messageType matches the characteristic
                    boolean validMessageType = false;
                    if (messageType.equals(Constants.MESSAGE_TYPE_DUST) && sensorType.equals("dust")) {
                        validMessageType = true;
                    } else if (messageType.equals(Constants.MESSAGE_TYPE_SOUND) && sensorType.equals("noise")) {
                        validMessageType = true;
                    }
                    
                    if (!validMessageType) {
                        Log.e(Constants.TAG_BLUETOOTH, "MessageType mismatch: " + messageType + " for " + sensorType + " sensor");
                        return;
                    }
                    
                    // Validate sensor value range
                    if (!isValidSensorValue(sensorType, sensorValue)) {
                        Log.e(Constants.TAG_BLUETOOTH, "Value out of range: " + sensorValue + " for " + sensorType);
                        return;
                    }
                    
                    // Create and pass sensor data object to the view model
                    SensorData data = new SensorData(sensorType, sensorValue);
                    bluetoothViewModel.handleNewData(data);
                    
                    Log.d(Constants.TAG_BLUETOOTH, "Successfully processed " + sensorType + " data: " + sensorValue);
                    return;
                }
                
                // Alternate format - direct value field
                if (json.has("value")) {
                    float sensorValue = (float) json.getDouble("value");
                    
                    // Validate sensor value range
                    if (!isValidSensorValue(sensorType, sensorValue)) {
                        Log.e(Constants.TAG_BLUETOOTH, "Value out of range: " + sensorValue + " for " + sensorType);
                        return;
                    }
                    
                    // Create and pass sensor data object to the view model
                    SensorData data = new SensorData(sensorType, sensorValue);
                    bluetoothViewModel.handleNewData(data);
                    
                    Log.d(Constants.TAG_BLUETOOTH, "Successfully processed " + sensorType + " data (alternate format): " + sensorValue);
                    return;
                }
                
                Log.e(Constants.TAG_BLUETOOTH, "JSON missing required fields: " + stringValue);
                
            } catch (JSONException e) {
                // Not a valid JSON, try as plain number as fallback
                Log.d(Constants.TAG_BLUETOOTH, "Not a valid JSON, trying as plain number: " + e.getMessage());
                try {
                    float sensorValue = Float.parseFloat(stringValue);
                    
                    // Validate sensor value range
                    if (!isValidSensorValue(sensorType, sensorValue)) {
                        Log.e(Constants.TAG_BLUETOOTH, "Value out of range: " + sensorValue + " for " + sensorType);
                        return;
                    }
                    
                    // Create and pass sensor data object to the view model
                    SensorData data = new SensorData(sensorType, sensorValue);
                    bluetoothViewModel.handleNewData(data);
                    
                    Log.d(Constants.TAG_BLUETOOTH, "Successfully processed " + sensorType + " data (plain number): " + sensorValue);
                    
                } catch (NumberFormatException nfe) {
                    Log.e(Constants.TAG_BLUETOOTH, "Value is neither valid JSON nor a number: " + stringValue);
                }
            }
        } catch (Exception e) {
            Log.e(Constants.TAG_BLUETOOTH, "Error processing characteristic data: " + e.getMessage(), e);
        }
    }

    /**
     * Safely updates the ViewModel on the main thread
     * @param action The action to run on the main thread
     */
    private void updateViewModelSafely(Runnable action) {
        if (bluetoothViewModel == null) {
            Log.e(Constants.TAG_BLUETOOTH, "Cannot update ViewModel - it is null");
            return;
        }
        
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                // We're already on the main thread, execute directly
                action.run();
            } else {
                // Post to main thread
                new Handler(Looper.getMainLooper()).post(action);
            }
        } catch (Exception e) {
            Log.e(Constants.TAG_BLUETOOTH, "Error updating ViewModel: " + e.getMessage());
        }
    }

    /**
     * Connect to specific SmartHat device
     * This uses a direct connection to the device with a known MAC address
     */
    public void connectToSpecificSmartHat() {
        if (testModeEnabled) {
            Log.d(Constants.TAG_BLUETOOTH, "Test mode active - skipping real device connection");
            bluetoothViewModel.updateConnectionState(Constants.STATE_CONNECTED);
            return;
        }
        
        try {
            // Update UI to connecting state
            Log.d(Constants.TAG_BLUETOOTH, "Setting connection state to CONNECTING");
            bluetoothViewModel.updateConnectionState(Constants.STATE_CONNECTING);
            
            // First try direct connection by MAC address
            BluetoothDevice device = bluetoothManager.getDeviceByMac();
            if (device != null) {
                Log.d(Constants.TAG_BLUETOOTH, "Found device by MAC address, attempting direct connection");
                connect(device);
                
                // Add timeout to fall back to scanning
                timeoutHandler.postDelayed(() -> {
                    // If still connecting after timeout, try scanning approach
                    Integer currentState = bluetoothViewModel.getConnectionState().getValue();
                    Log.d(Constants.TAG_BLUETOOTH, "Connection timeout check - current state: " + currentState);
                    
                    if (currentState != null && currentState == Constants.STATE_CONNECTING) {
                        Log.d(Constants.TAG_BLUETOOTH, "Direct connection timed out, falling back to scanning");
                        disconnect();
                        bluetoothManager.scanAndConnect();
                    }
                }, 5000); // 5 second timeout for direct connection
            } else {
                Log.d(Constants.TAG_BLUETOOTH, "Device not found by MAC, starting scan");
                bluetoothManager.scanAndConnect();
            }
        } catch (Exception e) {
            Log.e(Constants.TAG_BLUETOOTH, "Error connecting to SmartHat: " + e.getMessage(), e);
            bluetoothViewModel.handleError("Error connecting to SmartHat: " + e.getMessage());
        }
    }

    /**
     * Prepare test mode (just set the flag but don't start data generation)
     * This lets the user manually connect/disconnect in test mode using the Connect button
     */
    public void prepareTestMode() {
        try {
            // Log all current info about the device
            Log.i(Constants.TAG_BLUETOOTH, "Preparing test mode...");
            
            // Mock the connection
            bluetoothViewModel.updateConnectionState(Constants.STATE_CONNECTED);
            
            // Start generating test data
            setTestMode(true);
            startMockDataGeneration();
            
            Log.i(Constants.TAG_BLUETOOTH, "Test mode prepared successfully");
        } catch (Exception e) {
            Log.e(Constants.TAG_BLUETOOTH, "Error preparing test mode: " + e.getMessage());
        }
    }

    public void showDustAlert(SensorData data) {
        // Skip notifications for test data
        if (data.isTestData()) {
            Log.d(Constants.TAG_MAIN, "Skipping notification for TEST dust data: " + data.getValue());
            return;
        }
        // ... rest of the method that shows the notification
    }
}