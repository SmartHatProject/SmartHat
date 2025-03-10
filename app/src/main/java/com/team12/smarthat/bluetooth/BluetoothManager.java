package com.team12.smarthat.bluetooth;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.bluetooth.le.ScanRecord;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.os.ParcelUuid;

import androidx.core.content.ContextCompat;

import com.team12.smarthat.utils.Constants;
import com.team12.smarthat.viewmodels.BluetoothViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class BluetoothManager {
    // Initialize all fields with default values to avoid null pointer issues
    private BluetoothAdapter bluetoothAdapter = null;
    private final Context context;
    private BluetoothLeScanner bluetoothLeScanner = null;
    private boolean scanning = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothGatt bluetoothGatt = null;
    private BluetoothViewModel bluetoothViewModel = null;
    
    // callbacks so we can return to service
    private BluetoothGattCallback gattCallback = null;
    private ScanCallback scanCallback = null;
    private BluetoothDevice targetDevice = null;

    // monitor bt state change
    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 
                        BluetoothAdapter.ERROR);
                handleBluetoothStateChange(state);
            }
        }
    };

    // Add BleOperationQueue as a field
    private final BleOperationQueue operationQueue = new BleOperationQueue();
    private int scanRetryCount = 0;
    private static final int MAX_SCAN_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    public BluetoothManager(Context context) {
        this.context = context;
        
        if (context == null) {
            Log.e(Constants.TAG_BLUETOOTH, "Context is null in BluetoothManager constructor!");
            return;
        }
        
        try {
            this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            
            if (bluetoothAdapter == null) {
                Log.e(Constants.TAG_BLUETOOTH, "Bluetooth is not supported on this device");
                return;
            }
            
            // Initialize scanner with permission check
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) 
                        == PackageManager.PERMISSION_GRANTED) {
                    this.bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
                    Log.d(Constants.TAG_BLUETOOTH, "BluetoothLeScanner initialized successfully");
                } else {
                    Log.e(Constants.TAG_BLUETOOTH, "Cannot initialize BluetoothLeScanner - BLUETOOTH_SCAN permission not granted");
                }
            } else {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) 
                        == PackageManager.PERMISSION_GRANTED) {
                    this.bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
                    Log.d(Constants.TAG_BLUETOOTH, "BluetoothLeScanner initialized successfully");
                } else {
                    Log.e(Constants.TAG_BLUETOOTH, "Cannot initialize BluetoothLeScanner - BLUETOOTH permission not granted");
                }
            }
            
            // register receiver for bt state change
            IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            context.registerReceiver(bluetoothStateReceiver, filter);
        } catch (SecurityException se) {
            Log.e(Constants.TAG_BLUETOOTH, "Security exception during BluetoothManager initialization: " + se.getMessage());
        } catch (Exception e) {
            Log.e(Constants.TAG_BLUETOOTH, "Error initializing BluetoothManager: " + e.getMessage());
        }
    }
    
