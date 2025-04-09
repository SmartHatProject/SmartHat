package com.team12.smarthat.bluetooth.core;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.team12.smarthat.bluetooth.devices.esp32.ESP32BluetoothSpec;
import com.team12.smarthat.permissions.BluetoothPermissionManager;
import com.team12.smarthat.utils.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressLint("MissingPermission")
public class BleConnectionManager {
    private static final String TAG = "BleConnectionManager";
    
    // connection states
    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        DISCONNECTING
    }
    
    // singleton instance
    private static BleConnectionManager instance;
    
    // core components
    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final BluetoothPermissionManager permissionManager;
    
    // livedata for state observation (single source of truth)
    private final MutableLiveData<ConnectionState> connectionState = new MutableLiveData<>(ConnectionState.DISCONNECTED);
    private final MutableLiveData<String> connectionError = new MutableLiveData<>();
    
    // ble components
    private BluetoothGatt bluetoothGatt;
    private BluetoothDevice targetDevice;
    private final BleOperationQueue operationQueue = new BleOperationQueue();
    private ScanCallback scanCallback;
    
    // connection parameters (esp32-specific)
    private static final int MAX_RETRIES = ESP32BluetoothSpec.ConnectionParams.MAX_RETRY_COUNT;
    private static final long CONNECTION_TIMEOUT = ESP32BluetoothSpec.ConnectionParams.CONNECTION_TIMEOUT;
    private int connectionRetries = 0;
    
    // listeners
    private final List<ConnectionListener> listeners = new CopyOnWriteArrayList<>();
    
    // characteristic change listener
    private CharacteristicChangeListener characteristicChangeListener;
    
    // store the last successfully connected device for reconnection purposes
    private BluetoothDevice lastConnectedDevice = null;
    
    // scan in progress flag for thread safety
    private final AtomicBoolean isScanning = new AtomicBoolean(false);
    
    // reconnection parameters
    private static final int MAX_RECONNECTION_ATTEMPTS = 5;
    private static final long BASE_RECONNECTION_DELAY = ESP32BluetoothSpec.ConnectionParams.BASE_RECONNECTION_DELAY;
    // Maximum reconnection delay to cap exponential growth
    private static final long MAX_RECONNECTION_DELAY = 60000; // 60 seconds
    private int reconnectionAttempts = 0;
    
    // connection timeout runnable
    private Runnable connectionTimeoutRunnable;
    
    // callback interfaces
    public interface ConnectionListener {
        void onStateChanged(ConnectionState state);
        void onError(String error);
    }
    
    public interface CharacteristicChangeListener {
        void onCharacteristicChanged(BluetoothGattCharacteristic characteristic);
    }
    
    /**
     * callback interface for esp32 scan results
     * simplified and optimized for android 12 on pixel 4a
     */
    public interface ScanResultCallback {
        /**
         * called when an esp32 device is found during a scan
         * @param device the discovered bluetooth device
         * @param result the scan result containing signal strength and advertisement data
         */
        void onDeviceFound(BluetoothDevice device, ScanResult result);
        
        /**
         * called when the scan fails
         * @param errorCode standard android error code from scancallback constants
         * @param errorMessage human-readable error message
         */
        void onScanFailed(int errorCode, String errorMessage);
        
        /**
         * called when the scan times out without finding an esp32 device
         */
        void onScanTimeout();
    }
    
    // protected constructor for singleton and subclasses (like mockbleconnectionmanager)
    protected BleConnectionManager(Context context, BluetoothPermissionManager manager) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        if (manager == null) {
            throw new IllegalArgumentException("BluetoothPermissionManager cannot be null");
        }
        
        this.appContext = context.getApplicationContext();
        this.permissionManager = manager;
        
        // initialize bluetooth adapter once during construction to avoid repeated lookups
        BluetoothManager bluetoothManager = (BluetoothManager) appContext.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            Log.e(TAG, "BluetoothManager not available on this device");
            return;
        }
        
        BluetoothAdapter adapter = bluetoothManager.getAdapter();
        if (adapter == null) {
            Log.e(TAG, "BluetoothAdapter not available on this device");
        }
    }
    
    // public getter for singleton instance
    public static synchronized BleConnectionManager getInstance(Context context, BluetoothPermissionManager manager) {
        if (instance == null) {
            instance = new BleConnectionManager(context, manager);
            Log.d(TAG, "Created new BleConnectionManager instance");
        }
        return instance;
    }
    
    /**
     * helper method to check if bluetooth is available and enabled
     * optimized for android 12 on pixel 4a
     * 
     * @return true if bluetooth is available and enabled
     */
    public boolean isBluetoothReadyToUse() {
        BluetoothManager bluetoothManager = (BluetoothManager) appContext.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            return false;
        }
        
        BluetoothAdapter adapter = bluetoothManager.getAdapter();
        return adapter != null && adapter.isEnabled();
    }
    
    /**
     * helper method to check if required permissions are granted
     * optimized for android 12 on pixel 4a
     * 
     * @return true if all required permissions are granted
     */
    public boolean hasRequiredPermissions() {
        return permissionManager != null && permissionManager.hasRequiredPermissions();
    }
    
    /**
     * update connection state and notify listeners
     */
    protected void updateState(ConnectionState newState) {
        Log.d(TAG, "State changing: " + 
              (connectionState.getValue() != null ? connectionState.getValue().name() : "null") + 
              " -> " + newState.name());
              
        // ensure we are on the main thread for livedata updates
        if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
            mainHandler.post(() -> doUpdateState(newState));
        } else {
            doUpdateState(newState);
        }
    }
    
    /**
     * helper method to perform state update on main thread
     * 
     */
    private void doUpdateState(ConnectionState newState) {
        // update livedata first
        connectionState.setValue(newState);
        
        // don't create new list if there are no listeners
        if (listeners.isEmpty()) return;
        
        // copy list for thread safety optimization
        final List<ConnectionListener> listenersCopy = new ArrayList<>(listeners);
        
        // notify listeners directly on main thread
        for (ConnectionListener listener : listenersCopy) {
            listener.onStateChanged(newState);
        }
    }
    
    /**
     * execute an operation with permission check
     * handles permission verification before ble operation
     * 
     */
    private void executeWithPermissionCheck(Runnable operation, String operationName) {
        try {
            if (!isBluetoothReadyToUse()) {
                Log.e(TAG, "Cannot perform " + operationName + ": Bluetooth not ready");
                reportError("Bluetooth not ready for " + operationName);
                updateState(ConnectionState.DISCONNECTED);
                return;
            }
            
            // for 12+ check bluetooth_connect permission specifically
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!permissionManager.hasBluetoothConnectPermission()) {
                    Log.e(TAG, "Cannot perform " + operationName + ": BLUETOOTH_CONNECT permission not granted");
                    reportError("Permission required for " + operationName);
                    updateState(ConnectionState.DISCONNECTED);
                    return;
                }
            } else {
                // for older android versions permission manager
                if (!hasRequiredPermissions()) {
                    Log.e(TAG, "Cannot perform " + operationName + ": Required permissions not granted");
                    reportError("Permission denied for " + operationName);
                    updateState(ConnectionState.DISCONNECTED);
                    return;
                }
            }
            
            // execute the operation if permission check passes
            operation.run();
        } catch (SecurityException se) {
            // handle securityexception explicitly (could be thrown even if permission check passes)
            Log.e(TAG, "Security exception during " + operationName + ": " + se.getMessage());
            reportError("Permission denied: " + se.getMessage());
            updateState(ConnectionState.DISCONNECTED);
        } catch (Exception e) {
            // catch any other exceptions that might occur
            Log.e(TAG, "Error during " + operationName + ": " + e.getMessage(), e);
            reportError("Error in " + operationName + ": " + e.getMessage());
            // only update state if it's not already disconnected to avoid loop
            if (connectionState.getValue() != ConnectionState.DISCONNECTED) {
                updateState(ConnectionState.DISCONNECTED);
            }
        }
    }
    
    /**
     * connect to specified device with retry and exponential backoff
     *
     */
    public void connect(BluetoothDevice device) {
        if (device == null) {
            reportError("Cannot connect to null device");
            return;
        }
        
        // store the target device for connection
        targetDevice = device;
        
        // update connection state to connecting
        updateState(ConnectionState.CONNECTING);
        
        // reset connection retry counter
        connectionRetries = 0;
        
        // start the connection process with permission check
        executeWithPermissionCheck(() -> {
            connectWithRetry();
        }, "device connection");
    }
    
    /**
     * connect with retry and exponential backoff
     * 
     */
    private void connectWithRetry() {
        // clear any existing connection
        closeGattAndUpdateState();
        
        // start connection timeout handler
        startConnectionTimeout();
        
        // connect to the device with permission check
        executeWithPermissionCheck(() -> {
            // use appropriate connect method based on api level
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // for api 26+ use connectgatt with transport_le and phy settings
                bluetoothGatt = targetDevice.connectGatt(
                    appContext,
                    false, // don't auto-connect - we manage reconnection ourselves
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE,
                    BluetoothDevice.PHY_LE_1M_MASK,
                    mainHandler
                );
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // for api 23-25 use connectgatt with transport_le
                bluetoothGatt = targetDevice.connectGatt(
                    appContext,
                    false, // don't auto-connect
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE
                );
            } else {
                // for api < 23 use the basic connectgatt method
                // this is included for completeness, though the app's min sdk is 24
                bluetoothGatt = targetDevice.connectGatt(
                    appContext,
                    false, // don't auto-connect
                    gattCallback
                );
            }
            
            // check if connection was initiated
            if (bluetoothGatt == null) {
                // failed to initiate connection
                mainHandler.post(() -> {
                    reportError("Failed to initiate connection to " + targetDevice.getAddress());
                    updateState(ConnectionState.DISCONNECTED);
                });
            } else {
                // connection initiated 
                Log.d(TAG, "Connection initiated to " + targetDevice.getAddress());
            }
        }, "connect to device");
    }
    
    /**
     * 
     * @return calculated delay in milliseconds for the next connection attempt
     */
    private long calculateBackoffDelay() {
        // base delay increased by retry count using exponential backoff
        long baseDelay = BASE_RECONNECTION_DELAY * (1L << Math.min(reconnectionAttempts, 6)); // cap at 64x
        
       
        double jitterFactor = 0.8 + (Math.random() * 0.4); // 0.8-1.2 range for ±20% jitter
        
        long delay = (long)(baseDelay * jitterFactor);
        
        // log the calculated delay
        Log.d(TAG, "Reconnection attempt " + reconnectionAttempts + 
              ", backoff delay: " + delay + "ms");
        
        // cap at a reasonable maximum to prevent excessive delays
        return Math.min(delay, MAX_RECONNECTION_DELAY);
    }
    
    /**
     * request optimal connection parameters for esp32 for battery 
     * 
     */
    private void requestConnectionParameters(BluetoothGatt gatt) {
        if (gatt == null) return;
        
        executeWithPermissionCheck(() -> {
            // Request connection priority first - changed to HIGH priority
            boolean priorityResult = gatt.requestConnectionPriority(
                BluetoothGatt.CONNECTION_PRIORITY_HIGH);
            Log.d(TAG, "Connection priority (HIGH) request result: " + priorityResult);
            
            // Short delay before MTU request to improve reliability on Android 12
            mainHandler.postDelayed(() -> {
                requestMtuNegotiation(gatt);
            }, 300);
            
            // Set connection update success flag for monitoring
            operationQueue.queue(() -> {
                // Only log on debug - no need to handle errors here as it's not critical
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        // On Android 12+
                        int interval = ESP32BluetoothSpec.ConnectionParams.ANDROID12_CONN_INTERVAL;
                        int latency = ESP32BluetoothSpec.ConnectionParams.ANDROID12_SLAVE_LATENCY; 
                        int timeout = ESP32BluetoothSpec.ConnectionParams.ANDROID12_SUPERVISION_TIMEOUT;
                        
                        // doesn't return a result in Android 12
                        gatt.setPreferredPhy(
                            BluetoothDevice.PHY_LE_1M, 
                            BluetoothDevice.PHY_LE_1M,  
                            BluetoothDevice.PHY_OPTION_NO_PREFERRED
                        );
                        Log.d(TAG, "Requested preferred PHY: 1M");
                        
                    } catch (Exception e) {
                        Log.w(TAG, "Error setting connection parameters: " + e.getMessage());
                    }
                }
                // mark as complete
                operationQueue.operationComplete();
            });
        }, "request connection parameters");
    }
    
    /**
     * timeout handler with exponential backoff for retries
     */
    private void startConnectionTimeout() {
        // Clear any existing timeout
        mainHandler.removeCallbacks(connectionTimeoutRunnable);
        
        // Create a new timeout runnable
        connectionTimeoutRunnable = () -> {
            if (connectionState.getValue() == ConnectionState.CONNECTING) {
                Log.d(TAG, "Connection timeout - attempt " + (connectionRetries + 1));
                
                // Close existing GATT
                if (bluetoothGatt != null) {
                    bluetoothGatt.close();
                    bluetoothGatt = null;
                }
                
                // Retry if we haven't exhausted retries
                if (connectionRetries < MAX_RETRIES) {
                    connectionRetries++;
                    
                    // Schedule retry with exponential backoff
                    long delay = calculateBackoffDelay();
                    Log.d(TAG, "Scheduling retry in " + delay + "ms");
                    
                    mainHandler.postDelayed(this::connectWithRetry, delay);
                } else {
                    reportError("Connection timeout after " + MAX_RETRIES + " attempts");
                    updateState(ConnectionState.DISCONNECTED);
                }
            }
        };
        
        // Schedule the timeout
        mainHandler.postDelayed(connectionTimeoutRunnable, CONNECTION_TIMEOUT);
    }
    
    /**
     * Disconnect from the currently connected device
     */
    public void disconnect() {
        Log.d(TAG, "Disconnecting BLE device");
        
        // Update state to disconnecting
        updateState(ConnectionState.DISCONNECTING);
        
        // Cancel any pending connection timeout
        mainHandler.removeCallbacks(connectionTimeoutRunnable);
        
        // Use the operation queue to ensure proper sequencing
        executeWithPermissionCheck(() -> {
            if (bluetoothGatt != null) {
                try {
                    // Disconnect and close GATT
                    bluetoothGatt.disconnect();
                    Log.d(TAG, "Disconnect request sent");
                    
                    // Note: We don't update state here wait for the callback
                } catch (Exception e) {
                    Log.e(TAG, "Error disconnecting: " + e.getMessage());
                    closeGattAndUpdateState();
                }
            } else {
                // No active connection
                updateState(ConnectionState.DISCONNECTED);
            }
        }, "disconnect device");
    }
    
    /**
     * Helper method to close GATT and update state
     */
    private void closeGattAndUpdateState() {
        executeWithPermissionCheck(() -> {
            if (bluetoothGatt != null) {
                try {
                    bluetoothGatt.close();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing GATT: " + e.getMessage(), e);
                } finally {
                    bluetoothGatt = null;
                    updateState(ConnectionState.DISCONNECTED);
                }
            }
        }, "GATT close");
    }
    
    /**
     *request mtu 
     * Larger MTU efficiency >>>> , we had a lower before 
     * esp32 max=  517 bytes
     */
    private void requestMtuNegotiation(BluetoothGatt gatt) {
        if (gatt == null) return;
        
        executeWithPermissionCheck(() -> {
           
            final int OPTIMAL_MTU = 512; 
            
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    if (!gatt.requestMtu(OPTIMAL_MTU)) {
                        Log.w(TAG, "Failed to request MTU negotiation");
                    } else {
                        Log.d(TAG, "Requested MTU size: " + OPTIMAL_MTU);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error requesting MTU: " + e.getMessage());
            }
        }, "request MTU negotiation");
    }
    
    // The GATT callback
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String deviceAddress = gatt.getDevice().getAddress();
            
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "Connected to " + deviceAddress);
                    
                    // Store as last connected device for reconnection
                    lastConnectedDevice = gatt.getDevice();
                    
                    // Reset connection retries
                    connectionRetries = 0;
                    reconnectionAttempts = 0;
                    
                    // Update connection state
                    updateState(ConnectionState.CONNECTED);
                    
                    // Request optimal connection parameters for battery life
                    requestConnectionParameters(gatt);
                    
                    // Discover services after a short delay
                    mainHandler.postDelayed(() -> {
                        executeWithPermissionCheck(() -> {
                            try {
                                if (bluetoothGatt != null) {
                                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                                        Log.d(TAG, "Starting service discovery");
                                    }
                                    if (!bluetoothGatt.discoverServices()) {
                                        reportError("Failed to start service discovery");
                                    }
                                } else {
                                    reportError("GATT is null, cannot discover services");
                                }
                            } catch (Exception e) {
                                reportError("Error discovering services: " + e.getMessage());
                            }
                        }, "service discovery");
                    }, 600); // delay to ensure connection is stable
                    
                } else {
                    String errorMsg = "Connection error: " + getErrorMessage(status);
                    Log.e(TAG, errorMsg);
                    reportError(errorMsg);
                    
                    // Close GATT and update state
                    closeGattAndUpdateState();
                    
                    //reconnection with exponential backoff
                    handleConnectionError(status);
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from " + deviceAddress);
                
                // Close GATT and clean up
                if (gatt != null) {
                    gatt.close();
                }
                
                // Only change state if not already disconnecting
                if (connectionState.getValue() != ConnectionState.DISCONNECTING) {
                    updateState(ConnectionState.DISCONNECTED);
                    
                    // Attempt auto-reconnect for unexpected disconnections
                    if (lastConnectedDevice != null && reconnectionAttempts < MAX_RECONNECTION_ATTEMPTS) {
                        reconnectionAttempts++;
                        long delay = calculateBackoffDelay();
                        
                        Log.d(TAG, "Scheduling reconnection attempt " + reconnectionAttempts + 
                                " in " + delay + "ms");
                        
                        mainHandler.postDelayed(() -> {
                            if (connectionState.getValue() == ConnectionState.DISCONNECTED) {
                                Log.d(TAG, "Attempting auto-reconnection");
                                connect(lastConnectedDevice);
                            }
                        }, delay);
                    }
                } else {
                    // Expected disconnection
                    updateState(ConnectionState.DISCONNECTED);
                }
            }
        }
        
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered successfully");
                List<BluetoothGattService> services = gatt.getServices();
                
                // Check if we found the target service
                boolean foundTargetService = false;
                for (BluetoothGattService service : services) {
                    if (ESP32BluetoothSpec.SERVICE_UUID.equals(service.getUuid())) {
                        foundTargetService = true;
                        break;
                    }
                }
                
                if (foundTargetService) {
                    // Request MTU negotiation for better performance
                    requestMtuNegotiation(gatt);
                } else {
                    reportError("Target service not found");
                    disconnect();
                }
            } else {
                reportError("Service discovery failed: " + status);
                disconnect();
            }
        }
        
        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "MTU changed successfully to " + mtu);
            } else {
                Log.w(TAG, "MTU change failed with status: " + status);
            }
        }
        
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            // Early return if no listener is registered
            if (characteristicChangeListener == null) return;
            
            // Optimization for Android 12 on Pixel 4a - reduce context switches
            // by using a local reference to the listener that's thread-safe
            final CharacteristicChangeListener localListener = characteristicChangeListener;
            
            mainHandler.post(() -> {
                // Check again in case listener was removed while posting to main thread
                if (localListener != null) {
                    // Forward the characteristic changed event to the registered listener
                    localListener.onCharacteristicChanged(characteristic);
                }
            });
        }
    };
    
    /**
     * get error message 
     * Handles both connection error codes and scan error codes
     * 
     */
    private String getErrorMessage(int errorCode) {
        // GATT connection error codes
        if (errorCode == BluetoothGatt.GATT_SUCCESS) {
            return "Success";
        }
        else if (errorCode == BluetoothGatt.GATT_FAILURE) {
            return "GATT failure";
        }
        else if (errorCode == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION) {
            return "Insufficient authentication";
        }
        else if (errorCode == BluetoothGatt.GATT_CONNECTION_CONGESTED) {
            return "Connection congested, retry later";
        }
        else if (errorCode == BluetoothGatt.GATT_READ_NOT_PERMITTED) {
            return "Read not permitted";
        }
        else if (errorCode == ESP32BluetoothSpec.ErrorCodes.GATT_ERROR) {
            return "GATT error (133) - This is common on Android 12, please retry";
        }
        else if (errorCode == ESP32BluetoothSpec.ErrorCodes.CONNECTION_TIMEOUT ||
                 errorCode == 62 || // Android 12 spec GATT_CONN_TIMEOUT
                 errorCode == 8) {  // Android 12 spec  GATT_CONN_TIMEOUT_PEER
            return "Connection timeout - ESP32 may be out of range";
        }
        else if (errorCode == ESP32BluetoothSpec.ErrorCodes.CONN_PARAM_REJECTED) {
            return "Connection parameters rejected by ESP32";
        }
        else if (errorCode == ESP32BluetoothSpec.ErrorCodes.UNSUPPORTED_TRANSPORT) {
            return "Unsupported transport - Try with default PHY";
        }
        else if (errorCode == 19) { //  GATT_CONN_TERMINATE_PEER_USER
            return "Connection terminated by ESP32 - It may be in sleep mode";
        }
        else if (errorCode == 22) { //  GATT_CONN_TERMINATE_LOCAL_HOST
            return "Connection terminated by Android - Battery optimization may be active";
        }
        else if (errorCode == 34) { // GATT_CONN_LMP_TIMEOUT
            return "LMP response timeout - Interference or ESP32 unresponsive";
        }
        else if (errorCode == 256) { //GATT_CONN_FAIL_ESTABLISH
            return "Failed to establish connection - Retry with different parameters";
        }
        
        // Scan error codes
        else if (errorCode == ScanCallback.SCAN_FAILED_ALREADY_STARTED) {
            return "Scan already started";
        }
        else if (errorCode == ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED) {
            return "Application registration failed";
        }
        else if (errorCode == ScanCallback.SCAN_FAILED_INTERNAL_ERROR) {
            return "Internal error";
        }
        else if (errorCode == ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED) {
            return "Feature unsupported";
        }
        // 6+ scan errors
        else if (errorCode == 5) { // SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES
            return "Out of hardware resources";
        }
        // Android 10+ scan errors
        else if (errorCode == 6) { // SCAN_FAILED_SCANNING_TOO_FREQUENTLY
            return "Scanning too frequently";
        }
        
        return "Unknown error code: " + errorCode;
    }
    
    /**
     * Handle connection errors based on error code
     */
    private void handleConnectionError(int errorCode) {
        Log.e(TAG, "Handling connection error: " + errorCode + " - " + getErrorMessage(errorCode));
        
        updateState(ConnectionState.DISCONNECTED);
        
        if (errorCode == BluetoothGatt.GATT_SUCCESS) {
            // Not an error, nothing to handle
            return;
        }
        
        // Handle specific error code
        if (errorCode == ESP32BluetoothSpec.ErrorCodes.CONNECTION_TIMEOUT ||
            errorCode == 62 || 
            errorCode == 8) {  
            
            // retry with backoff if under max retries
            if (connectionRetries < MAX_RETRIES) {
                long delay = calculateBackoffDelay();
                Log.d(TAG, "Connection timed out, retrying after " + delay + "ms (attempt " + (connectionRetries + 1) + ")");
                mainHandler.postDelayed(this::connectWithRetry, delay);
            } else {
                reportError("Connection failed after " + MAX_RETRIES + " retries: " + getErrorMessage(errorCode));
            }
        }
        else if (errorCode == ESP32BluetoothSpec.ErrorCodes.GATT_ERROR) {
            // retry with exponential backoff
            if (connectionRetries < MAX_RETRIES) {
                long delay = calculateBackoffDelay();
                Log.d(TAG, "Retrying connection after " + delay + "ms (attempt " + (connectionRetries + 1) + ")");
                mainHandler.postDelayed(this::connectWithRetry, delay);
            } else {
                reportError("Connection failed after " + MAX_RETRIES + " retries: " + getErrorMessage(errorCode));
            }
        }
        else {
            // For other errors, report and don't retry automatically
            reportError("Connection error: " + getErrorMessage(errorCode));
        }
    }
    
    /**
     * Get the BluetoothGatt instance
     */
    public BluetoothGatt getBluetoothGatt() {
        return bluetoothGatt;
    }
    
    /**
     * Get the current connection state
     */
    public ConnectionState getCurrentState() {
        return connectionState.getValue();
    }
    
    /**
     * Reconnect to the last connected device
     * This is called when notifications timeout to recover the connection
     */
    public void reconnect() {
        Log.d(TAG, "Attempting to reconnect due to notification timeout");
        
        // If we're already connecting, don't interrupt
        if (connectionState.getValue() == ConnectionState.CONNECTING) {
            Log.d(TAG, "Already in connecting state, skipping reconnection");
            return;
        }
        
        // If we're already connected, disconnect first
        if (connectionState.getValue() == ConnectionState.CONNECTED) {
            Log.d(TAG, "Disconnecting before reconnection");
            disconnect();
            
            // Schedule the reconnection after a short delay to allow disconnect to complete
            mainHandler.postDelayed(() -> attemptReconnect(), 500);
        } else {
            // If we're already disconnected, attempt reconnection immediately
            attemptReconnect();
        }
    }
    
    /**
     * Helper method to attempt reconnection with the last connected device
     */
    private void attemptReconnect() {
        if (lastConnectedDevice == null) {
            Log.e(TAG, "Cannot reconnect - no previous device");
            reportError("Cannot reconnect - no previous device");
            return;
        }
        
        Log.d(TAG, "Reconnecting to last device: " + lastConnectedDevice.getAddress());
        
        // Reset retry counters to allow a fresh set of connection attempts
        connectionRetries = 0;
        reconnectionAttempts = 0;
        
        // Connect to the last device
        connect(lastConnectedDevice);
    }
    
    /**
     * Getter methods for LiveData
     */
    public LiveData<ConnectionState> getConnectionState() {
        return connectionState;
    }
    
    public LiveData<String> getConnectionError() {
        return connectionError;
    }
    
    /**
     * Listener management
     */
    public void addConnectionListener(ConnectionListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    public void removeConnectionListener(ConnectionListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Set the characteristic change listener
     */
    public void setCharacteristicChangeListener(CharacteristicChangeListener listener) {
        this.characteristicChangeListener = listener;
    }
    
    /**
     * Clean up resources
     */
    public void cleanup() {
        Log.d(TAG, "Cleaning up BLE resources");
        
        // Disconnect if connected
        if (connectionState.getValue() == ConnectionState.CONNECTED || 
            connectionState.getValue() == ConnectionState.CONNECTING) {
            disconnect();
        }
        
        // Ensure GATT is closed
        executeWithPermissionCheck(() -> {
            closeGattAndUpdateState();
        }, "cleanup resources");
        
        // Stop any active scan
        stopScan();
        
        // Clear operation queue
        operationQueue.cancelAllOperations();
        
        // Clear handlers
        mainHandler.removeCallbacksAndMessages(null);
        
        // Clear listeners (but don't null the list to avoid NPEs)
        listeners.clear();
        characteristicChangeListener = null;
    }
    
    /**
     * Get the last connected device 
     * @return The last device we successfully connected to null if none
     */
    public BluetoothDevice getLastConnectedDevice() {
        executeWithPermissionCheck(() -> {
            // No op, just for permission check
        }, "get last connected device");
        
        return lastConnectedDevice;
    }
    
    /**
     * Stop any active Bluetooth LE scan
     * 
     */
    public void stopScan() {
        if (!isScanning.get()) return;
        
        executeWithPermissionCheck(() -> {
            try {
                if (isScanning.getAndSet(false)) {
                    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                    if (adapter != null && adapter.getBluetoothLeScanner() != null && scanCallback != null) {
                        adapter.getBluetoothLeScanner().stopScan(scanCallback);
                        Log.d(TAG, "BLE scan stopped");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error stopping scan: " + e.getMessage());
            } finally {
                // Clear scan callback
                scanCallback = null;
            }
        }, "stop BLE scan");
    }
    
    /**
     * Scan for ESP32 BLE devices
     * 
     * 
     * @param callback Callback to receive scan results
     * @param timeoutMs Timeout in milliseconds
     */
    public void scanForESP32(ScanResultCallback callback, long timeoutMs) {
        // Early validation of callback
        if (callback == null) {
            Log.e(TAG, "Cannot scan: callback is null");
            return;
        }
        
        // 12 spec
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!permissionManager.hasBluetoothScanPermission()) {
                Log.e(TAG, "Cannot scan: BLUETOOTH_SCAN permission not granted");
                mainHandler.post(() -> callback.onScanFailed(
                    ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED, 
                    "BLUETOOTH_SCAN permission not granted"));
                return;
            }
        }
        
        executeWithPermissionCheck(() -> {
            // Stop any existing scan first
            if (isScanning.get()) {
                stopScan();
            }
            
            // Get Bluetooth adapter and scanner
            BluetoothManager bluetoothManager = (BluetoothManager) 
                    appContext.getSystemService(Context.BLUETOOTH_SERVICE);
            if (bluetoothManager == null) {
                mainHandler.post(() -> callback.onScanFailed(
                    ScanCallback.SCAN_FAILED_INTERNAL_ERROR, 
                    "Bluetooth manager not available"));
                return;
            }
            
            BluetoothAdapter adapter = bluetoothManager.getAdapter();
            if (adapter == null) {
                mainHandler.post(() -> callback.onScanFailed(
                    ScanCallback.SCAN_FAILED_INTERNAL_ERROR, 
                    "Bluetooth adapter not available"));
                return;
            }
            
            if (!adapter.isEnabled()) {
                mainHandler.post(() -> callback.onScanFailed(
                    ScanCallback.SCAN_FAILED_INTERNAL_ERROR, 
                    "Bluetooth is disabled"));
                return;
            }
            
            BluetoothLeScanner scanner = adapter.getBluetoothLeScanner();
            if (scanner == null) {
                mainHandler.post(() -> callback.onScanFailed(
                    ScanCallback.SCAN_FAILED_INTERNAL_ERROR, 
                    "Bluetooth LE scanner not available"));
                return;
            }
            
            // Create scan settings 
            ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)  // quick discovery high power mode
                .setReportDelay(0)  // Report results immediately
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)  // Report all devices
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)  
                .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)  // Get all advertisements
                .build();
                
            // Create ESP32 service UUID filter
            List<ScanFilter> filters = new ArrayList<>();
            ScanFilter filter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(ESP32BluetoothSpec.SERVICE_UUID))
                .build();
            filters.add(filter);
            
            // Create scan callback that looks for esp32 
            scanCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    BluetoothDevice device = result.getDevice();
                    if (device != null && isESP32Device(device, result)) {
                        // Stop scanning as soon as we find a matching device
                        stopScan();
                        
                        // Notify on main thread
                        mainHandler.post(() -> callback.onDeviceFound(device, result));
                    }
                }
                
                @Override
                public void onScanFailed(int errorCode) {
                    // Handle scan failure
                    stopScan();
                    String errorMsg = "Scan failed with code " + errorCode + 
                            ": " + getErrorMessage(errorCode);
                    Log.e(TAG, errorMsg);
                    
                    // Notify failure on main thread
                    mainHandler.post(() -> callback.onScanFailed(errorCode, errorMsg));
                }
            };
            
            try {
                // Start the scan
                scanner.startScan(filters, settings, scanCallback);
                isScanning.set(true);
                Log.d(TAG, "Started BLE scan for ESP32 devices");
                
                // Set timeout to stop scan after specified duration
                mainHandler.postDelayed(() -> {
                    // Check if we're still scanning and haven't found a device
                    if (isScanning.get()) {
                        Log.d(TAG, "Scan timeout reached after " + timeoutMs + "ms");
                        stopScan();
                        mainHandler.post(callback::onScanTimeout);
                    }
                }, timeoutMs);
                
            } catch (Exception e) {
                // Handle exceptions
                isScanning.set(false);
                scanCallback = null;
                Log.e(TAG, "Error starting scan: " + e.getMessage());
                mainHandler.post(() -> callback.onScanFailed(
                    ScanCallback.SCAN_FAILED_INTERNAL_ERROR, 
                    "Error starting scan: " + e.getMessage()));
            }
        }, "scan for ESP32 devices");
    }
    
    /**
     * Check if a device is likely an esp based on scan result
     */
    private boolean isESP32Device(BluetoothDevice device, ScanResult result) {
        // Check by name
        String deviceName = getDeviceNameSafely(device);
        if (deviceName != null && !deviceName.equals("Unknown Device") && !deviceName.equals("ESP32 Device")) {
            deviceName = deviceName.toLowerCase();
            if (deviceName.contains("esp32") || deviceName.contains("smarthat")) {
                return true;
            }
        }
         
        // Check by service UUID
        ScanRecord record = result.getScanRecord();
        if (record != null) {
            List<ParcelUuid> serviceUuids = record.getServiceUuids();
            if (serviceUuids != null) {
                for (ParcelUuid uuid : serviceUuids) {
                    if (ESP32BluetoothSpec.SERVICE_UUID.equals(uuid.getUuid())) {
                        return true;
                    }
                }
            }
        }
        
        // If neither matche heck signal strength as last resort
    
        int rssi = result.getRssi();
        return rssi > -75; // Only consider devices with strong signal
    }
    
    /**
     * Safely get device name with permission checks
     * Returns a default name if permission is denied or name is null
     */
    private String getDeviceNameSafely(BluetoothDevice device) {
        if (device == null) return "Unknown Device";
        
        // Use a default name that will be returned if permission is denied
        final String[] deviceName = {"ESP32 Device"};
        
        // Try to get the actual name with permission check
        if (permissionManager != null && permissionManager.hasBluetoothConnectPermission()) {
            try {
                String name = device.getName();
                if (name != null) {
                    deviceName[0] = name;
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Permission denied when getting device name", e);
            }
        }
        
        return deviceName[0];
    }
    
    /**
     * Report an error
     */
    private void reportError(String error) {
        Log.e(TAG, error);
        
        // Ensure we are on the main thread
        if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
            mainHandler.post(() -> doReportError(error));
        } else {
            doReportError(error);
        }
    }
    
    /**
     * Helper method to report error on main thread
     * Optimized for Android 12 on Pixel 4a
     */
    private void doReportError(String error) {
        // Update LiveData
        connectionError.setValue(error);
        
        // Don't create new list if there are no listeners
        if (listeners.isEmpty()) return;
        
        // Copy list for thread safety optimization
        final List<ConnectionListener> listenersCopy = new ArrayList<>(listeners);
        
        // Notify listeners directly
        for (ConnectionListener listener : listenersCopy) {
            listener.onError(error);
        }
    }
    
    /**
     * Resets the singleton instance for test only
     * 
     */
    @VisibleForTesting
    public static void resetInstanceForTesting() {
        if (instance != null) {
            instance.cleanup();
            instance = null;
        }
    }
    
    // protected getter for characteristicChangeListener
    /**
     *
     * @return The currently registered listener or null if none
     */
    protected CharacteristicChangeListener getCharacteristicChangeListener() {
        return characteristicChangeListener;
    }
    
    /**
     * Check if the disconnection was initiated by the user
     * This is used to prevent automatic reconnection attempts when the user intentionally disconnected
     * @return true if the user initiated the disconnection
     */
    public boolean isUserDisconnected() {
        ConnectionState state = connectionState.getValue();
        return state == ConnectionState.DISCONNECTING || state == ConnectionState.DISCONNECTED;
    }
}  