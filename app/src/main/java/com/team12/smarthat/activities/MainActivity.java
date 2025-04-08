package com.team12.smarthat.activities;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.github.anastr.speedviewlib.PointerSpeedometer;
import com.github.anastr.speedviewlib.components.Section;
import com.team12.smarthat.R;
import com.team12.smarthat.bluetooth.core.BleConnectionManager;
import com.team12.smarthat.bluetooth.core.BluetoothServiceIntegration;
import com.team12.smarthat.bluetooth.core.MockBleConnectionManager;
import com.team12.smarthat.bluetooth.devices.esp32.ESP32BluetoothSpec;
import com.team12.smarthat.database.DatabaseHelper;
import com.team12.smarthat.models.SensorData;
import com.team12.smarthat.permissions.BluetoothPermissionManager;
import com.team12.smarthat.utils.Constants;
import com.team12.smarthat.utils.NotificationUtils;
import com.team12.smarthat.utils.TestDataGenerator;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * main activity handling a lot we might consider refactoring some of this into separate classes later
 */
public class MainActivity extends AppCompatActivity implements 
        BluetoothServiceIntegration.SensorDataListener,
        BluetoothPermissionManager.PermissionCallback,
        TestDataGenerator.TestDataListener {


    private BluetoothManager systemBluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    
    // permission management
    private BluetoothPermissionManager permissionManager;
    
    // database
    private DatabaseHelper databaseHelper; // our local sqlite database access
    private NotificationUtils notificationUtils;
    
    // ui components
    private TextView tvStatus, tvDust, tvNoise, tvTestMode, tvGasValue;
    private Button btnConnect;
    private View connectionIndicator;
    private TextView connectionHelper;
    private ProgressBar soundMeter, dustMeter;
    private PointerSpeedometer gasGauge;
    
    // ble connection management
    private BleConnectionManager connectionManager;
    private BluetoothServiceIntegration btIntegration;
    
    // added to support switching between real and mock ble managers
    private MockBleConnectionManager mockConnectionManager;
    private boolean testModeActive = false;
    
    // Variables for tracking sustained high noise levels
    private long highNoiseStartTime = 0;
    private boolean isHighNoiseTracking = false;
    private static final long SUSTAINED_NOISE_THRESHOLD_MS = 4000; // 4 seconds
    
    // region activation system
    private ActivityResultLauncher<Intent> bluetoothEnableLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            if (result.getResultCode() == Activity.RESULT_OK) {

                Log.d(Constants.TAG_MAIN, "Bluetooth enabled successfully via system dialog");
                startScanAndConnect();
            } else {
                // user declined bluetooth activation
                Log.d(Constants.TAG_MAIN, "User declined to enable Bluetooth via system dialog");
                showToast("Bluetooth is required to connect to SmartHat device");
                updateConnectionUI(BleConnectionManager.ConnectionState.DISCONNECTED);
            }
        }
    );
    // endregion

    // single thread executor for database operations to avoid blocking the main thread
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    private TestDataGenerator testDataGenerator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Set up toolbar
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.main_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        
        // initialize components
        initializeComponents();
        
        // Setup UI components
        setupUI();
        
        // Setup observers for data changes
        setupObservers();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // Check if test mode should be visible
        MenuItem testModeItem = menu.findItem(R.id.action_test_mode);
        testModeItem.setVisible(Constants.DEV_MODE);
        
        // Update test mode menu items
        if (testDataGenerator != null) {
            TestDataGenerator.TestMode currentMode = testDataGenerator.getCurrentMode();
            MenuItem offItem = menu.findItem(R.id.action_test_mode_off);
            MenuItem normalItem = menu.findItem(R.id.action_test_mode_normal);
            MenuItem highDustItem = menu.findItem(R.id.action_test_mode_high_dust);
            MenuItem highNoiseItem = menu.findItem(R.id.action_test_mode_high_noise);
            MenuItem highGasItem = menu.findItem(R.id.action_test_mode_high_gas);
            MenuItem randomItem = menu.findItem(R.id.action_test_mode_random);
            
            if (offItem != null) offItem.setChecked(currentMode == TestDataGenerator.TestMode.OFF);
            if (normalItem != null) normalItem.setChecked(currentMode == TestDataGenerator.TestMode.NORMAL);
            if (highDustItem != null) highDustItem.setChecked(currentMode == TestDataGenerator.TestMode.HIGH_DUST);
            if (highNoiseItem != null) highNoiseItem.setChecked(currentMode == TestDataGenerator.TestMode.HIGH_NOISE);
            if (highGasItem != null) highGasItem.setChecked(currentMode == TestDataGenerator.TestMode.HIGH_GAS);
            if (randomItem != null) randomItem.setChecked(currentMode == TestDataGenerator.TestMode.RANDOM);
        }
        
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.action_history) {
            // Open threshold history activity
            Intent intent = new Intent(this, ThresholdHistoryActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_settings) {
            // Open settings activity
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_test_mode_off) {
            setTestMode(TestDataGenerator.TestMode.OFF);
            return true;
        } else if (id == R.id.action_test_mode_normal) {
            setTestMode(TestDataGenerator.TestMode.NORMAL);
            return true;
        } else if (id == R.id.action_test_mode_high_dust) {
            setTestMode(TestDataGenerator.TestMode.HIGH_DUST);
            return true;
        } else if (id == R.id.action_test_mode_high_noise) {
            setTestMode(TestDataGenerator.TestMode.HIGH_NOISE);
            return true;
        } else if (id == R.id.action_test_mode_high_gas) {
            setTestMode(TestDataGenerator.TestMode.HIGH_GAS);
            return true;
        } else if (id == R.id.action_test_mode_random) {
            setTestMode(TestDataGenerator.TestMode.RANDOM);
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // register receiver to listen for bluetooth state changes
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(bluetoothStateReceiver, filter);
        

        if (permissionManager.hasRequiredPermissions()) {
            try {
                // check if bluetooth is enabled
                if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
                    // update the ui to show bluetooth is disabled
                    updateConnectionUI(BleConnectionManager.ConnectionState.DISCONNECTED);
                    connectionHelper.setText(R.string.bluetooth_disabled);
                } else if (connectionManager != null) {
                    updateConnectionUI(connectionManager.getCurrentState());
                    
                    // attempt auto-reconnection if we were previously connected but now disconnected
                    if (connectionManager.getCurrentState() == BleConnectionManager.ConnectionState.DISCONNECTED
                        && connectionManager.getLastConnectedDevice() != null) {
                        // add a short delay before attempting reconnection
                        mainHandler.postDelayed(this::startReconnectionProcess, 1000);
                    }
                }
            } catch (SecurityException e) {
                // handle security exception that might occur when accessing bluetooth adapter
                Log.e(Constants.TAG_MAIN, "Security exception in onResume: " + e.getMessage());
                updateConnectionUI(BleConnectionManager.ConnectionState.DISCONNECTED);
            }
        } else {
            // we don't have required permissions yet, just update the ui accordingly
            updateConnectionUI(BleConnectionManager.ConnectionState.DISCONNECTED);
            connectionHelper.setText(R.string.permission_required);
        }
        
        // Restore test mode state after resuming
        restoreTestModeState();
    }

    @Override
    protected void onPause() {
        super.onPause();
        
        // unregister the bluetooth state receiver to prevent leaks
        try {
            unregisterReceiver(bluetoothStateReceiver);
        } catch (IllegalArgumentException e) {
            // ignore if receiver wasn't registered
            Log.d(Constants.TAG_MAIN, "BluetoothStateReceiver was not registered");
        }
        
        // cancel any pending ui updates or delayed operations
        mainHandler.removeCallbacksAndMessages(null);

        // Save test mode state when pausing
        saveTestModeState();
    }

    @Override
    protected void onStop() {
        super.onStop();

        // we don't auto disconnect to allow background operation
        // this follows android 12 background restrictions specifications
        
        // save current state to restore later if needed
        if (databaseHelper != null) {
            databaseHelper.saveAppState(connectionManager.getCurrentState().name());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // clean up resources
        try {
            // clean up test data generator
            if (testDataGenerator != null) {
                testDataGenerator.cleanup();
                testDataGenerator = null;
            }
            

            
            // remove listeners to prevent callbacks during cleanup
            if (btIntegration != null) {
                btIntegration.removeSensorDataListener(this);
            }
            //cleanup BLE integration
            if (btIntegration != null) {
                btIntegration.cleanup();
                btIntegration = null;
            }
            
            // clean up both connection managers
            if (connectionManager != null) {
                connectionManager.cleanup();
                BleConnectionManager.resetInstanceForTesting();
            }
            
            if (mockConnectionManager != null) {
                mockConnectionManager.cleanup();
                MockBleConnectionManager.resetInstance();
            }

            mainHandler.removeCallbacksAndMessages(null);
            
            // shut down db executor service if exists
            if (dbExecutor != null && !dbExecutor.isShutdown()) {
                Log.d(Constants.TAG_MAIN, "Shutting down database executor service");
                dbExecutor.shutdown();
                
                try {
                    // timeout
                    if (!dbExecutor.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                        dbExecutor.shutdownNow();
                    }
                    Log.d(Constants.TAG_MAIN, "Database executor service shut down");
                } catch (InterruptedException e) {
                    dbExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                    Log.e(Constants.TAG_MAIN, "Database executor shutdown interrupted");
                }
            }
        } catch (Exception e) {
            Log.e(Constants.TAG_MAIN, "Error during cleanup: " + e.getMessage(), e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (!permissionManager.handlePermissionResult(requestCode, permissions, grantResults)) {
            // if not handled by our permis manager -> parent
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
    
    /**
     * shows a dialog explaining how to enable permissions from settings
     * case denied permission
     */
    private void showSettingsPermissionDialog() {
        Log.d(Constants.TAG_MAIN, "Showing settings permission dialog");
        new AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("SmartHat requires Bluetooth permissions to function properly. " +
                       "Please enable them in app settings.")
            .setPositiveButton("Settings", (dialog, which) -> {
                // Open app settings
                Log.d(Constants.TAG_MAIN, "User chose to open settings");
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                startActivity(intent);
            })
            .setNegativeButton("Cancel", (dialog, which) -> {
                Log.d(Constants.TAG_MAIN, "User canceled settings dialog");
                updateConnectionUI(BleConnectionManager.ConnectionState.DISCONNECTED);
                showToast("Cannot connect without required permissions");
            })
            .setCancelable(false)
            .show();
    }

    // region component init
    private void initializeComponents() {
        // find and initialize ui components
        tvStatus = findViewById(R.id.tv_status);
        tvDust = findViewById(R.id.tv_dust);
        tvNoise = findViewById(R.id.tv_noise);
        tvTestMode = findViewById(R.id.tv_test_mode);
        tvGasValue = findViewById(R.id.tv_gas_value);
        btnConnect = findViewById(R.id.btn_connect);
        connectionIndicator = findViewById(R.id.connection_indicator);
        connectionHelper = findViewById(R.id.connection_helper);
        soundMeter = findViewById(R.id.sound_meter);
        dustMeter = findViewById(R.id.dust_meter);
        gasGauge = findViewById(R.id.gas_gauge);
        
        // initialize database helper singleton
        databaseHelper = DatabaseHelper.getInstance();
        
        // initialize bluetooth system components
        systemBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (systemBluetoothManager != null) {
            bluetoothAdapter = systemBluetoothManager.getAdapter();
        }
        
        // initialize permission manager for android 12 compatibility
        permissionManager = new BluetoothPermissionManager(this);
        
        // get the singleton instance of the connection manager
        connectionManager = BleConnectionManager.getInstance(this, permissionManager);
        
        // initialize mock manager but don't use it yet
        mockConnectionManager = MockBleConnectionManager.getInstance(this, permissionManager);

        btIntegration = new BluetoothServiceIntegration(connectionManager);
        btIntegration.addSensorDataListener(this);
        
        // set up lifecycle aware connection state observation
        btIntegration.observeConnectionState(this);

        notificationUtils = new NotificationUtils(this);
        
        boolean notificationsEnabled = getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getBoolean(Constants.PREF_NOTIFICATIONS_ENABLED, true);
        notificationUtils.setNotificationsEnabled(notificationsEnabled);
    }
    // endregion

    // region ui config
    private void setupUI() {
        tvStatus.setText(R.string.status_disconnected);
        tvDust.setText(R.string.dust_initial);
        tvNoise.setText(R.string.noise_initial);
        tvGasValue.setText(R.string.gas_initial);
        
        // Initialize progress bars
        if (dustMeter != null) {
            dustMeter.setProgress(0);
        }
        
        if (soundMeter != null) {
            soundMeter.setProgress(0);
        }
        
        // Configure gas gauge
        if (gasGauge != null) {
            gasGauge.setMaxSpeed(500);
            gasGauge.setUnit("PPM");
            gasGauge.setSpeedTextColor(Color.BLACK);
            gasGauge.setCenterCircleColor(Color.WHITE);
            gasGauge.setMarkColor(Color.DKGRAY);
            gasGauge.setTextSize(30f);
            gasGauge.setUnitTextSize(15f);
            gasGauge.setWithTremble(false);
            
            // Get custom gas threshold to configure the sections
            float gasThreshold = getCustomGasThreshold();
            float thresholdRatio = Math.min(gasThreshold / gasGauge.getMaxSpeed(), 0.9f);
            
            gasGauge.clearSections();
            gasGauge.addSections(
                new Section(0f, thresholdRatio, Color.parseColor("#4CAF50"), 30),
                new Section(thresholdRatio, 1f, Color.parseColor("#F44336"), 30)
            );
            
            // Set initial speed to 0
            gasGauge.speedTo(0, 1000);
        }
        
        // hide test mode indicator initially
        tvTestMode.setVisibility(View.GONE);
        
        // set up connectbtn click handler
        btnConnect.setOnClickListener(v -> toggleConnection());
    }
    // endregion

    // region observers
    private void setupObservers() {
        if (connectionManager == null || btIntegration == null) {
            Log.e(Constants.TAG_MAIN, "Cannot setup observers - components not initialized");
            return;
        }

        // First, remove any existing observers to prevent duplicates
        if (connectionManager.getConnectionState().hasActiveObservers()) {
            connectionManager.getConnectionState().removeObservers(this);
        }
        if (mockConnectionManager.getConnectionState().hasActiveObservers()) {
            mockConnectionManager.getConnectionState().removeObservers(this);
        }
        if (connectionManager.getConnectionError().hasActiveObservers()) {
            connectionManager.getConnectionError().removeObservers(this);
        }
        if (mockConnectionManager.getConnectionError().hasActiveObservers()) {
            mockConnectionManager.getConnectionError().removeObservers(this);
        }

        // Observe the real connection manager
        connectionManager.getConnectionState().observe(this, state -> {
            if (!testModeActive) {
                Log.d(Constants.TAG_MAIN, "Real connection state changed: " + state);
                updateConnectionUI(state);
            }
        });
        
        // Observe mock connection manager
        mockConnectionManager.getConnectionState().observe(this, state -> {
            if (testModeActive) {
                Log.d(Constants.TAG_MAIN, "Mock connection state changed: " + state);
                updateConnectionUI(state);
                
                // Start/stop test data generation based on connection state
                if (state == BleConnectionManager.ConnectionState.CONNECTED) {
                    // Start generating test data when connected
                    if (testDataGenerator != null && 
                        testDataGenerator.getCurrentMode() != TestDataGenerator.TestMode.OFF) {
                        // Only start if not already running
                        if (!testDataGenerator.isTestModeActive()) {
                            TestDataGenerator.TestMode currentMode = testDataGenerator.getCurrentMode();
                            testDataGenerator.startTestMode(currentMode);
                            
                            // Update UI to reflect current mode
                            updateTestModeUI(currentMode);
                        }
                    }
                } else if (state == BleConnectionManager.ConnectionState.DISCONNECTED) {
                    // Stop generating test data when disconnected
                    if (testDataGenerator != null && testDataGenerator.isTestModeActive()) {
                        testDataGenerator.stopTestMode();
                    }
                }
            }
        });
        
        // Error message observers
        connectionManager.getConnectionError().observe(this, error -> {
            if (!testModeActive) {
                showToast(error);
            }
        });
        
        mockConnectionManager.getConnectionError().observe(this, error -> {
            if (testModeActive) {
                showToast(error);
            }
        });

        // Setup Bluetooth service integration - optimize for Android 12 on Pixel 4a
        if (btIntegration != null) {
            btIntegration.observeConnectionState(this);
        }
        
        Log.d(Constants.TAG_MAIN, "Observers setup complete for " + 
              (testModeActive ? "TEST" : "REAL") + " mode");
    }
    // endregion

    /**
     * Update the UI to reflect the current test mode
     */
    private void updateTestModeUI(TestDataGenerator.TestMode mode) {
        if (tvTestMode == null) {
            Log.e(Constants.TAG_MAIN, "tvTestMode is null, cannot update UI");
            return;
        }
        
        if (mode == TestDataGenerator.TestMode.OFF) {
            tvTestMode.setVisibility(View.GONE);
            return;
        }
        
        tvTestMode.setVisibility(View.VISIBLE);
        
        String modeText = "TEST MODE: UNKNOWN";
        switch (mode) {
            case NORMAL:
                modeText = "TEST MODE: NORMAL";
                break;
            case HIGH_DUST:
                modeText = "TEST MODE: HIGH DUST";
                break;
            case HIGH_NOISE:
                modeText = "TEST MODE: HIGH NOISE";
                break;
            case HIGH_GAS:
                modeText = "TEST MODE: HIGH GAS";
                break;
            case RANDOM:
                modeText = "TEST MODE: RANDOM";
                break;
        }
        
        tvTestMode.setText(modeText);
        Log.d(Constants.TAG_MAIN, "Updated test mode UI: " + modeText);
    }

    // region connection management
    /**
     * toggle connection state
     * for both real and test mode connections
     * optimized for Android 12 on Pixel 4a
     */
    private void toggleConnection() {
        // determine which manager to use
        BleConnectionManager activeManager = testModeActive ? mockConnectionManager : connectionManager;
        
        if (activeManager == null) {
            showToast("BLE connection manager not initialized");
            return;
        }
        
        BleConnectionManager.ConnectionState currentState = activeManager.getConnectionState().getValue();
        if (currentState == null) {
            currentState = BleConnectionManager.ConnectionState.DISCONNECTED;
        }
        
        try {
            Log.d(Constants.TAG_MAIN, "Toggle connection in " + (testModeActive ? "TEST" : "REAL") + 
                " mode, current state: " + currentState);
            
            switch (currentState) {
                case CONNECTED:
                    Log.d(Constants.TAG_MAIN, "User requested disconnect from connected state");
                    activeManager.disconnect();
                    
                    // For test mode, ensure test data generation is stopped
                    if (testModeActive && testDataGenerator != null && testDataGenerator.isTestModeActive()) {
                        testDataGenerator.stopTestMode();
                    }
                    break;
                    
                case CONNECTING:
                    Log.d(Constants.TAG_MAIN, "User requested cancel during connection attempt");
                    showToast("Cancelling connection attempt...");
                    
                    // Call disconnect on the active manager to cancel the connection attempt
                    activeManager.disconnect();
                    
                    // For test mode, we need to ensure any test data generation is also stopped
                    if (testModeActive && testDataGenerator != null && testDataGenerator.isTestModeActive()) {
                        testDataGenerator.stopTestMode();
                    }
                    break;
                    
                case DISCONNECTING:
                    Log.d(Constants.TAG_MAIN, "User pressed connect while disconnecting - ignoring");
                    showToast("Already disconnecting...");
                    break;
                    
                case DISCONNECTED:
                    Log.d(Constants.TAG_MAIN, "User requested connect from disconnected state");
                    
                    if (testModeActive) {
                        Log.d(Constants.TAG_MAIN, "Using mock connection in test mode");
                        // Ensure test mode is properly configured before connecting
                        if (testDataGenerator != null) {
                            TestDataGenerator.TestMode currentMode = testDataGenerator.getCurrentMode();
                            if (currentMode == TestDataGenerator.TestMode.OFF) {
                                // Default to NORMAL mode if not set
                                testDataGenerator.setCurrentMode(TestDataGenerator.TestMode.NORMAL);
                                updateTestModeUI(TestDataGenerator.TestMode.NORMAL);
                            }
                            // No need to start the test data generation here - it will be started 
                            // by the connection state change observer when connection completes
                        }
                        mockConnectionManager.connect(null); // null device is fine for mock
                    } else {
                        // in real mode check permissions and start real connection process
                        if (permissionManager.hasRequiredPermissions()) {
                            if (bluetoothAdapter != null) {
                                if (!bluetoothAdapter.isEnabled()) {
                                    requestBluetoothEnable();
                                } else {
                                    startScanAndConnect();
                                }
                            } else {
                                showToast("Bluetooth not available on this device");
                            }
                        } else {
                            Log.d(Constants.TAG_MAIN, "Requesting permissions before connection");
                            permissionManager.requestPermissions(this);
                        }
                    }
                    break;
            }
        } catch (SecurityException e) {
            //update : handle permission issues explicitly required by lint
            Log.e(Constants.TAG_MAIN, "Security exception in toggleConnection: " + e.getMessage());
            showToast("Permission denied: " + e.getMessage());
            updateConnectionUI(BleConnectionManager.ConnectionState.DISCONNECTED);
            
            if (!testModeActive) {
                // Only request permissions in real mode
                permissionManager.requestPermissions(this);
            }
        } catch (Exception e) {
            // Handle any other exceptions
            Log.e(Constants.TAG_MAIN, "Error in toggleConnection: " + e.getMessage());
            showToast("Error: " + e.getMessage());
            
            // Ensure UI is updated to a consistent state
            updateConnectionUI(BleConnectionManager.ConnectionState.DISCONNECTED);
        }
    }
    
    /**
     * start scanning for ESP32 device and connect to it when found
     * 
     */
    private void startScanAndConnect() {
        if (connectionManager == null) return;

        if (bluetoothAdapter == null) {
            showToast("Bluetooth not available on this device");
            updateConnectionUI(BleConnectionManager.ConnectionState.DISCONNECTED);
            return;
        }
                    
        if (!permissionManager.hasRequiredPermissions()) {
            Log.d(Constants.TAG_MAIN, "Requesting Bluetooth permissions");
            permissionManager.requestPermissions(this);
            return;
        }
        
        startScanAndConnectInternal();
    }
    
    /**
     * internal implementation of scan and connect process
     * this method assumes permissions have been checked already
     */
    private void startScanAndConnectInternal() {
        try {
            // Safe to call after permissions check
            if (!bluetoothAdapter.isEnabled()) {
                requestBluetoothEnable();
            return;
        }
              // update ui to show we're in scanning state
            updateConnectionUI(BleConnectionManager.ConnectionState.CONNECTING);
            
            // set timeout for scan operation
            final long SCAN_TIMEOUT = 10000; // 10 seconds
            connectionManager.scanForESP32(new BleConnectionManager.ScanResultCallback() {
                @Override
                public void onDeviceFound(BluetoothDevice device, ScanResult result) {

                    if (device != null) {
                        Log.d(Constants.TAG_MAIN, "ESP32 device found: " + 
                              device.getAddress() + ", connecting...");
                        connectionManager.connect(device);
                    }
                }
                
                @Override
                public void onScanFailed(int errorCode, String errorMessage) {
                    Log.e(Constants.TAG_MAIN, "Scan failed: " + errorMessage);
                    showToast("Scan failed: " + errorMessage);
                    updateConnectionUI(BleConnectionManager.ConnectionState.DISCONNECTED);
                }
                
                @Override
                public void onScanTimeout() {
                    Log.d(Constants.TAG_MAIN, "Scan timed out, no ESP32 devices found");
                    showToast("No ESP32 devices found nearby");
                    updateConnectionUI(BleConnectionManager.ConnectionState.DISCONNECTED);
                }
            }, SCAN_TIMEOUT);
            
        } catch (SecurityException e) {
            // explicitly handle SecurityException as required by lint
            Log.e(Constants.TAG_MAIN, "Security exception: " + e.getMessage());
            showToast("Permission denied: " + e.getMessage());
            updateConnectionUI(BleConnectionManager.ConnectionState.DISCONNECTED);
            
            // this shouldn't happen since we check permissions first, but handle it just in case
            permissionManager.requestPermissions(this);
                } catch (Exception e) {
            Log.e(Constants.TAG_MAIN, "Error starting scan: " + e.getMessage());
            showToast("Error: " + e.getMessage());
            updateConnectionUI(BleConnectionManager.ConnectionState.DISCONNECTED);
        }
    }
    
    /**
     * update the connection ui elements based on the current connection state
     * optimized for Android 12 on Pixel 4a
     */
    private void updateConnectionUI(BleConnectionManager.ConnectionState state) {
        if (isFinishing() || isDestroyed()) return;
        
        runOnUiThread(() -> {
            try {
                // update btn text based on state
                if (btnConnect != null) {
                    switch (state) {
                        case CONNECTED:
                            btnConnect.setText(R.string.disconnect);
                            btnConnect.setEnabled(true);
                            break;
                        case CONNECTING:
                            btnConnect.setText(R.string.cancel);
                            btnConnect.setEnabled(true);
                            break;
                        case DISCONNECTING:
                            btnConnect.setText(R.string.disconnecting);
                            btnConnect.setEnabled(false);
                            break;
                        case DISCONNECTED:
                            btnConnect.setText(R.string.connect);
                            btnConnect.setEnabled(true);
                            break;
                    }
                }
                
                // Update the connection indicator with appropriate color
                if (connectionIndicator != null) {
                    int drawableResId;
                    switch (state) {
                        case CONNECTED:
                            drawableResId = R.drawable.circle_green;
                            break;
                        case CONNECTING:
                            drawableResId = R.drawable.circle_yellow;
                            break;
                        case DISCONNECTING:
                        case DISCONNECTED:
                        default:
                            drawableResId = R.drawable.circle_red;
                            break;
                    }
                    connectionIndicator.setBackground(ContextCompat.getDrawable(this, drawableResId));
                }

                // Update status text
                if (tvStatus != null) {
                    String statusText;
                    if (testModeActive) {
                        statusText = getString(R.string.status_format, "TEST " + state.name());
                    } else {
                        statusText = getString(R.string.status_format, state.name());
                    }
                    tvStatus.setText(statusText);
                }

                // Update helper text
                if (connectionHelper != null) {
                    int helperTextResId;
                    switch (state) {
                        case CONNECTED:
                            helperTextResId = testModeActive ? 
                                R.string.helper_text_test_connected : 
                                R.string.helper_text_connected;
                            break;
                        case CONNECTING:
                            helperTextResId = testModeActive ? 
                                R.string.helper_text_test_connecting : 
                                R.string.helper_text_connecting;
                            break;
                        case DISCONNECTING:
                            helperTextResId = R.string.helper_text_disconnecting;
                            break;
                        case DISCONNECTED:
                            helperTextResId = testModeActive ? 
                                R.string.helper_text_test_disconnected : 
                                R.string.helper_text_disconnected;
                            break;
                        default:
                            helperTextResId = R.string.helper_text_disconnected;
                            break;
                    }
                    
                    // Check if the resource exists before trying to use it
                    try {
                        connectionHelper.setText(helperTextResId);
                    } catch (Resources.NotFoundException e) {
                        // Fallback to standard helper text if test-specific resources aren't defined
                        Log.d(Constants.TAG_MAIN, "Resource not found, using fallback: " + e.getMessage());
                        
                        switch (state) {
                            case CONNECTED:
                                connectionHelper.setText(R.string.helper_text_connected);
                                break;
                            case CONNECTING:
                                connectionHelper.setText(R.string.helper_text_connecting);
                                break;
                            case DISCONNECTING:
                                connectionHelper.setText(R.string.helper_text_disconnecting);
                                break;
                            case DISCONNECTED:
                                connectionHelper.setText(R.string.helper_text_disconnected);
                                break;
                            default:
                                connectionHelper.setText(R.string.helper_text_disconnected);
                                break;
                        }
                    }
                }
                
                // Update test mode indicator visibility
                if (tvTestMode != null && testModeActive && testDataGenerator != null) {
                    TestDataGenerator.TestMode currentMode = testDataGenerator.getCurrentMode();
                    if (currentMode != TestDataGenerator.TestMode.OFF) {
                        updateTestModeUI(currentMode);
                    }
                }
            } catch (Exception e) {
                Log.e(Constants.TAG_MAIN, "Error updating connection UI: " + e.getMessage());
            }
        });
    }

    private void updateSensorDisplays(SensorData data) {
        try {
            if (data.isDustData()) {
                String dustText = getString(R.string.dust_format, data.getValue());
                tvDust.setText(dustText);
                
                // Check if the value exceeds custom threshold
                float dustThreshold = getCustomDustThreshold();
                if (data.getValue() > dustThreshold) {
                    tvDust.setTextColor(Color.RED);
                } else {
                    tvDust.setTextColor(ContextCompat.getColor(this, R.color.sensor_value));
                }
                
                // Update dust meter visualization
                if (dustMeter != null) {
                    // Map dust value to progress (0-100)
                    int progress = (int) Math.min(100, Math.max(0, data.getValue() * 100 / Constants.DUST_MAX_VALUE));
                    dustMeter.setProgress(progress);
                }
                
                // Use our custom threshold handler
                handleDustSensorData(data);
            } else if (data.isNoiseData()) {
                String noiseText = getString(R.string.noise_format, data.getValue());
                tvNoise.setText(noiseText);
                
                // Check if the value exceeds custom threshold
                float noiseThreshold = getCustomNoiseThreshold();
                if (data.getValue() > noiseThreshold) {
                    tvNoise.setTextColor(Color.RED);
                } else {
                    tvNoise.setTextColor(ContextCompat.getColor(this, R.color.sensor_value));
                }
                
                // Update sound meter visualization
                if (soundMeter != null) {
                    // Map noise value to progress (0-100)
                    int progress = (int) Math.min(100, Math.max(0, data.getValue() * 100 / Constants.NOISE_THRESHOLD));
                    soundMeter.setProgress(progress);
                }
                
                // Use our custom threshold handler
                handleNoiseSensorData(data);
            } else if (data.isGasData()) {
                // Update gas value text
                String gasText = getString(R.string.gas_format, data.getValue());
                tvGasValue.setText(gasText);
                
                // Get custom threshold for gas
                float gasThreshold = getCustomGasThreshold();
                
                // Check if the value exceeds custom threshold
                if (data.getValue() > gasThreshold) {
                    tvGasValue.setTextColor(Color.RED);
                } else {
                    tvGasValue.setTextColor(ContextCompat.getColor(this, R.color.sensor_value));
                }
                
                // Update gas gauge visualization
                if (gasGauge != null) {
                    float gasValue = data.getValue();
                    gasGauge.speedTo(gasValue, 1000);
                    
                    // Update pointer color based on threshold
                    if (data.getValue() > gasThreshold) {
                        gasGauge.setPointerColor(Color.RED);
                    } else {
                        gasGauge.setPointerColor(Color.DKGRAY);
                    }
                }
                
                // Use our custom threshold handler
                handleGasSensorData(data);
            }
            
            // update db in background only save real data by default
            if (!data.isTestData()) {
                saveDataToDatabase(data);
            }
        } catch (Exception e) {
            Log.e(Constants.TAG_MAIN, "Error updating sensor displays: " + e.getMessage());
        }
    }

    /**
     * sensor data from BluetoothServiceIntegration
     *
     */
    @Override
    public void onSensorData(SensorData data, String sensorType) {
        if (data == null) {
            Log.w(Constants.TAG_MAIN, "Received null sensor data");
            return;
        }

        mainHandler.post(() -> {
            try {
                if (isFinishing() || isDestroyed()) {
                    Log.d(Constants.TAG_MAIN, "Activity finishing/destroyed, skipping UI update");
                    return;
                }
                
                // update ui
                updateSensorDisplays(data);
                
                // log4debug
                if (data.isTestData()) {
                    Log.d(Constants.TAG_MAIN, "Test data displayed: " + sensorType + "=" + data.getValue());
        } else {
                    Log.d(Constants.TAG_MAIN, "Sensor data displayed: " + sensorType + "=" + data.getValue());
                }
            } catch (Exception e) {
                Log.e(Constants.TAG_MAIN, "Error handling sensor data: " + e.getMessage());
            }
        });
    }

    /**
     * show a toast message
     */
    private void showToast(String message) {
        if (message == null || message.isEmpty()) return;
        
        runOnUiThread(() -> {
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
        });
    }

    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

                mainHandler.post(() -> {
                    switch (state) {
                        case BluetoothAdapter.STATE_OFF:
                            Log.d(Constants.TAG_MAIN, "Bluetooth turned off");
                            updateConnectionUI(BleConnectionManager.ConnectionState.DISCONNECTED);
                            connectionHelper.setText(R.string.bluetooth_disabled);
                            if (!isFinishing() && !isDestroyed()) {
                                showToast("Bluetooth turned off");
                            }
                            break;
                            
                        case BluetoothAdapter.STATE_TURNING_OFF:
                            Log.d(Constants.TAG_MAIN, "Bluetooth turning off");
                    break;
                    
                        case BluetoothAdapter.STATE_TURNING_ON:
                            Log.d(Constants.TAG_MAIN, "Bluetooth turning on");
                            connectionHelper.setText(R.string.bluetooth_enabling);
                    break;
                    
                        case BluetoothAdapter.STATE_ON:
                            Log.d(Constants.TAG_MAIN, "Bluetooth turned on");
                            boolean autoReconnect = getSharedPreferences("app_prefs", MODE_PRIVATE)
                                    .getBoolean("auto_reconnect", true);
                            
                            if (autoReconnect && connectionManager.getLastConnectedDevice() != null) {

                                mainHandler.postDelayed(() -> {
                                    if (!isFinishing() && !isDestroyed()) {
                                        startReconnectionProcess();
                                    }
                                }, 1500);
        } else {
                                connectionHelper.setText(R.string.ready_to_connect);
                            }
                            
                            // Only show toast if app is in foreground
                            if (!isFinishing() && !isDestroyed()) {
                                showToast("Bluetooth turned on");
                            }
                    break;
            }
        });
    }
        }
    };

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private void startReconnectionProcess() {

        BluetoothDevice lastDevice = connectionManager.getLastConnectedDevice();
        if (lastDevice == null) {
            Log.d(Constants.TAG_MAIN, "No previous device to reconnect to");
            return;
        }

        if (connectionManager.getCurrentState() == BleConnectionManager.ConnectionState.CONNECTING) {
            Log.d(Constants.TAG_MAIN, "Already in the process of connecting, skipping reconnection");
            return;
        }

        if (!permissionManager.hasRequiredPermissions()) {
            Log.d(Constants.TAG_MAIN, "Missing permissions for reconnection");
            permissionManager.requestPermissions(this);
            return;
        }
        
        try {

            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
                Log.d(Constants.TAG_MAIN, "Bluetooth not enabled, cannot reconnect");
                connectionHelper.setText(R.string.bluetooth_disabled);
                return;
            }

            updateConnectionUI(BleConnectionManager.ConnectionState.CONNECTING);
            showToast("Reconnecting to SmartHat...");

            Log.d(Constants.TAG_MAIN, "Attempting reconnection to: " + lastDevice.getAddress());
            connectionManager.connect(lastDevice);
            
        } catch (SecurityException e) {
            Log.e(Constants.TAG_MAIN, "Security exception during reconnection: " + e.getMessage());
            showToast("Permission denied: " + e.getMessage());
            updateConnectionUI(BleConnectionManager.ConnectionState.DISCONNECTED);
            permissionManager.requestPermissions(this);
        } catch (Exception e) {
            Log.e(Constants.TAG_MAIN, "Error during reconnection: " + e.getMessage());
            showToast("Error: " + e.getMessage());
            updateConnectionUI(BleConnectionManager.ConnectionState.DISCONNECTED);
        }
    }

    private void requestBluetoothEnable() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && 
                !permissionManager.hasBluetoothConnectPermission()) {
            Log.d(Constants.TAG_MAIN, "Requesting BLUETOOTH_CONNECT permission");
            permissionManager.requestRequiredPermissions();
            return;
        }
        
        try {
            // Use the activity result launcher to request Bluetooth enable
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            bluetoothEnableLauncher.launch(enableBtIntent);
        } catch (SecurityException e) {
            // this shouldnot happen since we check for permissions above
            // but handle it explicitly since required by lint
            Log.e(Constants.TAG_MAIN, "Security exception requesting Bluetooth enable: " + e.getMessage());
            showToast("Permission denied: " + e.getMessage());
            updateConnectionUI(BleConnectionManager.ConnectionState.DISCONNECTED);
            permissionManager.requestRequiredPermissions();
        } catch (Exception e) {
            Log.e(Constants.TAG_MAIN, "Error requesting Bluetooth enable: " + e.getMessage());
            showToast("Error: " + e.getMessage());
            updateConnectionUI(BleConnectionManager.ConnectionState.DISCONNECTED);
        }
    }

    private void saveDataToDatabase(SensorData data) {
        if (data == null) {
            Log.w(Constants.TAG_MAIN, "Attempted to save null data to database");
            return;
        }

        if (data.isTestData()) {
            Log.d(Constants.TAG_MAIN, "Skipping database save for test data");
            return;
        }
        // run database operations on background thread to avoid blocking the main thread
        dbExecutor.execute(() -> {
            try {
                long startTime = System.currentTimeMillis();
                
                // get the database helper singleton instance
                DatabaseHelper db = DatabaseHelper.getInstance();
                if (db == null) {
                    Log.e(Constants.TAG_MAIN, "Database helper is null");
                    return;
                }

                db.insertSensorData(data);
                
                // calculate operation time for performance tracking
                long operationTime = System.currentTimeMillis() - startTime;

                String sensorType = data.getSensorType(); // use getSensorType instead of getType
                Log.d(Constants.TAG_MAIN, "Saved " + sensorType + " data to database in " + operationTime + "ms");

                if (Math.random() < 0.01) {
                    Log.d(Constants.TAG_MAIN, "Running database maintenance");
                    db.performMaintenance();
                }
            } catch (Exception e) {
                Log.e(Constants.TAG_MAIN, "Error saving data to database: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public void onPermissionsGranted() {
        Log.d(Constants.TAG_MAIN, "Bluetooth permissions granted");
        // continue the operation that was waiting for permissions
        continueOperationAfterPermissions();
    }
    

    @Override
    public void onPermissionsDenied(boolean somePermissionsPermanentlyDenied) {
        Log.d(Constants.TAG_MAIN, "Bluetooth permissions denied");
        
        if (somePermissionsPermanentlyDenied) {
            showSettingsPermissionDialog();
            } else {
            updateConnectionUI(BleConnectionManager.ConnectionState.DISCONNECTED);
            showToast("Cannot connect without required permissions");
        }
    }
    

    private void continueOperationAfterPermissions() {
        if (connectionManager != null) {
            BleConnectionManager.ConnectionState currentState = connectionManager.getConnectionState().getValue();
            
            if (currentState == BleConnectionManager.ConnectionState.DISCONNECTED) {
                startScanAndConnectInternal();
            }
        }
    }

    // region test mode
    /**
     * set the test mode state and config the test data generation
     * simulates the connection 
     * optimized for Android 12 on Pixel 4a
     */
    private void setTestMode(TestDataGenerator.TestMode mode) {
        boolean wasTestModeActive = testModeActive;
        boolean enableTestMode = mode != TestDataGenerator.TestMode.OFF;

        // Initialize testDataGenerator if it doesn't exist
        if (testDataGenerator == null) {
            testDataGenerator = new TestDataGenerator();
            testDataGenerator.setListener(this);
        }
        
        // Always update the current mode, even if not changing active state
        if (testDataGenerator != null) {
            testDataGenerator.setCurrentMode(mode);
        }

        // Toggling between test mode and normal mode
        if (enableTestMode != wasTestModeActive) {
            // Check both connection managers to determine if we're in a disconnected state
            BleConnectionManager.ConnectionState realState = connectionManager.getCurrentState();
            BleConnectionManager.ConnectionState mockState = mockConnectionManager.getCurrentState();
            
            // Only allow mode switching when both managers are disconnected
            boolean canSwitchMode = realState == BleConnectionManager.ConnectionState.DISCONNECTED && 
                                   mockState == BleConnectionManager.ConnectionState.DISCONNECTED;
            
            if (canSwitchMode) {
                if (enableTestMode) {
                    // Switch to test mode
                    testDataGenerator.setMockBleManager(mockConnectionManager);
                    if (btIntegration != null) {
                        btIntegration.cleanup();
                    }
                    btIntegration = new BluetoothServiceIntegration(mockConnectionManager);
                    btIntegration.addSensorDataListener(this);
                    testModeActive = true;
                    updateTestModeUI(mode);
                    
                    // Ensure UI reflects the current mock connection state
                    updateConnectionUI(mockConnectionManager.getCurrentState());
                    showToast("Test mode enabled - Use Connect button to simulate connection");
                } else {
                    // Switch to real mode
                    if (testDataGenerator != null) {
                        testDataGenerator.stopTestMode();
                    }
                    if (btIntegration != null) {
                        btIntegration.cleanup();
                    }
                    btIntegration = new BluetoothServiceIntegration(connectionManager);
                    btIntegration.addSensorDataListener(this);
                    tvTestMode.setVisibility(View.GONE);
                    testModeActive = false;
                    
                    // Ensure UI reflects the current real connection state
                    updateConnectionUI(connectionManager.getCurrentState());
                    showToast("Test mode disabled - Real BLE connection mode");
                }
                
                // Re-setup observers for the new mode
                setupObservers();
            } else {
                // Revert test data generator's mode if can't change mode
                if (testDataGenerator != null) {
                    testDataGenerator.setCurrentMode(wasTestModeActive ? testDataGenerator.getCurrentMode() : TestDataGenerator.TestMode.OFF);
                }
                showToast("Please disconnect before changing test mode");
                invalidateOptionsMenu(); // Update menu to reflect reverted state
                return;
            }
        } else if (testModeActive && mode != TestDataGenerator.TestMode.OFF) {
            // Just changing test mode types while remaining in test mode
            updateTestModeUI(mode);
            
            // If already connected in test mode, restart data generation with new mode
            if (mockConnectionManager.getCurrentState() == BleConnectionManager.ConnectionState.CONNECTED) {
                if (testDataGenerator != null) {
                    testDataGenerator.stopTestMode(); // Stop current generation
                    testDataGenerator.startTestMode(mode); // Restart with new mode
                }
            }
        }
        
        invalidateOptionsMenu();
    }
    
    // legacy method for backward compatibility with older test implementation
    @Override
    public void onTestDataGenerated(SensorData data) {
        // for backward compatibility
        // the data will now flow through the BluetoothServiceIntegration
        if (data == null) return;
        
        // process the test data in the same way as real data
        onSensorData(data, data.getSensorType());
    }
    // endregion

    // Get custom dust threshold
    private float getCustomDustThreshold() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);
        return prefs.getFloat(Constants.PREF_DUST_THRESHOLD, Constants.DUST_THRESHOLD);
    }
    
    // Get custom noise threshold
    private float getCustomNoiseThreshold() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);
        return prefs.getFloat(Constants.PREF_NOISE_THRESHOLD, Constants.NOISE_THRESHOLD);
    }
    
    // Get custom gas threshold
    private float getCustomGasThreshold() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);
        return prefs.getFloat(Constants.PREF_GAS_THRESHOLD, Constants.GAS_THRESHOLD);
    }

    private void handleDustSensorData(SensorData data) {
        // Get custom threshold
        float dustThreshold = getCustomDustThreshold();
        
        // Check if value exceeds threshold
        if (data.getValue() > dustThreshold) {
            // Show alert
            if (notificationUtils != null) {
                notificationUtils.showDustAlert(data);
            }
            
            // Log for test data
            if (data.isTestData()) {
                Log.d(Constants.TAG_MAIN, "Test dust data triggered notification: " + data.getValue() + " µg/m³ (threshold: " + dustThreshold + " µg/m³)");
            } else {
                Log.d(Constants.TAG_MAIN, "Dust threshold exceeded: " + data.getValue() + " µg/m³ (threshold: " + dustThreshold + " µg/m³)");
            }
        }
    }

    private void handleNoiseSensorData(SensorData data) {
        // Get custom threshold
        float noiseThreshold = getCustomNoiseThreshold();
        
        // Check if noise exceeds threshold
        if (data.getValue() > noiseThreshold) {
            // Track sustained high noise
            long currentTime = System.currentTimeMillis();
            
            if (!isHighNoiseTracking) {
                // Start tracking high noise
                isHighNoiseTracking = true;
                highNoiseStartTime = currentTime;
                Log.d(Constants.TAG_MAIN, "Started tracking high noise level: " + data.getValue() + " dB (threshold: " + noiseThreshold + " dB)");
            } else if (currentTime - highNoiseStartTime >= SUSTAINED_NOISE_THRESHOLD_MS) {
                // High noise sustained for required duration, trigger alert
                if (notificationUtils != null) {
                    notificationUtils.showNoiseAlert(data);
                }
                
                // log for test data
                if (data.isTestData()) {
                    Log.d(Constants.TAG_MAIN, "Test noise data triggered notification after sustained period: " + data.getValue() + " dB (threshold: " + noiseThreshold + " dB)");
                } else {
                    Log.d(Constants.TAG_MAIN, "Noise data triggered notification after sustained period: " + data.getValue() + " dB (threshold: " + noiseThreshold + " dB)");
                }
                
                // Reset tracking timestamp to avoid constant alerts
                highNoiseStartTime = currentTime;
            }
        } else {
            // Reset tracking when noise drops below threshold
            if (isHighNoiseTracking) {
                isHighNoiseTracking = false;
                Log.d(Constants.TAG_MAIN, "Stopped tracking high noise, level dropped to: " + data.getValue() + " dB (below threshold: " + noiseThreshold + " dB)");
            }
        }
    }
    
    private void handleGasSensorData(SensorData data) {
        // Get custom threshold
        float gasThreshold = getCustomGasThreshold();
        
        // Check if value exceeds threshold
        if (data.getValue() > gasThreshold) {
            // Show alert
            if (notificationUtils != null) {
                notificationUtils.showGasAlert(data);
            }
            
            // Log for test data
            if (data.isTestData()) {
                Log.d(Constants.TAG_MAIN, "Test gas data triggered notification: " + data.getValue() + " ppm (threshold: " + gasThreshold + " ppm)");
            } else {
                Log.d(Constants.TAG_MAIN, "Gas threshold exceeded: " + data.getValue() + " ppm (threshold: " + gasThreshold + " ppm)");
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    // Add after the test mode region
    /**
     * Save the current test mode state and connection state to SharedPreferences
     * Optimized for Android 12 on Pixel 4a
     */
    private void saveTestModeState() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        // Save whether test mode is active
        editor.putBoolean(Constants.PREF_TEST_MODE_ACTIVE, testModeActive);
        
        // Save test mode type if active
        if (testModeActive && testDataGenerator != null) {
            TestDataGenerator.TestMode mode = testDataGenerator.getCurrentMode();
            editor.putString(Constants.PREF_TEST_MODE_TYPE, mode.name());
        }
        
        // Save connection state of the active manager
        BleConnectionManager activeManager = testModeActive ? mockConnectionManager : connectionManager;
        if (activeManager != null) {
            BleConnectionManager.ConnectionState state = activeManager.getCurrentState();
            editor.putString(Constants.PREF_CONNECTION_STATE, state.name());
        }
        
        editor.apply();
        Log.d(Constants.TAG_MAIN, "Saved test mode state: active=" + testModeActive + 
              ", connection=" + (activeManager != null ? activeManager.getCurrentState() : "unknown"));
    }
    
    /**
     * Restore the test mode state and connection state from SharedPreferences
     * Optimized for Android 12 on Pixel 4a
     */
    private void restoreTestModeState() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);
        
        // Get saved test mode active state (default to false if not found)
        boolean savedTestModeActive = prefs.getBoolean(Constants.PREF_TEST_MODE_ACTIVE, false);
        
        // Only restore if there's a change in the test mode state
        if (savedTestModeActive != testModeActive) {
            if (savedTestModeActive) {
                // Get the saved test mode type (default to NORMAL if not found)
                String modeStr = prefs.getString(Constants.PREF_TEST_MODE_TYPE, TestDataGenerator.TestMode.NORMAL.name());
                TestDataGenerator.TestMode mode;
                try {
                    mode = TestDataGenerator.TestMode.valueOf(modeStr);
                } catch (IllegalArgumentException e) {
                    // Fall back to NORMAL if there's an error parsing the mode
                    mode = TestDataGenerator.TestMode.NORMAL;
                }
                
                // Set the test mode
                setTestMode(mode);
            } else {
                // Turn off test mode if it was previously off
                setTestMode(TestDataGenerator.TestMode.OFF);
            }
        }
        
        // Get the saved connection state
        String savedStateStr = prefs.getString(Constants.PREF_CONNECTION_STATE, BleConnectionManager.ConnectionState.DISCONNECTED.name());
        BleConnectionManager.ConnectionState savedState;
        try {
            savedState = BleConnectionManager.ConnectionState.valueOf(savedStateStr);
        } catch (IllegalArgumentException e) {
            // Fall back to DISCONNECTED if there's an error parsing the state
            savedState = BleConnectionManager.ConnectionState.DISCONNECTED;
        }
        
        // Get the active manager based on current test mode
        BleConnectionManager activeManager = testModeActive ? mockConnectionManager : connectionManager;
        
        // If saved state was CONNECTED but current state is DISCONNECTED, try to reconnect in test mode
        if (testModeActive && savedState == BleConnectionManager.ConnectionState.CONNECTED && 
            activeManager.getCurrentState() == BleConnectionManager.ConnectionState.DISCONNECTED) {
            Log.d(Constants.TAG_MAIN, "Restoring test mode connection");
            mockConnectionManager.connect(null);
        }
        
        // Update UI with correct connection state
        updateConnectionUI(activeManager.getCurrentState());
        
        Log.d(Constants.TAG_MAIN, "Restored test mode state: active=" + testModeActive + 
              ", expected connection=" + savedState + 
              ", actual connection=" + activeManager.getCurrentState());
    }
}