// getcontext permission check return context bt manager
    public Context getContext() {
        return context;
    }

    // basic bluetooth checks
    public boolean isBluetoothSupported() {
        return bluetoothAdapter != null;
    }

    public boolean isBluetoothEnabled() {
        try {
            // Check that we have Bluetooth adapter
            if (bluetoothAdapter == null) {
                return false;
            }
            
            // For Android 12+, we need to check BLUETOOTH_CONNECT permission
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) 
                        != PackageManager.PERMISSION_GRANTED) {
                    Log.e(Constants.TAG_BLUETOOTH, "BLUETOOTH_CONNECT permission not granted");
                    return false;
                }
            } else {
                // For older Android versions, check BLUETOOTH permission
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) 
                        != PackageManager.PERMISSION_GRANTED) {
                    Log.e(Constants.TAG_BLUETOOTH, "BLUETOOTH permission not granted");
                    return false;
                }
            }
            
            // Check if Bluetooth is enabled
            return bluetoothAdapter.isEnabled();
        } catch (Exception e) {
            Log.e(Constants.TAG_BLUETOOTH, "Error checking Bluetooth state: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get device by MAC address from Constants
     * @return The BluetoothDevice if found, null otherwise
     * @throws SecurityException if permission is denied
     */
    @Nullable
    public BluetoothDevice getDeviceByMac() throws SecurityException {
        try {
            return bluetoothAdapter.getRemoteDevice(Constants.ESP32_MAC_ADDRESS);
        } catch (IllegalArgumentException e) {
            Log.e(Constants.TAG_BLUETOOTH, "Invalid MAC: " + Constants.ESP32_MAC_ADDRESS);
            return null;
        }
    }

    // check if paired
    public boolean isDevicePaired() {

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) 
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("BLUETOOTH_CONNECT permission not granted");
        }
        
        for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
            if (device.getAddress().equals(Constants.ESP32_MAC_ADDRESS)) {
                return true;
            }
        }
        return false;
    }

   //might change later
    public BluetoothDevice getPairedDevice() throws SecurityException {
        return getDeviceByMac();
    }

    // new methods for ble
    public void setViewModel(BluetoothViewModel bluetoothViewModel) {
        this.bluetoothViewModel = bluetoothViewModel;
        Log.d(Constants.TAG_BLUETOOTH, "ViewModel set in BluetoothManager");
    }
    
    public void setGattCallback(BluetoothGattCallback callback) {
        this.gattCallback = callback;
    }
    
    /**
     * Connect to a BLE device with proper permission handling and retry mechanism
     */
    public BluetoothGatt connectToBleDevice(BluetoothDevice device) {
        // Queue the operation and return the result
        operationQueue.queue(() -> {
            if (device == null) {
                Log.e(Constants.TAG_BLUETOOTH, "Cannot connect to null device");
                operationQueue.operationComplete();
                return;
            }
            
            try {
                Log.i(Constants.TAG_BLUETOOTH, "Connecting to device: " + device.getAddress());
                
                // Stop any ongoing scan before connecting
                stopScan();
                
                // Create connection
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // Use TRANSPORT_LE for Android 6.0+
                    bluetoothGatt = device.connectGatt(context, false, gattCallback, 
                            BluetoothDevice.TRANSPORT_LE);
                } else {
                    // Fallback for older Android versions
                    bluetoothGatt = device.connectGatt(context, false, gattCallback);
                }
                
                // Set target device reference
                targetDevice = device;
                
                if (bluetoothGatt == null) {
                    Log.e(Constants.TAG_BLUETOOTH, "Failed to connect - GATT is null");
                    updateViewModelSafely(() -> {
                        bluetoothViewModel.handleError("Failed to connect to device");
                        bluetoothViewModel.updateConnectionState(Constants.STATE_DISCONNECTED);
                    });
                    operationQueue.operationComplete();
                }
            } catch (SecurityException se) {
                Log.e(Constants.TAG_BLUETOOTH, "Security exception during connection: " + se.getMessage(), se);
                updateViewModelSafely(() -> {
                    bluetoothViewModel.handleError("Permission denied for connection: " + se.getMessage());
                    bluetoothViewModel.updateConnectionState(Constants.STATE_DISCONNECTED);
                });
                operationQueue.operationComplete();
            } catch (Exception e) {
                Log.e(Constants.TAG_BLUETOOTH, "Error connecting: " + e.getMessage(), e);
                updateViewModelSafely(() -> {
                    bluetoothViewModel.handleError("Error connecting: " + e.getMessage());
                    bluetoothViewModel.updateConnectionState(Constants.STATE_DISCONNECTED);
                });
                operationQueue.operationComplete();
            }
        });
        
        return bluetoothGatt;
    }
    
    /**
     * Create a ScanCallback for BLE device discovery
     * @return The ScanCallback that validates service UUID
     */
    private ScanCallback createScanCallback() {
        return new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                try {
                    BluetoothDevice device = result.getDevice();
                    
                    if (device == null) {
                        Log.e(Constants.TAG_BLUETOOTH, "Scan result had null device");
                        return;
                    }

                    String deviceName = null;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (ContextCompat.checkSelfPermission(context, 
                                Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                            deviceName = device.getName();
                        } else {
                            Log.e(Constants.TAG_BLUETOOTH, "Bluetooth connect permission not granted for accessing device name");
                            bluetoothViewModel.handleError("Permission issue when scanning");
                            return;
                        }
                    } else {
                        deviceName = device.getName();
                    }
                    
                    // Check if this device has our service UUID in its advertising packet
                    boolean hasTargetService = false;
                    
                    // First check service UUIDs in scan record
                    ScanRecord scanRecord = result.getScanRecord();
                    if (scanRecord != null) {
                        List<ParcelUuid> serviceUuids = scanRecord.getServiceUuids();
                        if (serviceUuids != null) {
                            for (ParcelUuid uuid : serviceUuids) {
                                Log.d(Constants.TAG_BLUETOOTH, "Service in adv packet: " + uuid.toString());
                                if (uuid.getUuid().equals(Constants.SERVICE_UUID)) {
                                    Log.i(Constants.TAG_BLUETOOTH, "Found matching service UUID in advertising packet!");
                                    hasTargetService = true;
                                    break;
                                }
                            }
                        }
                    }

                    // Device name match for our SmartHat device
                    boolean isSmartHatDevice = deviceName != null && deviceName.contains("SmartHat");
                    boolean isESP32Device = deviceName != null && deviceName.contains("ESP32");
                    
                    // PRIORITY ORDER:
                    // 1. Has matching Service UUID
                    // 2. Has SmartHat in name (primary device name)
                    // 3. Has ESP32 in name (fallback for compatibility)
                    // 4. MAC address matching (final fallback)
                    
                    // Enhanced device detection logic: Prioritize UUID matching
                    if (hasTargetService) {
                        Log.i(Constants.TAG_BLUETOOTH, "SmartHat device found by SERVICE UUID!");
                        stopScan();
                        targetDevice = device;
                        connectToBleDevice(device);
                    }
                    // Primary device name check (SmartHat)
                    else if (isSmartHatDevice) {
                        Log.i(Constants.TAG_BLUETOOTH, "SmartHat device found by NAME!");
                        stopScan();
                        targetDevice = device;
                        connectToBleDevice(device);
                    }
                    // Secondary device name check (ESP32 - fallback for compatibility)
                    else if (isESP32Device) {
                        Log.i(Constants.TAG_BLUETOOTH, "ESP32 device found by NAME (fallback)!");
                        stopScan();
                        targetDevice = device;
                        connectToBleDevice(device);
                    }
                    // Final fallback to MAC address
                    else if (device.getAddress().equals(Constants.ESP32_MAC_ADDRESS)) {
                        Log.i(Constants.TAG_BLUETOOTH, "SmartHat device found by MAC address (final fallback)!");
                        stopScan();
                        targetDevice = device;
                        connectToBleDevice(device);
                    }
                    
                    // Log device info for debugging regardless of match
                    if (Constants.ENABLE_SERVICE_DISCOVERY_DEBUG) {
                        Log.d(Constants.TAG_BLUETOOTH, "Scanned device: " + (deviceName != null ? deviceName : "Unknown"));
                        Log.d(Constants.TAG_BLUETOOTH, "Device address: " + device.getAddress());
                        Log.d(Constants.TAG_BLUETOOTH, "Has target service in adv: " + hasTargetService);
                        Log.d(Constants.TAG_BLUETOOTH, "Signal strength (RSSI): " + result.getRssi() + " dBm");
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
                String errorMsg = "Scan failed with error: ";
                
                switch (errorCode) {
                    case ScanCallback.SCAN_FAILED_ALREADY_STARTED:
                        errorMsg += "scan already started";
                        break;
                    case ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                        errorMsg += "application registration failed";
                        break;
                    case ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED:
                        errorMsg += "feature unsupported";
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
    }

    /**
     * Scan for BLE devices
     * @param callback The callback to handle scan results
     */
    public void scanForDevices(ScanCallback callback) {
        // Use our enhanced callback for validating service UUID if none provided
        this.scanCallback = callback != null ? callback : createScanCallback();
        scanRetryCount = 0;
        
        // Queue the scan operation to avoid race conditions
        operationQueue.queue(() -> {
            if (scanning) {
                Log.d(Constants.TAG_BLUETOOTH, "Scan already in progress, stopping first");
                stopScan();
                // Small delay to prevent "scanning too frequently" errors
                handler.postDelayed(this::startScanWithRetry, 100);
            } else {
                startScanWithRetry();
            }
        });
    }

    /**
     * Verify if all required Bluetooth permissions are granted for Android 12+
     * @param context The context to check permissions
     * @return true if all required permissions are granted, false otherwise
     */
    public static boolean hasRequiredBluetoothPermissions(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == 
                   PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == 
                   PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == 
                   PackageManager.PERMISSION_GRANTED;
        }
    }

    /**
     * Start BLE scan with retry mechanism
     */
    private void startScanWithRetry() {
        if (!isBluetoothEnabled()) {
            Log.e(Constants.TAG_BLUETOOTH, "Bluetooth is not enabled");
            updateViewModelSafely(() -> bluetoothViewModel.handleError("Bluetooth is not enabled. Please enable Bluetooth and try again."));
            operationQueue.operationComplete();
            return;
        }

        // Initialize scanner if needed
        if (bluetoothLeScanner == null) {
            initializeScanner();
        }
        
        if (bluetoothLeScanner == null) {
            Log.e(Constants.TAG_BLUETOOTH, "BluetoothLeScanner is null after initialization attempt");
            updateViewModelSafely(() -> bluetoothViewModel.handleError("Bluetooth scanner not available"));
            operationQueue.operationComplete();
            return;
        }

        // Check required permissions - more thorough check for Android 12+
        if (!hasRequiredBluetoothPermissions(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Log.e(Constants.TAG_BLUETOOTH, "Android 12+: Missing BLUETOOTH_SCAN or BLUETOOTH_CONNECT permissions");
                updateViewModelSafely(() -> {
                    bluetoothViewModel.handleError("Android 12+ requires Bluetooth scan and connect permissions. Please grant these permissions in settings.");
                    bluetoothViewModel.updateConnectionState(Constants.STATE_DISCONNECTED);
                });
            } else {
                Log.e(Constants.TAG_BLUETOOTH, "LOCATION permission not granted - required for BLE scanning on Android 11 and below");
                updateViewModelSafely(() -> {
                    bluetoothViewModel.handleError("Location permission is required for scanning on this Android version.");
                    bluetoothViewModel.updateConnectionState(Constants.STATE_DISCONNECTED);
                });
            }
            operationQueue.operationComplete();
            return;
        }

        try {
            // Create scan filters and settings
            List<ScanFilter> filters = new ArrayList<>();
            
            // Add service UUID filter - prioritize UUID-based scanning over MAC address
            ScanFilter serviceFilter = new ScanFilter.Builder()
                    .setServiceUuid(new ParcelUuid(Constants.SERVICE_UUID))
                    .build();
            filters.add(serviceFilter);
            
            Log.d(Constants.TAG_BLUETOOTH, "Starting BLE scan with Service UUID filter: " + Constants.SERVICE_UUID);
            
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
                    
            Log.d(Constants.TAG_BLUETOOTH, "Starting BLE scan with all permissions verified");
            bluetoothLeScanner.startScan(filters, settings, scanCallback);
            scanning = true;
            Log.d(Constants.TAG_BLUETOOTH, "BLE scan started successfully");
            scanRetryCount = 0;  // Reset retry count on success
            
            // Set scan timeout
            handler.postDelayed(() -> {
                if (scanning) {
                    Log.d(Constants.TAG_BLUETOOTH, "Scan timeout reached, stopping scan");
                    stopScan();
                }
            }, Constants.SCAN_PERIOD);
            
            // Signal operation complete to process any queued operations
            operationQueue.operationComplete();
        } catch (SecurityException se) {
            // Handle permission issues explicitly
            Log.e(Constants.TAG_BLUETOOTH, "Security exception during scan: " + se.getMessage(), se);
            
            if (scanRetryCount < MAX_SCAN_RETRIES) {
                scanRetryCount++;
                Log.d(Constants.TAG_BLUETOOTH, "Retrying scan (attempt " + scanRetryCount + ")");
                handler.postDelayed(this::startScanWithRetry, RETRY_DELAY_MS);
            } else {
                updateViewModelSafely(() -> {
                    bluetoothViewModel.handleError("Permission denied for scanning after " + MAX_SCAN_RETRIES + " attempts: " + se.getMessage());
                    bluetoothViewModel.updateConnectionState(Constants.STATE_DISCONNECTED);
                });
                scanning = false;
                operationQueue.operationComplete();
            }
        } catch (Exception e) {
            // Handle other errors
            String errorMsg = "Scan failed: " + e.getMessage();
            Log.e(Constants.TAG_BLUETOOTH, errorMsg, e);
            
            if (scanRetryCount < MAX_SCAN_RETRIES) {
                scanRetryCount++;
                Log.d(Constants.TAG_BLUETOOTH, "Retrying scan (attempt " + scanRetryCount + ")");
                handler.postDelayed(this::startScanWithRetry, RETRY_DELAY_MS);
            } else {
                updateViewModelSafely(() -> {
                    bluetoothViewModel.handleError(errorMsg);
                    bluetoothViewModel.updateConnectionState(Constants.STATE_DISCONNECTED);
                });
                scanning = false;
                operationQueue.operationComplete();
            }
        }
    }
    
    /**
     * Stop BLE scanning with proper permission handling and notify queue
     */
    public void stopScan() {
        operationQueue.queue(() -> {
            if (scanning && bluetoothLeScanner != null && scanCallback != null) {
                try {
                    // Double-check permission before stopping scan
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) 
                                != PackageManager.PERMISSION_GRANTED) {
                            Log.e(Constants.TAG_BLUETOOTH, "BLUETOOTH_SCAN permission not granted - cannot stop scan");
                            operationQueue.operationComplete();
                            return;
                        }
                    }
                    
                    // Stop scan
                    bluetoothLeScanner.stopScan(scanCallback);
                    Log.d(Constants.TAG_BLUETOOTH, "BLE scan stopped");
                    scanning = false;
                } catch (SecurityException se) {
                    // Handle permission issues explicitly
                    Log.e(Constants.TAG_BLUETOOTH, "Security exception stopping scan: " + se.getMessage(), se);
                } catch (Exception e) {
                    // Handle other errors
                    Log.e(Constants.TAG_BLUETOOTH, "Error stopping scan: " + e.getMessage(), e);
                } finally {
                    operationQueue.operationComplete();
                }
            } else {
                Log.d(Constants.TAG_BLUETOOTH, "No scan to stop or scanner not available");
                operationQueue.operationComplete();
            }
        });
    }
    
    public void disconnect() {
        if (bluetoothGatt != null) {
            try {
                // Check permission for Android 12+
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) 
                            != PackageManager.PERMISSION_GRANTED) {
                        Log.e(Constants.TAG_BLUETOOTH, "BLUETOOTH_CONNECT permission not granted - cannot disconnect");
                        if (bluetoothViewModel != null) {
                            bluetoothViewModel.handleError("Permission issue when disconnecting");
                        }
                        return;
                    }
                }
                
                bluetoothGatt.disconnect();
                Log.d(Constants.TAG_BLUETOOTH, "Disconnected from GATT server");
            } catch (SecurityException se) {
                Log.e(Constants.TAG_BLUETOOTH, "Security exception in disconnect: " + se.getMessage());
                if (bluetoothViewModel != null) {
                    bluetoothViewModel.handleError("Permission denied when disconnecting");
                }
            } catch (Exception e) {
                Log.e(Constants.TAG_BLUETOOTH, "Error during disconnect: " + e.getMessage());
            }
        }
    }
    
    public void setBluetoothGatt(BluetoothGatt gatt) {
        try {
            // no permission needed storing a reference
            // potential SecurityException check just in case
            this.bluetoothGatt = gatt;
        } catch (SecurityException e) {
            Log.e(Constants.TAG_BLUETOOTH, "security exception in setBluetoothGatt: " + e.getMessage());
            if (bluetoothViewModel != null) {
                bluetoothViewModel.handleError("permission denied when managing bluetooth connection");
            }

        }
    }

    /**
     * cleanup
     */
    public void cleanup() {
        try {
            // Unregister Bluetooth state receiver
            if (context != null) {
                context.unregisterReceiver(bluetoothStateReceiver);
            }
        } catch (Exception e) {
            Log.e(Constants.TAG_BLUETOOTH, "Error unregistering receiver: " + e.getMessage());
        }
    }

   //bt adapter state change handle
    private void handleBluetoothStateChange(int state) {
        switch (state) {
            case BluetoothAdapter.STATE_OFF:
                Log.d(Constants.TAG_BLUETOOTH, "bluetooth turned off");
                if (bluetoothViewModel != null) {
                    bluetoothViewModel.updateConnectionState(Constants.STATE_DISCONNECTED);
                    bluetoothViewModel.handleError("bluetooth turned off");
                    
                    // force disc any active connection
                    disconnect();
                }
                break;
                
            case BluetoothAdapter.STATE_TURNING_OFF:
                Log.d(Constants.TAG_BLUETOOTH, "Bluetooth turning OFF");
                // disc prep
                if (bluetoothViewModel != null) {
                    bluetoothViewModel.updateConnectionState(Constants.STATE_DISCONNECTED);
                }
                break;
                
            case BluetoothAdapter.STATE_ON:
                Log.d(Constants.TAG_BLUETOOTH, "bluetooth turned on");
                // Reinitialize the scanner when Bluetooth is turned on
                initializeScanner();
                break;
                
            case BluetoothAdapter.STATE_TURNING_ON:
                Log.d(Constants.TAG_BLUETOOTH, "Bluetooth turning ON");
                break;
        }
    }
    
    /**
     * Initialize scanner if needed
     */
    public void initializeScanner() {
        if (bluetoothAdapter == null) {
            Log.e(Constants.TAG_BLUETOOTH, "Cannot initialize scanner - BluetoothAdapter is null");
            return;
        }
        
        if (!bluetoothAdapter.isEnabled()) {
            Log.e(Constants.TAG_BLUETOOTH, "Cannot initialize scanner - Bluetooth is disabled");
            return;
        }
        
        try {
            // Check appropriate permissions based on Android version
            boolean hasPermission = false;
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                hasPermission = ContextCompat.checkSelfPermission(context, 
                        Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
                
                if (!hasPermission) {
                    Log.e(Constants.TAG_BLUETOOTH, "Cannot initialize scanner - BLUETOOTH_SCAN permission not granted");
                    return;
                }
            } else {
                hasPermission = ContextCompat.checkSelfPermission(context, 
                        Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED;
                
                if (!hasPermission) {
                    Log.e(Constants.TAG_BLUETOOTH, "Cannot initialize scanner - BLUETOOTH permission not granted");
                    return;
                }
            }
            
            // Initialize the scanner
            this.bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            
            if (this.bluetoothLeScanner != null) {
                Log.d(Constants.TAG_BLUETOOTH, "BluetoothLeScanner successfully initialized");
            } else {
                Log.e(Constants.TAG_BLUETOOTH, "Failed to get BluetoothLeScanner even with permissions");
            }
        } catch (SecurityException se) {
            Log.e(Constants.TAG_BLUETOOTH, "Security exception initializing scanner: " + se.getMessage());
        }
    }

    /**
     * Direct connection method for Android 12 demo purposes
     * This will attempt to connect to any ESP32 device that's already paired
     */
    public boolean connectToAnyPairedESP32() {
        Log.d(Constants.TAG_BLUETOOTH, "DEMO: Attempting direct connection to any paired ESP32 device");
        
        try {
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
                Log.e(Constants.TAG_BLUETOOTH, "Bluetooth not enabled for direct connection");
                return false;
            }
            
            // Check for BLUETOOTH_CONNECT permission on Android 12+
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) 
                        != PackageManager.PERMISSION_GRANTED) {
                    Log.e(Constants.TAG_BLUETOOTH, "BLUETOOTH_CONNECT permission not granted for direct connection");
                    return false;
                }
            }
            
            // Get all paired devices
            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            
            if (pairedDevices.size() > 0) {
                Log.d(Constants.TAG_BLUETOOTH, "Found " + pairedDevices.size() + " paired devices");
                
                // Look for any ESP32 device in the paired devices
                for (BluetoothDevice device : pairedDevices) {
                    String deviceName = device.getName();
                    
                    if (deviceName != null && (
                            deviceName.contains("ESP32") || 
                            deviceName.contains("SmartHat") || 
                            deviceName.contains("esp") || 
                            deviceName.contains("BLE"))) {
                        
                        Log.d(Constants.TAG_BLUETOOTH, "DEMO: Found target device: " + deviceName + " (" + device.getAddress() + ")");
                        
                        // Return the device to the service
                        if (scanCallback != null) {
                            targetDevice = device;
                            return true;
                        }
                    }
                }
            } else {
                Log.d(Constants.TAG_BLUETOOTH, "No paired devices found");
            }
            
            return false;
            
        } catch (SecurityException e) {
            Log.e(Constants.TAG_BLUETOOTH, "Security exception in direct connection: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get the target device from last scan
     * @return The target device or null if none found
     */
    @Nullable
    public BluetoothDevice getTargetDevice() {
        return targetDevice;
    }

    /**
     * Get the BLE operation queue for this manager
     * @return The operation queue
     */
    @NonNull
    public BleOperationQueue getOperationQueue() {
        return operationQueue;
    }
    
    /**
     * Start a high-power BLE scan
     * @param callback Callback to handle scan results
     */
    public void startHighPowerScan(ScanCallback callback) {
        this.scanCallback = callback;
        
        // make sure bt works
        if (bluetoothAdapter == null || bluetoothLeScanner == null) {
            updateViewModelSafely(() -> bluetoothViewModel.handleError("Bluetooth not initialized properly for Pixel scan"));
            return;
        }
        
        // check scan permission (only essential for Pixel)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) 
                != PackageManager.PERMISSION_GRANTED) {
            updateViewModelSafely(() -> bluetoothViewModel.handleError("Scan permission needed for Pixel 4A"));
            return;
        }
        
        if (!scanning) {
            // stop scan after longer timeout for demo
            handler.postDelayed(this::stopScan, 20000); // 20 seconds instead of usual 10
            
            scanning = true;
            
            // HIGH POWER mode for best results during the demo
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // Higher power, faster results
                    .setReportDelay(0) // Report immediately
                    .build();
                    
            // No filters - find all devices for demo
            List<ScanFilter> filters = new ArrayList<>();
            
            try {
                Log.d(Constants.TAG_BLUETOOTH, "Starting HIGH POWER BLE scan for Pixel 4A demo");
                bluetoothLeScanner.startScan(filters, settings, scanCallback);
                Log.d(Constants.TAG_BLUETOOTH, "High power scan started for Pixel demo");
            } catch (Exception e) {
                updateViewModelSafely(() -> bluetoothViewModel.handleError("Scan error: " + e.getMessage()));
                Log.e(Constants.TAG_BLUETOOTH, "Error in high power scan: " + e.getMessage(), e);
                scanning = false;
            }
        } else {
            scanning = false;
            bluetoothLeScanner.stopScan(scanCallback);
            Log.d(Constants.TAG_BLUETOOTH, "Stopped existing scan to start high power scan");
            
            // Try again
            handler.postDelayed(() -> startHighPowerScan(callback), 500);
        }
    }

    /**
     * Helper method to ensure ViewModel updates happen on the main thread
     * @param action The action to perform with the ViewModel
     */
    private void updateViewModelSafely(Runnable action) {
        if (bluetoothViewModel == null) {
            Log.e(Constants.TAG_BLUETOOTH, "Cannot update ViewModel - not initialized");
            return;
        }
        
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // Already on main thread
            action.run();
        } else {
            // Post to main thread
            new Handler(Looper.getMainLooper()).post(action);
        }
    }

    /**
     * Get device by MAC address for Android 12 with permission check
     */
    public BluetoothDevice getDeviceByMacAddress(String macAddress) {
        try {
            if (bluetoothAdapter == null) {
                Log.e(Constants.TAG_BLUETOOTH, "BluetoothAdapter is null");
                return null;
            }
            
            // Check BLUETOOTH_CONNECT permission (required for Android 12)
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) 
                    != PackageManager.PERMISSION_GRANTED) {
                Log.e(Constants.TAG_BLUETOOTH, "BLUETOOTH_CONNECT permission not granted");
                if (bluetoothViewModel != null) {
                    bluetoothViewModel.handleError("Bluetooth permission not granted");
                }
                return null;
            }
            
            // Get device directly by MAC address
            return bluetoothAdapter.getRemoteDevice(macAddress);
        } catch (Exception e) {
            Log.e(Constants.TAG_BLUETOOTH, "Error getting device by MAC: " + e.getMessage());
            if (bluetoothViewModel != null) {
                bluetoothViewModel.handleError("Error finding device: " + e.getMessage());
            }
            return null;
        }
    }

    /**
     * Find a device by name with proper permission handling
     */
    public BluetoothDevice findDeviceByName() {
        try {
            if (bluetoothAdapter == null) {
                Log.e(Constants.TAG_BLUETOOTH, "BluetoothAdapter is null");
                return null;
            }
            
            // Check BLUETOOTH_CONNECT permission for Android 12+
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) 
                        != PackageManager.PERMISSION_GRANTED) {
                    Log.e(Constants.TAG_BLUETOOTH, "BLUETOOTH_CONNECT permission not granted - cannot find device by name");
                    if (bluetoothViewModel != null) {
                        bluetoothViewModel.handleError("Bluetooth permission not granted");
                    }
                    return null;
                }
            }
            
            // Get all paired devices
            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            
            if (pairedDevices.isEmpty()) {
                Log.d(Constants.TAG_BLUETOOTH, "No paired devices found");
                return null;
            }
            
            Log.d(Constants.TAG_BLUETOOTH, "Searching among " + pairedDevices.size() + " paired devices");
            
            // Priority order search
            for (BluetoothDevice device : pairedDevices) {
                try {
                    String name = device.getName();
                    if (name == null) continue;
                    
                    Log.d(Constants.TAG_BLUETOOTH, "Checking device: " + name);
                    
                    // Match criteria
                    if (name.equals("SmartHat") || name.contains("SmartHat")) {
                        Log.d(Constants.TAG_BLUETOOTH, "Found SmartHat device: " + name);
                        return device;
                    } else if (name.contains("ESP32")) {
                        Log.d(Constants.TAG_BLUETOOTH, "Found ESP32 device: " + name);
                        return device;
                    } else if (name.contains("BLE")) {
                        Log.d(Constants.TAG_BLUETOOTH, "Found BLE device: " + name);
                        return device;
                    }
                } catch (SecurityException se) {
                    Log.e(Constants.TAG_BLUETOOTH, "Security exception getting device name: " + se.getMessage());
                } catch (Exception e) {
                    Log.e(Constants.TAG_BLUETOOTH, "Error processing device: " + e.getMessage());
                }
            }
            
            Log.d(Constants.TAG_BLUETOOTH, "No matching device found by name");
            return null;
        } catch (SecurityException se) {
            Log.e(Constants.TAG_BLUETOOTH, "Security exception in findDeviceByName: " + se.getMessage());
            if (bluetoothViewModel != null) {
                bluetoothViewModel.handleError("Permission denied when finding device");
            }
            return null;
        } catch (Exception e) {
            Log.e(Constants.TAG_BLUETOOTH, "Error finding device by name: " + e.getMessage());
            return null;
        }
    }

    /**
     * Scan specifically for your SmartHat ESP32 device and connect when found
     * This improved scan will look for devices by both MAC address and common ESP32 names
     */
    public void scanAndConnect() {
        Log.d(Constants.TAG_BLUETOOTH, "Starting advanced scan for ESP32 device");
        Log.d(Constants.TAG_BLUETOOTH, "Target MAC: " + Constants.ESP32_MAC_ADDRESS);
        Log.d(Constants.TAG_BLUETOOTH, "Android version: " + Build.VERSION.SDK_INT + 
              ", Manufacturer: " + Build.MANUFACTURER + 
              ", Model: " + Build.MODEL);
        
        if (bluetoothViewModel != null) {
            bluetoothViewModel.updateConnectionState(Constants.STATE_CONNECTING);
        }
        
        try {
            // Verify Bluetooth is available and enabled
            if (bluetoothAdapter == null) {
                Log.e(Constants.TAG_BLUETOOTH, "BluetoothAdapter is null - Bluetooth not available");
                if (bluetoothViewModel != null) {
                    bluetoothViewModel.handleError("Bluetooth not available on this device");
                    bluetoothViewModel.updateConnectionState(Constants.STATE_DISCONNECTED);
                }
                return;
            }
            
            if (!bluetoothAdapter.isEnabled()) {
                Log.e(Constants.TAG_BLUETOOTH, "BluetoothAdapter is not enabled");
                if (bluetoothViewModel != null) {
                    bluetoothViewModel.handleError("Bluetooth is not enabled. Please enable Bluetooth and try again.");
                    bluetoothViewModel.updateConnectionState(Constants.STATE_DISCONNECTED);
                }
                return;
            }
            
            // Verify scanner is available
            if (bluetoothLeScanner == null) {
                Log.e(Constants.TAG_BLUETOOTH, "BluetoothLeScanner is null - reinitializing");
                initializeScanner();
                
                if (bluetoothLeScanner == null) {
                    Log.e(Constants.TAG_BLUETOOTH, "Failed to initialize BluetoothLeScanner");
                    if (bluetoothViewModel != null) {
                        bluetoothViewModel.handleError("Bluetooth scan not available on this device");
                        bluetoothViewModel.updateConnectionState(Constants.STATE_DISCONNECTED);
                    }
                    return;
                }
            }
            
            // Check scan permission
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) 
                    != PackageManager.PERMISSION_GRANTED) {
                Log.e(Constants.TAG_BLUETOOTH, "BLUETOOTH_SCAN permission not granted");
                if (bluetoothViewModel != null) {
                    bluetoothViewModel.handleError("Bluetooth scan permission not granted. Please grant this permission in settings.");
                    bluetoothViewModel.updateConnectionState(Constants.STATE_DISCONNECTED);
                }
                return;
            }
            
            // Log permission status
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                boolean hasScan = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) 
                        == PackageManager.PERMISSION_GRANTED;
                boolean hasConnect = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) 
                        == PackageManager.PERMISSION_GRANTED;
                boolean hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) 
                        == PackageManager.PERMISSION_GRANTED;
                
                Log.d(Constants.TAG_BLUETOOTH, "Permission status - SCAN: " + hasScan + 
                        ", CONNECT: " + hasConnect + ", LOCATION: " + hasLocation);
            }
            
            // Location permission is not needed with neverForLocation flag in Android 12+
            Log.d(Constants.TAG_BLUETOOTH, "Using neverForLocation flag, no location permission check needed");
            
            // Set a flag to track if we found our device
            final boolean[] deviceFound = {false};
            
            // Configure scan for maximum efficiency to find our ESP32
            List<ScanFilter> filters = new ArrayList<>();
            Log.d(Constants.TAG_BLUETOOTH, "Using empty scan filter list to find all devices");
            
            // Set up scan settings for low latency (faster scan)
            ScanSettings scanSettings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setReportDelay(0) // Get results immediately
                    .build();
            Log.d(Constants.TAG_BLUETOOTH, "Using LOW_LATENCY scan mode with zero delay");
            
            // Create scan callback if not already done
            if (scanCallback == null) {
                scanCallback = new ScanCallback() {
                    @Override
                    public void onScanResult(int callbackType, ScanResult result) {
                        BluetoothDevice device = result.getDevice();
                        if (device == null) return;
                        
                        String deviceAddress = device.getAddress();
                        String deviceName = null;
                        
                        try {
                            if (ContextCompat.checkSelfPermission(context, 
                                    Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                                deviceName = device.getName();
                            }
                        } catch (Exception e) {
                            Log.e(Constants.TAG_BLUETOOTH, "Error getting device name: " + e.getMessage());
                        }
                        
                        // Log every device found - useful for debugging
                        Log.d(Constants.TAG_BLUETOOTH, "Device found: " + deviceAddress + 
                                (deviceName != null ? " (" + deviceName + ")" : "") +
                                ", RSSI: " + result.getRssi());
                        
                        // Check if this is our device - first by exact MAC match
                        boolean isTargetDevice = deviceAddress.equals(Constants.ESP32_MAC_ADDRESS);
                        
                        // If name is available and MAC didn't match, try matching by common ESP32 names
                        if (!isTargetDevice && deviceName != null) {
                            // Common names for ESP32 devices
                            String[] esp32Names = {"SmartHat", "SMARTHAT", "Smart Hat", "ESP32", "ESP-32"};
                            for (String name : esp32Names) {
                                if (deviceName.toUpperCase().contains(name.toUpperCase())) {
                                    Log.d(Constants.TAG_BLUETOOTH, "Found device with ESP32 name pattern: " + deviceName);
                                    isTargetDevice = true;
                                    // If MAC doesn't match but name does, log a warning
                                    if (!deviceAddress.equals(Constants.ESP32_MAC_ADDRESS)) {
                                        Log.w(Constants.TAG_BLUETOOTH, 
                                            "Device MAC " + deviceAddress + 
                                            " doesn't match expected MAC " + Constants.ESP32_MAC_ADDRESS +
                                            " but name suggests it's an ESP32 device");
                                    }
                                    break;
                                }
                            }
                        }
                        
                        // If this is our target device, connect to it
                        if (isTargetDevice) {
                            Log.d(Constants.TAG_BLUETOOTH, "*** FOUND ESP32 SmartHat device! ***");
                            deviceFound[0] = true;
                            stopScan();
                            
                            // Connect to the device
                            targetDevice = device;
                            bluetoothGatt = connectToBleDevice(device);
                        }
                    }
                    
                    @Override
                    public void onScanFailed(int errorCode) {
                        String errorMsg = "BLE scan failed with code: " + errorCode;
                        Log.e(Constants.TAG_BLUETOOTH, errorMsg);
                        
                        // Log additional details about the error
                        String errorDescription = getErrorDescription(errorCode);
                        Log.e(Constants.TAG_BLUETOOTH, "Error description: " + errorDescription);
                        
                        // Log permission status for debugging
                        boolean hasScan = ContextCompat.checkSelfPermission(context, 
                                Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
                        boolean hasConnect = ContextCompat.checkSelfPermission(context, 
                                Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
                        boolean hasLocation = ContextCompat.checkSelfPermission(context, 
                                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                        
                        Log.e(Constants.TAG_BLUETOOTH, "Permission status when scan failed - " +
                                "SCAN: " + hasScan + ", CONNECT: " + hasConnect + ", LOCATION: " + hasLocation);
                        
                        scanning = false;
                        if (bluetoothViewModel != null) {
                            bluetoothViewModel.handleError("Failed to scan for SmartHat: " + errorDescription);
                            bluetoothViewModel.updateConnectionState(Constants.STATE_DISCONNECTED);
                        }
                    }
                };
                Log.d(Constants.TAG_BLUETOOTH, "Created new ScanCallback instance");
            }
            
            // Start the BLE scan with our settings
            Log.d(Constants.TAG_BLUETOOTH, "Beginning active BLE scan for SmartHat ESP32...");
            scanning = true;
            try {
                bluetoothLeScanner.startScan(filters, scanSettings, scanCallback);
                Log.d(Constants.TAG_BLUETOOTH, "Scan started successfully");
            } catch (Exception e) {
                Log.e(Constants.TAG_BLUETOOTH, "Error starting scan: " + e.getMessage(), e);
                if (bluetoothViewModel != null) {
                    bluetoothViewModel.handleError("Error starting Bluetooth scan: " + e.getMessage());
                    bluetoothViewModel.updateConnectionState(Constants.STATE_DISCONNECTED);
                }
                scanning = false;
                return;
            }
            
            // Set a timeout for the scan and handle device not found
            handler.postDelayed(() -> {
                if (scanning) {
                    stopScan();
                    
                    // If our device wasn't found during the scan
                    if (!deviceFound[0] && bluetoothViewModel != null) {
                        bluetoothViewModel.handleError("SmartHat device not found. Please make sure it is turned on and nearby.");
                        bluetoothViewModel.updateConnectionState(Constants.STATE_DISCONNECTED);
                    }
                }
            }, Constants.SCAN_PERIOD);
            
        } catch (SecurityException se) {
            Log.e(Constants.TAG_BLUETOOTH, "Security exception during scan: " + se.getMessage());
            if (bluetoothViewModel != null) {
                bluetoothViewModel.handleError("Permission denied for Bluetooth scan");
                bluetoothViewModel.updateConnectionState(Constants.STATE_DISCONNECTED);
            }
        } catch (Exception e) {
            Log.e(Constants.TAG_BLUETOOTH, "Error during scan: " + e.getMessage());
            if (bluetoothViewModel != null) {
                bluetoothViewModel.handleError("Error scanning for devices: " + e.getMessage());
                bluetoothViewModel.updateConnectionState(Constants.STATE_DISCONNECTED);
            }
        }
    }

    // Helper method to get user-friendly error descriptions
    private String getErrorDescription(int errorCode) {
        switch (errorCode) {
            case ScanCallback.SCAN_FAILED_ALREADY_STARTED:
                return "Another scan is already in progress";
            case ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                return "Application registration failed (this may be due to scanning too frequently)";
            case ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED:
                return "BLE scanning not supported on this device";
            case ScanCallback.SCAN_FAILED_INTERNAL_ERROR:
                return "Internal error (check if Bluetooth is fully enabled)";
            case 5: // SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES on newer Android
                return "Out of hardware resources for scanning";
            case 6: // SCAN_FAILED_SCANNING_TOO_FREQUENTLY on newer Android
                return "Scanning too frequently";
            default:
                return "Unknown error code: " + errorCode + " (check if all Bluetooth permissions are granted)";
        }
    }
}