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
    private final BluetoothManager bluetoothManager;
    
    // test mode fields
    private boolean testModeEnabled = false;
    private Handler testModeHandler;
    private final Runnable testDataRunnable = new Runnable() {
        @Override
        public void run() {
            // generate random sensor data
            generateMockSensorData();
            // schedule next data generation
            if (testModeEnabled) {
                testModeHandler.postDelayed(this, 2000); // generate data every 2 seconds
            }
        }
    };
    // endregion
    
    // region callbacks
    // scan callback for device discovery
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String deviceName = device.getName();
            
            // checking if device is our esp32 
            if (deviceName != null && deviceName.contains("SmartHat")) {
                Log.d(Constants.TAG_BLUETOOTH, "found device: " + deviceName);
                
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
                bluetoothManager.stopScan();
            }
        }, Constants.SCAN_PERIOD);
        
        try {
            bluetoothManager.scanForDevices(scanCallback);
        } catch (Exception e) {
            Log.e(Constants.TAG_BLUETOOTH, "error starting scan: " + e.getMessage());
            viewModel.handleError("error starting bluetooth scan: " + e.getMessage());
        }
    }
    
    /**
     * connect to the specified bluetoothdevice
     */
    public void connect(BluetoothDevice device) {
        // in test mode, just simulate connection
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
     * helper to enable notifications for a characteristic
     */
    private void enableCharacteristicNotification(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        // enable notification
        gatt.setCharacteristicNotification(characteristic, true);
        
        // descriptor setup would go here if needed
        // not needed for most simple implementations
    }
    
    /**
     * process json data received from characteristic with support for multiple formats
     * @param rawData the json string to parse
     * @param fallbackSensorType the sensor type to use if not specified in json
     */
    private void processJsonData(String rawData, String fallbackSensorType) throws JSONException {
        // parse the json string
        JSONObject json = new JSONObject(rawData);
        
        // support multiple json formats that may come from the arduino
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
     * disc gatt
     */
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
    // endregion
    
    // region test mode
    
    /**
     * Enable or disable test mode
     * @param enabled true to enable test mode, false to disable
     */
    public void setTestMode(boolean enabled) {
        this.testModeEnabled = enabled;
        if (enabled) {
            Log.d(Constants.TAG_BLUETOOTH, "test mode enabled - bluetooth hardware will be bypassed");
            // If already connected, disconnect real connection
            if (bluetoothGatt != null) {
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
                bluetoothGatt = null;
                bluetoothManager.setBluetoothGatt(null);
            }
        } else {
            Log.d(Constants.TAG_BLUETOOTH, "test mode disabled");
            stopMockDataGeneration();
            // Reset connection state
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
     * random data gen
     */
    private void generateMockSensorData() {
        Log.d(Constants.TAG_BLUETOOTH, "generating mock sensor data in test mode");
        
        // random dust reading (10-50 microg / m^3) 
        float dustValue = 10 + (float) (Math.random() * 40);
        SensorData dustData = new SensorData("dust", dustValue);
        viewModel.handleNewData(dustData);
        
        // random noise reading(40-85 dB)
        float noiseValue = 40 + (float) (Math.random() * 45);
        SensorData noiseData = new SensorData("noise", noiseValue);
        viewModel.handleNewData(noiseData);
        
        // occasionally simulate threshold alerts
        if (Math.random() > 0.7) {
            if (Math.random() > 0.5) {
                // high dust reading sim
                dustData = new SensorData("dust", Constants.DUST_THRESHOLD + 10);
                viewModel.handleNewData(dustData);
                
                // sample json formats sim
                if (Math.random() > 0.5) {
                    Log.d(Constants.TAG_BLUETOOTH, "sample json: {\"sensor\":\"dust\",\"value\":" + (Constants.DUST_THRESHOLD + 10) + "}");
                } else {
                    Log.d(Constants.TAG_BLUETOOTH, "sample json: {\"dust\":" + (Constants.DUST_THRESHOLD + 10) + "}");
                }
            } else {
                //high noise reading sim
                noiseData = new SensorData("noise", Constants.NOISE_THRESHOLD + 5);
                viewModel.handleNewData(noiseData);
                
                // log sample json formats
                if (Math.random() > 0.5) {
                    Log.d(Constants.TAG_BLUETOOTH, "sample json: {\"sensor\":\"noise\",\"value\":" + (Constants.NOISE_THRESHOLD + 5) + "}");
                } else {
                    Log.d(Constants.TAG_BLUETOOTH, "sample json: {\"noise\":" + (Constants.NOISE_THRESHOLD + 5) + "}");
                }
            }
        }
    }
    // endregion
}