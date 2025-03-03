package com.team12.smarthat.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.team12.smarthat.models.SensorData;
import com.team12.smarthat.utils.Constants;
import com.team12.smarthat.viewmodels.BluetoothViewModel;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public class BluetoothService {
    // ble connection
    private BluetoothGatt bluetoothGatt;
    // updates ui state,sensor data,errors
    private final BluetoothViewModel viewModel;
    // ble manager
    private final BluetoothManager bluetoothManager;
    
    // Test mode fields
    private boolean testModeEnabled = false;
    private Handler testModeHandler;
    private final Runnable testDataRunnable = new Runnable() {
        @Override
        public void run() {
            // Generate random sensor data
            generateMockSensorData();
            // Schedule next data generation
            if (testModeEnabled) {
                testModeHandler.postDelayed(this, 2000); // Generate data every 2 seconds
            }
        }
    };
    
    // scan callback - for device discovery
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String deviceName = device.getName();
            
            // checking if device is our esp32 
            if (deviceName != null && deviceName.contains("SmartHat")) {
                Log.d(Constants.TAG_BLUETOOTH, "found device: " + deviceName);
                // found our device, connect to it
                bluetoothManager.stopScan();
                connect(device);
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
    
    // gatt callback - handles connection & data events
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
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
                    // update ui
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
        }
        
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
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
        }
        
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            // reading characteristic data - will contain sensor values
            UUID uuid = characteristic.getUuid();
            byte[] data = characteristic.getValue();
            String sensorType;
            
            // determine sensor type by characteristic uuid
            if (uuid.equals(Constants.DUST_CHARACTERISTIC_UUID)) {
                sensorType = "dust";
            } else if (uuid.equals(Constants.NOISE_CHARACTERISTIC_UUID)) {
                sensorType = "noise";
            } else {
                return; // unknown characteristic
            }
            
            // convert bytes to string and process
            String stringValue = new String(data, StandardCharsets.UTF_8);
            try {
                // might be pure float or json format
                try {
                    // try as json
                    processData(stringValue);
                } catch (JSONException e) {
                    // try as pure float value
                    float value = Float.parseFloat(stringValue.trim());
                    SensorData sensorData = new SensorData(sensorType, value);
                    viewModel.handleNewData(sensorData);
                }
            } catch (Exception e) {
                viewModel.handleError("invalid data format: " + e.getMessage());
            }
        }
    };
    
    public BluetoothService(BluetoothViewModel viewModel, BluetoothManager bluetoothManager) {
        this.viewModel = viewModel;
        this.bluetoothManager = bluetoothManager;
        
        // setup callbacks
        bluetoothManager.setViewModel(viewModel);
        bluetoothManager.setGattCallback(gattCallback);
        
        // Initialize test mode handler
        testModeHandler = new Handler(Looper.getMainLooper());
    }
    
  
    /** kicks off device scanning */
    public void startScan() {
        // Check if test mode is enabled
        if (testModeEnabled) {
            Log.d(Constants.TAG_BLUETOOTH, "test mode active - bypassing actual bluetooth scanning");
            // Simulate found device after a short delay
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                viewModel.updateConnectionState(Constants.STATE_CONNECTED);
                startMockDataGeneration();
            }, 1500); // Simulate connection delay
            return;
        }
        
        // adding some logs for easier debugging
        Log.d(Constants.TAG_BLUETOOTH, "starting scan... hope we find something");
        
        // gonna timeout after a bit if no device found
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (viewModel.getConnectionState().getValue().equals(Constants.STATE_DISCONNECTED)) {
                viewModel.handleError("No SmartHat device found nearby");
                bluetoothManager.stopScan();
            }
        }, Constants.SCAN_PERIOD);
        
        try {
            bluetoothManager.scanForDevices(scanCallback);
        } catch (Exception e) {
            Log.e(Constants.TAG_BLUETOOTH, "error starting scan: " + e.getMessage());
            viewModel.handleError("Error starting Bluetooth scan: " + e.getMessage());
        }
    }
    
    // connect to device - can be called directly or from scan
    public void connect(BluetoothDevice device) {
        // In test mode, just simulate connection
        if (testModeEnabled) {
            Log.d(Constants.TAG_BLUETOOTH, "test mode active - simulating connection");
            viewModel.updateConnectionState(Constants.STATE_CONNECTED);
            startMockDataGeneration();
            return;
        }
        
        try {
            bluetoothGatt = bluetoothManager.connectToBleDevice(device);
            bluetoothManager.setBluetoothGatt(bluetoothGatt);
        } catch (Exception e) {
            Log.e(Constants.TAG_BLUETOOTH, "error connecting: " + e.getMessage());
            viewModel.handleError("Error connecting to device: " + e.getMessage());
        }
    }
    
    // enable notifications for sensor values
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
    
    // helper to enable notifications
    private void enableCharacteristicNotification(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        // enable notification
        gatt.setCharacteristicNotification(characteristic, true);
        
        // descriptor setup would go here if needed
        // not needed for most simple implementations
    }
    
    // process json data from characteristic
    private void processData(String rawData) throws JSONException {
        // json object from rawdata string
        JSONObject json = new JSONObject(rawData);
        //sensor data object using json
        SensorData data = new SensorData(
                json.getString("sensor"),
                (float) json.getDouble("value")
        );
        // update viewmodel: new sensor val
        viewModel.handleNewData(data);
    }
    
    // disconnect from gatt server
    public void disconnect() {
        if (testModeEnabled) {
            stopMockDataGeneration();
            viewModel.updateConnectionState(Constants.STATE_DISCONNECTED);
            return;
        }
        
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
            bluetoothManager.setBluetoothGatt(null);
        }
    }
    
    /**
     * test mode methods
     */
    
    /**
     * enable or disable test mode
     * @param enabled true enable 
     */
    public void setTestMode(boolean enabled) {
        this.testModeEnabled = enabled;
        if (enabled) {
            Log.d(Constants.TAG_BLUETOOTH, "Test Mode Enabled");
            // no real connection yet!!!
            if (bluetoothGatt != null) {
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
                bluetoothGatt = null;
                bluetoothManager.setBluetoothGatt(null);
            }
        } else {
            Log.d(Constants.TAG_BLUETOOTH, "Test mode disabled");
            stopMockDataGeneration();
            // connection state eset
        
            viewModel.updateConnectionState(Constants.STATE_DISCONNECTED);
        }
    }
    
    /**
     * @return true if test mode is enabled
     */
    public boolean isTestModeEnabled() {
        return testModeEnabled;
    }
    
    /**
     * start generating mock sensor data
     */
    private void startMockDataGeneration() {
        if (!testModeEnabled) return;
        
        Log.d(Constants.TAG_BLUETOOTH, "Starting mock data generation");
        testModeHandler.post(testDataRunnable);
    }
    
    /**
     * stop generating mock sensor data
     */
    private void stopMockDataGeneration() {
        Log.d(Constants.TAG_BLUETOOTH, "Stopping mock data generation");
        testModeHandler.removeCallbacks(testDataRunnable);
    }
    
    /**
     * generate random sensor data
     */
    private void generateMockSensorData() {
        Log.d(Constants.TAG_BLUETOOTH, "generating mock sensor data in test mode");
        
        // random dust reading (10-50 micrograms/m³)
        float dustValue = 10 + (float) (Math.random() * 40);
        SensorData dustData = new SensorData("dust", dustValue);
        viewModel.handleNewData(dustData);
        
        //random noise  (40-85 dB)
        float noiseValue = 40 + (float) (Math.random() * 45);
        SensorData noiseData = new SensorData("noise", noiseValue);
        viewModel.handleNewData(noiseData);
        
        // occasional simulate threshold alerts
        if (Math.random() > 0.7) {
            if (Math.random() > 0.5) {
                // high dust reading sim
                dustData = new SensorData("dust", Constants.DUST_THRESHOLD + 10);
                viewModel.handleNewData(dustData);
            } else {
                // high noise reading sim
                noiseData = new SensorData("noise", Constants.NOISE_THRESHOLD + 5);
                viewModel.handleNewData(noiseData);
            }
        }
    }
}