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
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.team12.smarthat.models.SensorData;
import com.team12.smarthat.utils.Constants;
import com.team12.smarthat.viewmodels.BluetoothViewModel;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class BluetoothService {
    // ble connection
    private BluetoothGatt bluetoothGatt;
    // updates ui state,sensor data,errors
    private final BluetoothViewModel viewModel;
    private final BluetoothManager bluetoothManager;
    
    // test mode fields
    private boolean testModeEnabled = false;
    private Handler testModeHandler;
    private final Runnable testDataRunnable = new Runnable() {
        @Override
        public void run() {
            // generate random sensor data
            generateMockSensorData();
            
            // schedule next data generation with variable timing for more natural feel
            if (testModeEnabled) {
                // Random delay between 2-5 seconds (increased from 1-3 for slower generation)
                int randomDelay = 2000 + (int)(Math.random() * 3000);
                testModeHandler.postDelayed(this, randomDelay);
            }
        }
    };
    // endregion
    
    // region callbacks
    // scan callback for device discovery
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
                    Log.e(Constants.TAG_BLUETOOTH, "bluetooth connect permission not granted for accessing device name");
                    viewModel.handleError("permission issue when scanning");
                    return;
                }
                
                // checking if device is our esp32 
                if (deviceName != null && deviceName.contains("SmartHat")) {
                    Log.d(Constants.TAG_BLUETOOTH, "found device: " + deviceName);
                    

                    if (ContextCompat.checkSelfPermission(bluetoothManager.getContext(), 
                            Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                        bluetoothManager.stopScan();
                        connect(device);
                    } else {
                        Log.e(Constants.TAG_BLUETOOTH, "bluetooth scan permission not granted for stopping scan");
                        viewModel.handleError("permission issue when connecting to device");
                    }
                }
            } catch (SecurityException e) {
                Log.e(Constants.TAG_BLUETOOTH, "security exception in scan result: " + e.getMessage());
                viewModel.handleError("permission denied while processing scan result");
            } catch (Exception e) {
                Log.e(Constants.TAG_BLUETOOTH, "error processing scan result: " + e.getMessage());
                viewModel.handleError("error processing scan result");
            }
        }
        
        @Override
        public void onScanFailed(int errorCode) {
            String errorMsg = "scan failed: ";
            switch (errorCode) {
                case ScanCallback.SCAN_FAILED_ALREADY_STARTED:
                    errorMsg += "already started";
                    break;
                case ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                    errorMsg += "app registration failed";
                    break;
                case ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED:
                   
        errorMsg += "ble not supported";
                    break;
                case ScanCallback.SCAN_FAILED_INTERNAL_ERROR:
                    errorMsg += "internal error";
                    break;
                    
                default:
                    errorMsg += "unknown error";
            }
            viewModel.handleError(errorMsg);
        }
    };
    
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            try {

                if (ContextCompat.checkSelfPermission(bluetoothManager.getContext(), 
                        Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(Constants.TAG_BLUETOOTH, "bluetooth connect permission not granted for connection state change");
                    viewModel.handleError("permission issue when connecting");
                    viewModel.updateConnectionState(Constants.STATE_DISCONNECTED);
                    return;
                }
                
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        Log.d(Constants.TAG_BLUETOOTH, "connected to gatt server");
                        bluetoothGatt = gatt;
                        // discover services after connecting

                        gatt.discoverServices();
                        // update ui
                        viewModel.updateConnectionState(Constants.STATE_CONNECTED);
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        Log.d(Constants.TAG_BLUETOOTH, "disconnected from gatt server");
                    
                        viewModel.updateConnectionState(Constants.STATE_DISCONNECTED);
                    }
                } else {
                    // connection failed
                    String errorMsg = "connection failed with status: " + status;
                    Log.e(Constants.TAG_BLUETOOTH, errorMsg);
                    viewModel.handleError(errorMsg);
                    viewModel.updateConnectionState(Constants.STATE_DISCONNECTED);
                    disconnect();
                }
            } catch (SecurityException e) {
                Log.e(Constants.TAG_BLUETOOTH, "security exception in connection state change: " + e.getMessage());
                viewModel.handleError("permission denied during connection process");
                viewModel.updateConnectionState(Constants.STATE_DISCONNECTED);
            } catch (Exception e) {
                Log.e(Constants.TAG_BLUETOOTH, "error in connection state change: " + e.getMessage());
                viewModel.handleError("error during connection process");
                viewModel.updateConnectionState(Constants.STATE_DISCONNECTED);
            }
        }
        
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            try {

                if (ContextCompat.checkSelfPermission(bluetoothManager.getContext(), 
                        Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(Constants.TAG_BLUETOOTH, "bluetooth connect permission not granted for service discovery");
                    viewModel.handleError("permission issue when discovering services");
                    return;
                }
                
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    // find our service
                    BluetoothGattService service = gatt.getService(Constants.SERVICE_UUID);
                    if (service != null) {
                        Log.d(Constants.TAG_BLUETOOTH, "found service");
                        // enable notifications for all characteristics
                        enableSensorNotifications(gatt, service);
                    } else {
                        Log.e(Constants.TAG_BLUETOOTH, "service not found");
                        viewModel.handleError("service not found on device");
                    }
                } else {
                    Log.e(Constants.TAG_BLUETOOTH, "service discovery failed: " + status);
                    viewModel.handleError("service discovery failed");
                }
            } catch (SecurityException e) {
                Log.e(Constants.TAG_BLUETOOTH, "security exception in service discovery: " + e.getMessage());
                viewModel.handleError("permission denied during service discovery");
            } catch (Exception e) {
                Log.e(Constants.TAG_BLUETOOTH, "error in service discovery: " + e.getMessage());
                viewModel.handleError("error during service discovery");
            }
        }
        
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            try {
               
                if (ContextCompat.checkSelfPermission(bluetoothManager.getContext(), 
                        Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(Constants.TAG_BLUETOOTH, "bluetooth connect permission not granted for characteristic change");
                    viewModel.handleError("permission issue when reading sensor data");
                    return;
                }
                
                // get data for sensor type
                UUID uuid = characteristic.getUuid();
                byte[] data = characteristic.getValue();
                String sensorType;
                
                // sensor type based on characteristic uuid
                if (uuid.equals(Constants.DUST_CHARACTERISTIC_UUID)) {
                    sensorType = "dust";
                } else if (uuid.equals(Constants.NOISE_CHARACTERISTIC_UUID)) {
                    sensorType = "noise";
                } else {
                    return; // unknown characteristic, ignore
                }
                
                // convert bytes to string for processing
                String stringValue = new String(data, StandardCharsets.UTF_8);
                Log.d(Constants.TAG_BLUETOOTH, "received data: " + stringValue);
                
                try {
                    // try different formats that may come from the arduino
                    // 1. try as json if it starts with '{'
                    if (stringValue.trim().startsWith("{")) {
                        processJsonData(stringValue, sensorType);
                    } 
                    // 2. try as plain number
                    else {
                        try {
                            float value = Float.parseFloat(stringValue.trim());
                            SensorData sensorData = new SensorData(sensorType, value);
                            viewModel.handleNewData(sensorData);
                        } catch (NumberFormatException e) {
                            viewModel.handleError("invalid number format: " + stringValue);
                        }
                    }
                } catch (Exception e) {
                    viewModel.handleError("data processing error: " + e.getMessage());
                }
            } catch (SecurityException e) {
                Log.e(Constants.TAG_BLUETOOTH, "security exception in characteristic change: " + e.getMessage());
                viewModel.handleError("permission denied when reading sensor data");
            } catch (Exception e) {
                Log.e(Constants.TAG_BLUETOOTH, "error in characteristic change: " + e.getMessage());
                viewModel.handleError("error processing sensor data");
            }
        }
    };
    // endregion
    
    // region constructors
    public BluetoothService(BluetoothViewModel viewModel, BluetoothManager bluetoothManager) {
        this.viewModel = viewModel;
        this.bluetoothManager = bluetoothManager;
        
        // setup callbacks
        bluetoothManager.setViewModel(viewModel);
        bluetoothManager.setGattCallback(gattCallback);
        
        // initialize test mode handler
        testModeHandler = new Handler(Looper.getMainLooper());
    }
    // endregion
    
    // region scanning and connection
    /** 
     * SCANNING FOR BLE 
     */
    public void startScan() {
        // check if test mode is enabled
        if (testModeEnabled) {
            Log.d(Constants.TAG_BLUETOOTH, "test mode active - bypassing actual bluetooth scanning");
            // simulate found device after a short delay
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                viewModel.updateConnectionState(Constants.STATE_CONNECTED);
                startMockDataGeneration();
            }, 1500); // simulate connection delay
            return;
        }
        
        // add log for debugging
        Log.d(Constants.TAG_BLUETOOTH, "starting ble scan for smarthat device");
        
        // set timeout for scan
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (viewModel.getConnectionState().getValue().equals(Constants.STATE_DISCONNECTED)) {
                viewModel.handleError("no smarthat device found nearby");
                try {
               
                    if (ContextCompat.checkSelfPermission(bluetoothManager.getContext(), 
                            Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                        bluetoothManager.stopScan();
                    }
                } catch (SecurityException e) {
                    Log.e(Constants.TAG_BLUETOOTH, "security exception when stopping scan: " + e.getMessage());
                }
            }
        }, Constants.SCAN_PERIOD);
        
        try {

            if (ContextCompat.checkSelfPermission(bluetoothManager.getContext(), 
                    Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                viewModel.handleError("bluetooth scan permission not granted");
                return;
            }
            bluetoothManager.scanForDevices(scanCallback);
        } catch (SecurityException e) {
            Log.e(Constants.TAG_BLUETOOTH, "security exception during scan: " + e.getMessage());
            viewModel.handleError("permission denied for bluetooth scan: " + e.getMessage());
        } catch (Exception e) {
            Log.e(Constants.TAG_BLUETOOTH, "error starting scan: " + e.getMessage());
            viewModel.handleError("error starting bluetooth scan: " + e.getMessage());
        }
    }
    

    public void connect(BluetoothDevice device) {
        // in test mode, just simulate connection
        if (testModeEnabled) {
            Log.d(Constants.TAG_BLUETOOTH, "test mode active - simulating connection");
            viewModel.updateConnectionState(Constants.STATE_CONNECTED);
            startMockDataGeneration();
            return;
        }
        
        try {
            
            if (ContextCompat.checkSelfPermission(bluetoothManager.getContext(), 
                    Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                viewModel.handleError("bluetooth connect permission not granted");
                return;
            }
            
            // connect to device using manager
            Log.d(Constants.TAG_BLUETOOTH, "connecting to device: " + device.getName());
            bluetoothGatt = bluetoothManager.connectToBleDevice(device);
            bluetoothManager.setBluetoothGatt(bluetoothGatt);
        } catch (SecurityException e) {
            Log.e(Constants.TAG_BLUETOOTH, "security exception during connect: " + e.getMessage());
            viewModel.handleError("permission denied for bluetooth connection: " + e.getMessage());
        } catch (Exception e) {
            Log.e(Constants.TAG_BLUETOOTH, "error connecting: " + e.getMessage());
            viewModel.handleError("error connecting to device: " + e.getMessage());
        }
    }
    
    /**
     * enable notif for sensor vlaues
     */
    private void enableSensorNotifications(BluetoothGatt gatt, BluetoothGattService service) {
        // get characteristics
        BluetoothGattCharacteristic dustChar = service.getCharacteristic(Constants.DUST_CHARACTERISTIC_UUID);
        BluetoothGattCharacteristic noiseChar = service.getCharacteristic(Constants.NOISE_CHARACTERISTIC_UUID);
        
        // enable notifications for both sensors
        if (dustChar != null) {
            enableCharacteristicNotification(gatt, dustChar);
        }
        
        if (noiseChar != null) {
            enableCharacteristicNotification(gatt, noiseChar);
        }
    }
    
    /**
     * helper to enable notifications 
     */
    private void enableCharacteristicNotification(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        try {
            
            if (ContextCompat.checkSelfPermission(bluetoothManager.getContext(), 
                    Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.e(Constants.TAG_BLUETOOTH, "bluetooth connect permission not granted for enabling notifications");
                viewModel.handleError("permission issue when enabling sensor notifications");
                return;
            }
            
            // enable notification
            gatt.setCharacteristicNotification(characteristic, true);
            
            // ble config descriptor
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
            
            if (descriptor != null) {

                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor);
            }
        } catch (SecurityException e) {
            Log.e(Constants.TAG_BLUETOOTH, "security exception while enabling notifications: " + e.getMessage());
            viewModel.handleError("permission denied for bluetooth operation");
        } catch (Exception e) {
            Log.e(Constants.TAG_BLUETOOTH, "error enabling notifications: " + e.getMessage());
            viewModel.handleError("error setting up sensor notifications");
        }  
    }
    
    /**
     *  support for multiple json formats 
     * @param rawData the json string to parse
     * @param fallbackSensorType the sensor type to use if not specified in json
     */
    private void processJsonData(String rawData, String fallbackSensorType) throws JSONException {
        // parse the json string
        JSONObject json = new JSONObject(rawData);
        
        // support multiple json formats from the arduino
        //might remove/change when sensor repo is ready
        // format 1 -> {"sensor": "dust", "value": 25.4}
        if (json.has("sensor") && json.has("value")) {
            SensorData data = new SensorData(
                    json.getString("sensor"),
                    (float) json.getDouble("value")
            );
            viewModel.handleNewData(data);
            return;
        }
        
        // format 2 -> {"dust": 25.4} or {"noise": 65.7}
        if (json.has("dust")) {
            SensorData data = new SensorData("dust", (float) json.getDouble("dust"));
            viewModel.handleNewData(data);
            return;
        }
        
        if (json.has("noise")) {
            SensorData data = new SensorData("noise", (float) json.getDouble("noise"));
            viewModel.handleNewData(data);
            return;
        }
        
        // format 3 -> {"type": "dust", "reading": 25.4}
        if (json.has("type") && json.has("reading")) {
            SensorData data = new SensorData(
                    json.getString("type"),
                    (float) json.getDouble("reading")
            );
            viewModel.handleNewData(data);
            return;
        }
        
        // format 4 -> {"value": 25.4} - use fallback sensor type from characteristic uuid
        if (json.has("value")) {
            SensorData data = new SensorData(
                    fallbackSensorType,
                    (float) json.getDouble("value")
            );
            viewModel.handleNewData(data);
            return;
        }
        
        //case the json format wasn't recognized
        throw new JSONException("unrecognized json format: " + rawData);
    }
    
    /**
     * disc from gatt
     */
    public void disconnect() {
        if (testModeEnabled) {
            stopMockDataGeneration();
           
            viewModel.updateConnectionState(Constants.STATE_DISCONNECTED);

            return;
        }
        
        if (bluetoothGatt != null) {
            try {
                
                if (ContextCompat.checkSelfPermission(bluetoothManager.getContext(), 
                        Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(Constants.TAG_BLUETOOTH, "bluetooth connect permission not granted for disconnecting");
                    viewModel.handleError("permission issue when disconnecting");
                   
                    viewModel.updateConnectionState(Constants.STATE_DISCONNECTED);
                    return;
                }
                
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
                bluetoothGatt = null;
                bluetoothManager.setBluetoothGatt(null);
            } catch (SecurityException e) {
                Log.e(Constants.TAG_BLUETOOTH, "security exception while disconnecting: " + e.getMessage());
                viewModel.handleError("permission denied for bluetooth disconnection");
                // update UI state
                viewModel.updateConnectionState(Constants.STATE_DISCONNECTED);
            } catch (Exception e) {
                Log.e(Constants.TAG_BLUETOOTH, "error disconnecting: " + e.getMessage());
                viewModel.handleError("error disconnecting from device: " + e.getMessage());
                // update ui state
                viewModel.updateConnectionState(Constants.STATE_DISCONNECTED);
            }
        }
    }
    // endregion
    
    // region test mode
    
    /**
     * Enable or disable test mode
     * @param enabled true to enable test mode, false to disable
     */
    public void setTestMode(boolean enabled) {
        try {
            if (enabled) {
                if (!testModeEnabled) {
                    // enabling test mode
                    Log.d(Constants.TAG_BLUETOOTH, "test mode enabled - bluetooth hardware will be bypassed");
                    testModeEnabled = true;
                    
                    // init handler if needed
                    if (testModeHandler == null) {
                        testModeHandler = new Handler(Looper.getMainLooper());
                    }
                    
                    // start data generation (simulates device connection)
                    startMockDataGeneration();
                    
                    // For test mode, we automatically connect (simulate connection)
                    viewModel.updateConnectionState(Constants.STATE_CONNECTED);
                }
            } else {
                Log.d(Constants.TAG_BLUETOOTH, "test mode disabled");
                stopMockDataGeneration();
               
                // reset state
                viewModel.updateConnectionState(Constants.STATE_DISCONNECTED);
            }
        } catch (Exception e) {
            Log.e(Constants.TAG_BLUETOOTH, "error setting test mode: " + e.getMessage());
            viewModel.handleError("error setting test mode: " + e.getMessage());
        }
    }
    
    /**
     * @return true if test mode is enabled
     */
    public boolean isTestModeEnabled() {
        return testModeEnabled;
    }
    
    /**
     * mocck data gen
     */
    private void startMockDataGeneration() {
        if (!testModeEnabled) return;
        
        Log.d(Constants.TAG_BLUETOOTH, "starting mock data generation");
        testModeHandler.post(testDataRunnable);
    }
    
    /**
     * stop gen
     */
    private void stopMockDataGeneration() {
        Log.d(Constants.TAG_BLUETOOTH, "stopping mock data generation");
        testModeHandler.removeCallbacks(testDataRunnable);
    }
    
    /**
     * generates random sensor data in test mode
     */
    private void generateMockSensorData() {
        if (!testModeEnabled) {
            Log.e(Constants.TAG_BLUETOOTH, "Test mode not enabled, cannot generate mock data");
            return;
        }
        
        Log.d(Constants.TAG_BLUETOOTH, "Generating mock sensor data in test mode");
        
        try {
            // Alternate between dust and noise data generation to reduce frequency
            // Use system time to alternate: even seconds for dust, odd seconds for noise
            boolean generateDustData = (System.currentTimeMillis() / 1000) % 2 == 0;
            
            if (generateDustData) {
                // Generate dust value with 25% chance of high reading
                boolean highDustReading = Math.random() < 0.25;
                float dustValue;
                
                if (highDustReading) {
                    // High dust reading (50-120 μg/m³)
                    dustValue = Constants.DUST_THRESHOLD + (float)(Math.random() * 70);
                    Log.d(Constants.TAG_BLUETOOTH, "HIGH DUST generated: " + dustValue + " μg/m³");
                } else {
                    // Normal dust reading (5-45 μg/m³)
                    dustValue = 5f + (float)(Math.random() * 40);
                    Log.d(Constants.TAG_BLUETOOTH, "Normal dust generated: " + dustValue + " μg/m³");
                }
                
                // Create dust sensor data
                final SensorData dustData = new SensorData("dust", dustValue);
                
                // Send dust data on the main thread
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    try {
                        viewModel.handleNewData(dustData);
                        Log.d(Constants.TAG_BLUETOOTH, "Dust data sent to ViewModel: " + dustValue);
                    } catch (Exception e) {
                        Log.e(Constants.TAG_BLUETOOTH, "Error sending dust data: " + e.getMessage(), e);
                    }
                });
            } else {
                // Generate noise value with 25% chance of high reading
                boolean highNoiseReading = Math.random() < 0.25;
                float noiseValue;
                
                if (highNoiseReading) {
                    // High noise reading (85-120 dB)
                    noiseValue = Constants.NOISE_THRESHOLD + (float)(Math.random() * 35);
                    Log.d(Constants.TAG_BLUETOOTH, "HIGH NOISE generated: " + noiseValue + " dB");
                } else {
                    // Normal noise reading (40-80 dB)
                    noiseValue = 40f + (float)(Math.random() * 40);
                    Log.d(Constants.TAG_BLUETOOTH, "Normal noise generated: " + noiseValue + " dB");
                }
                
                // Create noise sensor data
                final SensorData noiseData = new SensorData("noise", noiseValue);
                
                // Send noise data on the main thread
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    try {
                        viewModel.handleNewData(noiseData);
                        Log.d(Constants.TAG_BLUETOOTH, "Noise data sent to ViewModel: " + noiseValue);
                    } catch (Exception e) {
                        Log.e(Constants.TAG_BLUETOOTH, "Error sending noise data: " + e.getMessage(), e);
                    }
                });
            }
            
        } catch (Exception e) {
            Log.e(Constants.TAG_BLUETOOTH, "Error in generateMockSensorData: " + e.getMessage(), e);
        }
    }
    
    /**
     * forces immediate test data generation
     */
    public void forceGenerateTestData() {
        if (testModeEnabled) {
            Log.d(Constants.TAG_BLUETOOTH, "Forcing immediate test data generation");
            // Generate data right away
            generateMockSensorData();
            // Also ensure the runnable is scheduled
            startMockDataGeneration();
        }
    }
    // endregion
}