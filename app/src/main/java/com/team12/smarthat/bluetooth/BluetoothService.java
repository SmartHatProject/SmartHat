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
                    viewModel.handleError("Permission issue when scanning");
                    return;
                }
                
                // Check if device is our SmartHat
                if (deviceName != null && (deviceName.contains("SmartHat") || deviceName.contains("ESP32_BLE_Device") || deviceName.contains("ESP32"))) {
                    Log.d(Constants.TAG_BLUETOOTH, "Found device: " + deviceName);
                    
                    if (ContextCompat.checkSelfPermission(bluetoothManager.getContext(), 
                            Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                        bluetoothManager.stopScan();
                        connect(device);
                    } else {
                        Log.e(Constants.TAG_BLUETOOTH, "Bluetooth scan permission not granted for stopping scan");
                        viewModel.handleError("Permission issue when connecting to device");
                    }
                }
            } catch (SecurityException e) {
                Log.e(Constants.TAG_BLUETOOTH, "Security exception in scan result: " + e.getMessage());
                viewModel.handleError("Permission denied while processing scan result");
            } catch (Exception e) {
                Log.e(Constants.TAG_BLUETOOTH, "Error processing scan result: " + e.getMessage());
                viewModel.handleError("Error processing scan result");
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
            viewModel.handleError(errorMsg);
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
    

    public BluetoothService(BluetoothViewModel viewModel, BluetoothManager bluetoothManager) {
        this.viewModel = viewModel;
        this.bluetoothManager = bluetoothManager;
        
        //callbacks
        bluetoothManager.setViewModel(viewModel);
        bluetoothManager.setGattCallback(gattCallback);
        
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
     * scanning for ble
     */
    public void startScan() {

        connectionStartTime = System.currentTimeMillis();
        
        // check if test mode is enabled
        if (testModeEnabled) {
            Log.d(Constants.TAG_BLUETOOTH, "test mode active - bypassing actual bluetooth scanning");
            // sim found device with a little delay
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                viewModel.updateConnectionState(Constants.STATE_CONNECTED);
                startMockDataGeneration();
                
                // log sim connection time
                long connectionTime = System.currentTimeMillis() - connectionStartTime;
                Log.d(Constants.TAG_BLUETOOTH, "Simulated connection established in " + connectionTime + "ms");
            }, 1500); // simulate connection delay
            return;
        }
        
        // log4debug
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
        // test mode
        if (testModeEnabled) {
            Log.d(Constants.TAG_BLUETOOTH, "test mode active - simulating connection");
            viewModel.updateConnectionState(Constants.STATE_CONNECTED);
            startMockDataGeneration();
            return;
        }
        
        // store device for reuse
        lastConnectedDevice = device;
        
        try {
            if (ContextCompat.checkSelfPermission(bluetoothManager.getContext(), 
                    Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                viewModel.handleError("bluetooth connect permission not granted");
                return;
            }
            
            // connection timeout
            startConnectionTimeout();
            
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
            handleConnectionError("Error connecting to device: " + e.getMessage());
        }
    }
    
    /**
     * enable notif for sensor vlaues
     */
    private void enableSensorNotifications(BluetoothGatt gatt, BluetoothGattService service) {
        // get characteristics
        BluetoothGattCharacteristic dustChar = service.getCharacteristic(Constants.DUST_CHARACTERISTIC_UUID);
        BluetoothGattCharacteristic soundChar = service.getCharacteristic(Constants.SOUND_CHARACTERISTIC_UUID);
        
        // enable notifications for both sensors
        if (dustChar != null) {
            enableCharacteristicNotification(gatt, dustChar);
        }
        
        if (soundChar != null) {
            enableCharacteristicNotification(gatt, soundChar);
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
                    Constants.CLIENT_CONFIG_DESCRIPTOR_UUID);
            
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
        // validate json
        if (!validateSensorJson(rawData)) {
            Log.e(Constants.TAG_BLUETOOTH, "Invalid JSON format: " + rawData);
            viewModel.handleError("Invalid sensor data format received");
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
            viewModel.handleNewData(data);
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
                    Log.e(Constants.TAG_BLUETOOTH, "Cannot disconnect: No BLUETOOTH_CONNECT permission");
                    return;
                }
                
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
                bluetoothGatt = null;
                bluetoothManager.setBluetoothGatt(null);
            } catch (Exception e) {
                Log.e(Constants.TAG_BLUETOOTH, "Error during disconnect: " + e.getMessage());
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
                    
                    // test mode auto sim connect
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
            // reduce  frequency with dust/noise alt data generation
            // used system time even dust odd noise
            boolean generateDustData = (System.currentTimeMillis() / 1000) % 2 == 0;
            // will add a config test with more options later
            if (generateDustData) {
                //dust 25% high
                boolean highDustReading = Math.random() < 0.25;
                float dustValue;
                
                if (highDustReading) {
                    // high dust
                    dustValue = Constants.DUST_THRESHOLD + (float)(Math.random() * 70);
                    Log.d(Constants.TAG_BLUETOOTH, "HIGH DUST generated: " + dustValue + " μg/m³");
                } else {
                    // normal dust
                    dustValue = 5f + (float)(Math.random() * 40);
                    Log.d(Constants.TAG_BLUETOOTH, "Normal dust generated: " + dustValue + " μg/m³");
                }
                
                // dust data
                final SensorData dustData = new SensorData("dust", dustValue);
                
                // send dust data on the main thread
                // will thread management later
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
                //noise 25% high
                boolean highNoiseReading = Math.random() < 0.25;
                float noiseValue;
                
                if (highNoiseReading) {
                    // high noise
                    noiseValue = Constants.NOISE_THRESHOLD + (float)(Math.random() * 35);
                    Log.d(Constants.TAG_BLUETOOTH, "HIGH NOISE generated: " + noiseValue + " dB");
                } else {
                    // normal noise
                    noiseValue = 40f + (float)(Math.random() * 40);
                    Log.d(Constants.TAG_BLUETOOTH, "Normal noise generated: " + noiseValue + " dB");
                }
                
                // noise sensor data
                final SensorData noiseData = new SensorData("noise", noiseValue);
                
                // send on main thread
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
     * forcegenerate test data with high values
     * notification test
     */
    //might add a notification test btn just for this
    public void forceGenerateTestData() {
        if (!testModeEnabled) {
            Log.d(Constants.TAG_BLUETOOTH, "Test mode not enabled, ignoring forceGenerateTestData()");
            return;
        }
        
        //high dust gen
        float highDustValue = Constants.DUST_THRESHOLD + 20.0f + (float) (Math.random() * 30.0f);
        SensorData dustData = new SensorData("dust", highDustValue);
        viewModel.handleNewData(dustData);
        
        // high noise gen
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            float highNoiseValue = Constants.NOISE_THRESHOLD + 10.0f + (float) (Math.random() * 20.0f);
            SensorData noiseData = new SensorData("noise", highNoiseValue);
            viewModel.handleNewData(noiseData);
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
                    viewModel.handleError("Burst test completed: " + 
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
                    viewModel.handleNewData(sensorData);
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
     * Start connection timeout
     */
    private void startConnectionTimeout() {
        // cancle if exists
        timeoutHandler.removeCallbacksAndMessages(null);
        
        // new timeout
        timeoutHandler.postDelayed(() -> {
            Log.e(Constants.TAG_BLUETOOTH, "connection timed out");
            if (viewModel.getConnectionState().getValue() != null && 
                !viewModel.getConnectionState().getValue().equals(Constants.STATE_CONNECTED)) {
                disconnect();
                viewModel.handleError("Connection timed out");
                // reconnect case retry left
                handleConnectionError("Connection timed out");
            }
        }, CONNECTION_TIMEOUT);
    }
    
    /**
     * connection errors handling with retry logic
     */
    private void handleConnectionError(String error) {
        if (connectionRetries < MAX_RETRIES && lastConnectedDevice != null) {
            connectionRetries++;
            // update: exponential backoff 1s, 2s, 4s
            long delay = (long) Math.pow(2, connectionRetries - 1) * 1000;
            
            Log.d(Constants.TAG_BLUETOOTH, "Attempting reconnection, retry " + 
                  connectionRetries + " of " + MAX_RETRIES + 
                  " in " + delay + "ms");
            
            // rec after delay
            timeoutHandler.postDelayed(() -> {
                if (lastConnectedDevice != null) {
                    Log.d(Constants.TAG_BLUETOOTH, "Reconnecting to device: " + 
                          lastConnectedDevice.getAddress());
                    connect(lastConnectedDevice);
                }
            }, delay);
        } else {
            // reset counter if reties used
            connectionRetries = 0;
            viewModel.handleError("Connection failed after " + MAX_RETRIES + 
                                 " attempts: " + error);
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
        if (viewModel != null && viewModel.getDatabaseHelper() != null) {
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
                
                viewModel.handleNewData(metricsData);
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
            try {
                // cancel pending timeout when state change
                timeoutHandler.removeCallbacksAndMessages(null);

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
                        // case successful connection resetretiries
                        connectionRetries = 0;
                        
                        // log
                        logEnhancedConnectionMetrics(gatt);

                        gatt.discoverServices();
                        // update ui
                        viewModel.updateConnectionState(Constants.STATE_CONNECTED);
                    }
                }
            } catch (Exception e) {
                Log.e(Constants.TAG_BLUETOOTH, "Error handling connection state change: " + e.getMessage());
                viewModel.handleError("Error handling connection state change: " + e.getMessage());
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(Constants.TAG_BLUETOOTH, "Services discovered successfully");
                
                // Get the service
                BluetoothGattService service = gatt.getService(Constants.SERVICE_UUID);
                if (service != null) {
                    Log.d(Constants.TAG_BLUETOOTH, "Found custom SmartHat service");
                    
                    // Enable notifications for the characteristics
                    enableSensorNotifications(gatt, service);
                } else {
                    Log.e(Constants.TAG_BLUETOOTH, "Custom service not found");
                    viewModel.handleError("Device is not a compatible SmartHat");
                }
            } else {
                Log.e(Constants.TAG_BLUETOOTH, "Service discovery failed with status: " + status);
                viewModel.handleError("Failed to discover services");
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] value = characteristic.getValue();
            processCharacteristicData(characteristic, value);
        }
        
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                byte[] value = characteristic.getValue();
                processCharacteristicData(characteristic, value);
            } else {
                Log.e(Constants.TAG_BLUETOOTH, "Characteristic read failed with status: " + status);
            }
        }
    };

    /**
     * Processes data received from a characteristic
     * @param characteristic The characteristic from which data was received
     * @param value The raw value as a byte array
     */
    private void processCharacteristicData(BluetoothGattCharacteristic characteristic, byte[] value) {
        // Check which characteristic this is
        UUID characteristicUuid = characteristic.getUuid();
        String sensorType = null;
        float sensorValue = 0.0f;
        
        try {
            if (value == null || value.length == 0) {
                Log.e(Constants.TAG_BLUETOOTH, "Received null or empty value");
                return;
            }
            
            // Process as JSON string (hardware now uses JSON)
            String stringValue = new String(value, StandardCharsets.UTF_8).trim();
            Log.d(Constants.TAG_BLUETOOTH, "Received value: " + stringValue);
            boolean dataProcessed = false;
            
            // Try to parse as JSON (hardware's format)
            try {
                JSONObject json = new JSONObject(stringValue);
                
                // Hardware team's format (messageType / data)
                if (json.has("messageType") && json.has("data")) {
                    String messageType = json.getString("messageType");
                    sensorValue = (float) json.getDouble("data");
                    
                    if (messageType.equals(Constants.MESSAGE_TYPE_DUST)) {
                        sensorType = "dust";
                    } else if (messageType.equals(Constants.MESSAGE_TYPE_SOUND)) {
                        sensorType = "noise";
                    } else {
                        Log.e(Constants.TAG_BLUETOOTH, "Unknown message type: " + messageType);
                        return;
                    }
                    
                    dataProcessed = true;
                }
            } catch (JSONException e) {
                // Not a valid JSON format, try as plain number
                Log.d(Constants.TAG_BLUETOOTH, "Not a valid JSON: " + e.getMessage());
                try {
                    sensorValue = Float.parseFloat(stringValue);
                    dataProcessed = true;
                } catch (NumberFormatException nfe) {
                    Log.e(Constants.TAG_BLUETOOTH, "Value is neither valid JSON nor a number: " + stringValue);
                    return;
                }
            }
            
            if (!dataProcessed) {
                Log.e(Constants.TAG_BLUETOOTH, "Could not process data in any known format");
                return;
            }
            
            // Determine sensor type based on characteristic UUID if not already set
            if (sensorType == null) {
                if (characteristicUuid.equals(Constants.DUST_CHARACTERISTIC_UUID)) {
                    sensorType = "dust";
                } else if (characteristicUuid.equals(Constants.SOUND_CHARACTERISTIC_UUID)) {
                    sensorType = "noise";
                } else {
                    Log.e(Constants.TAG_BLUETOOTH, "Unknown characteristic UUID: " + characteristicUuid);
                    return;
                }
            }
            
            // Validate the data
            if (!isValidSensorValue(sensorType, sensorValue)) {
                Log.w(Constants.TAG_BLUETOOTH, "Invalid sensor value: " + sensorValue + " for type " + sensorType);
                return;
            }
            
            // Create sensor data and notify the app
            SensorData data = new SensorData(sensorType, sensorValue);
            viewModel.handleNewData(data);
            
            // Log threshold crossings
            if (sensorType.equals("dust") && sensorValue > Constants.DUST_THRESHOLD) {
                Log.d(Constants.TAG_BLUETOOTH, "Dust threshold exceeded: " + sensorValue);
            } else if (sensorType.equals("noise") && sensorValue > Constants.NOISE_THRESHOLD) {
                Log.d(Constants.TAG_BLUETOOTH, "Noise threshold exceeded: " + sensorValue);
            }
        } catch (Exception e) {
            Log.e(Constants.TAG_BLUETOOTH, "Error processing characteristic data: " + e.getMessage());
        }
    }
}