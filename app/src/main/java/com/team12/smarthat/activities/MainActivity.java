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
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

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
 * main activity is handling a lot might consider moving to different classses later

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
    
    // uI components
    private TextView tvStatus, tvDust, tvNoise, tvTestMode;
    private Button btnConnect;
    private View connectionIndicator;
    private TextView connectionHelper;
    
    // ble connection management
    private BleConnectionManager connectionManager;
    private BluetoothServiceIntegration btIntegration;
    
    // added to support switching between real and mock ble managers
    private MockBleConnectionManager mockConnectionManager;
    private boolean testModeActive = false;
    
    // region activation system
    private ActivityResultLauncher<Intent> bluetoothEnableLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            if (result.getResultCode() == Activity.RESULT_OK) {

                Log.d(Constants.TAG_MAIN, "Bluetooth enabled successfully via system dialog");
                startScanAndConnect();
            } else {
                // case user declined
                Log.d(Constants.TAG_MAIN, "User declined to enable Bluetooth via system dialog");
                showToast("Bluetooth is required to connect to SmartHat device");
                updateConnectionUI(BleConnectionManager.ConnectionState.DISCONNECTED);
            }
        }
    );
    // endregion

    //a singlethreadexecutor for db operations
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    private TestDataGenerator testDataGenerator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
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
        // notif state
        MenuItem notificationsItem = menu.findItem(R.id.action_notifications);
        if (notificationsItem != null && notificationUtils != null) {
            notificationsItem.setChecked(notificationUtils.areNotificationsEnabled());
            //update menu
            notificationsItem.setTitle(notificationsItem.isChecked() ? 
                    "Disable Notifications" : "Enable Notifications");
        }
        
        // update test mode menu items
        if (testDataGenerator != null) {
            TestDataGenerator.TestMode currentMode = testDataGenerator.getCurrentMode();
            MenuItem offItem = menu.findItem(R.id.action_test_mode_off);
            MenuItem normalItem = menu.findItem(R.id.action_test_mode_normal);
            MenuItem highDustItem = menu.findItem(R.id.action_test_mode_high_dust);
            MenuItem highNoiseItem = menu.findItem(R.id.action_test_mode_high_noise);
            MenuItem randomItem = menu.findItem(R.id.action_test_mode_random);
            
            if (offItem != null) offItem.setChecked(currentMode == TestDataGenerator.TestMode.OFF);
            if (normalItem != null) normalItem.setChecked(currentMode == TestDataGenerator.TestMode.NORMAL);
            if (highDustItem != null) highDustItem.setChecked(currentMode == TestDataGenerator.TestMode.HIGH_DUST);
            if (highNoiseItem != null) highNoiseItem.setChecked(currentMode == TestDataGenerator.TestMode.HIGH_NOISE);
            if (randomItem != null) randomItem.setChecked(currentMode == TestDataGenerator.TestMode.RANDOM);
        }
        
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.action_history) {
            // threshold history activity launch
            Intent historyIntent = new Intent(this, ThresholdHistoryActivity.class);
            startActivity(historyIntent);
            return true;
        } else if (id == R.id.action_notifications) {
            // toggle notif
            boolean newState = !item.isChecked();
            item.setChecked(newState);
            
            // update title
                item.setTitle(newState ? "Disable Notifications" : "Enable Notifications");
                

            // update notification state
                notificationUtils.setNotificationsEnabled(newState);
            
            // save preference
            getSharedPreferences("app_prefs", MODE_PRIVATE)
                    .edit()
                    .putBoolean(Constants.PREF_NOTIFICATIONS_ENABLED, newState)
                    .apply();
            
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
        } else if (id == R.id.action_test_mode_random) {
            setTestMode(TestDataGenerator.TestMode.RANDOM);
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        
     //bt state changes registrereceiver
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(bluetoothStateReceiver, filter);
        

        if (permissionManager.hasRequiredPermissions()) {
            try {
                // enable check
                if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
                    // update the ui
                    updateConnectionUI(BleConnectionManager.ConnectionState.DISCONNECTED);
                    connectionHelper.setText(R.string.bluetooth_disabled);
                } else if (connectionManager != null) {
                    updateConnectionUI(connectionManager.getCurrentState());
                    
                    // auto recon connected/ disc / previous connection exists
                    if (connectionManager.getCurrentState() == BleConnectionManager.ConnectionState.DISCONNECTED
                        && connectionManager.getLastConnectedDevice() != null) {
                        // reconnection after short delay
                        mainHandler.postDelayed(this::startReconnectionProcess, 1000);
                    }
                }
            } catch (SecurityException e) {
                // handle security exception from bt adapter operations
                Log.e(Constants.TAG_MAIN, "Security exception in onResume: " + e.getMessage());
                updateConnectionUI(BleConnectionManager.ConnectionState.DISCONNECTED);
            }
        } else {
            //no permissions yet just update ui
            updateConnectionUI(BleConnectionManager.ConnectionState.DISCONNECTED);
            connectionHelper.setText(R.string.permission_required);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        
        //unregiter bt state receiver
        try {
            unregisterReceiver(bluetoothStateReceiver);
        } catch (IllegalArgumentException e) {
            // ignore
            Log.d(Constants.TAG_MAIN, "BluetoothStateReceiver was not registered");
        }
        
        // cancel pending ui updates/ delays
        mainHandler.removeCallbacksAndMessages(null);

    }

    @Override
    protected void onStop() {
        super.onStop();

        // no auto disconnec to allow background operation
        // android 12 background restrictions spec
        
        // saving to restore
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
        // find ui components
        tvStatus = findViewById(R.id.tv_status);
        tvDust = findViewById(R.id.tv_dust);
        tvNoise = findViewById(R.id.tv_noise);
        tvTestMode = findViewById(R.id.tv_test_mode);
        btnConnect = findViewById(R.id.btn_connect);
        connectionIndicator = findViewById(R.id.connection_indicator);
        connectionHelper = findViewById(R.id.connection_helper);
        
        // init db helper singleton
        databaseHelper = DatabaseHelper.getInstance();
        
        // init bt system components
        systemBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (systemBluetoothManager != null) {
            bluetoothAdapter = systemBluetoothManager.getAdapter();
        }
        
        // init permission manager for android 12
        permissionManager = new BluetoothPermissionManager(this);
        
        //  singleton
        connectionManager = BleConnectionManager.getInstance(this, permissionManager);
        
        // don't use it yet
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

        // Observe the real connection manager
        connectionManager.getConnectionState().observe(this, state -> {
            if (!testModeActive) {
                updateConnectionUI(state);
            }
        });
        
        // Observe mock connection manager
        mockConnectionManager.getConnectionState().observe(this, state -> {
            if (testModeActive) {
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

        // Setup Bluetooth service integration
        btIntegration.observeConnectionState(this);
        
        Log.d(Constants.TAG_MAIN, "Observers setup complete");
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
            switch (currentState) {
                case CONNECTED:
                    Log.d(Constants.TAG_MAIN, "User requested disconnect from connected state");
                    activeManager.disconnect();
                    break;
                    
                case CONNECTING:
                    Log.d(Constants.TAG_MAIN, "User requested cancel during connection attempt");
                    showToast("Cancelling connection attempt...");
                    activeManager.disconnect();
                    break;
                    
                case DISCONNECTING:
                    Log.d(Constants.TAG_MAIN, "User pressed connect while disconnecting - ignoring");
                    showToast("Already disconnecting...");
                    break;
                    
                case DISCONNECTED:
                    Log.d(Constants.TAG_MAIN, "User requested connect from disconnected state");
                    
                    if (testModeActive) {
                        Log.d(Constants.TAG_MAIN, "Using mock connection in test mode");
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
            permissionManager.requestPermissions(this);
        }
    }
    
    /**
     * start scanning for ESP32 & connect to found device
     * updates for android 12 on Pixel 4a spec
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
     *
     * this method assumes permissions have been checked
     */
    private void startScanAndConnectInternal() {
        try {
            // Safe to call after permissions check
            if (!bluetoothAdapter.isEnabled()) {
                requestBluetoothEnable();
            return;
        }
              //scanning state
            updateConnectionUI(BleConnectionManager.ConnectionState.CONNECTING);
            
            //timeout
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
            // update handling SecurityException explicit required by lint
            Log.e(Constants.TAG_MAIN, "Security exception: " + e.getMessage());
            showToast("Permission denied: " + e.getMessage());
            updateConnectionUI(BleConnectionManager.ConnectionState.DISCONNECTED);
            
            // this shouldn't happen since we check permission but just in case
            permissionManager.requestPermissions(this);
                } catch (Exception e) {
            Log.e(Constants.TAG_MAIN, "Error starting scan: " + e.getMessage());
            showToast("Error: " + e.getMessage());
            updateConnectionUI(BleConnectionManager.ConnectionState.DISCONNECTED);
        }
    }
    
    /**
     * update the connection ui based on the current state
     *
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

                if (tvStatus != null) {
                    tvStatus.setText(getString(R.string.status_format, state.name()));
                }

                if (connectionHelper != null) {
                    int helperTextResId;
                    switch (state) {
                        case CONNECTED:
                            helperTextResId = R.string.helper_text_connected;
                            break;
                        case CONNECTING:
                            helperTextResId = R.string.helper_text_connecting;
                            break;
                        case DISCONNECTING:
                            helperTextResId = R.string.helper_text_disconnecting;
                            break;
                        case DISCONNECTED:
                            helperTextResId = R.string.helper_text_disconnected;
                            break;
                        default:
                            helperTextResId = R.string.helper_text_disconnected;
                            break;
                    }
                    connectionHelper.setText(helperTextResId);
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
                if (data.getValue() > Constants.DUST_THRESHOLD) { //test can trigger notif
                    notificationUtils.showDustAlert(data);
                    
                    // log for test data
                    if (data.isTestData()) {
                        Log.d(Constants.TAG_MAIN, "Test dust data triggered notification: " + data.getValue());
                    }
                }
            } else if (data.isNoiseData()) {

                String noiseText = getString(R.string.noise_format, data.getValue());
                tvNoise.setText(noiseText);

                if (data.getValue() > Constants.NOISE_THRESHOLD) {

                    notificationUtils.showNoiseAlert(data);
                    
                    // log for test data
                    if (data.isTestData()) {
                        Log.d(Constants.TAG_MAIN, "Test noise data triggered notification: " + data.getValue());
                    }
                }
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
        // avoid main thread block
        dbExecutor.execute(() -> {
            try {
                long startTime = System.currentTimeMillis();
                
                // Get the db helper
                DatabaseHelper db = DatabaseHelper.getInstance();
                if (db == null) {
                    Log.e(Constants.TAG_MAIN, "Database helper is null");
                    return;
                }

                db.insertSensorData(data);
                
                // calc operation time for performance tracking
                long operationTime = System.currentTimeMillis() - startTime;

                String sensorType = data.getSensorType(); //instead of getType
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
     * set the test mode state and config
     * sim connection workflow
     */
    private void setTestMode(TestDataGenerator.TestMode mode) {
        boolean wasTestModeActive = testModeActive;
        boolean enableTestMode = mode != TestDataGenerator.TestMode.OFF;

        // Initialize testDataGenerator if it doesn't exist
        if (enableTestMode && testDataGenerator == null) {
            testDataGenerator = new TestDataGenerator();
            testDataGenerator.setListener(this);
        }
        
        // Always update the current mode, even if not changing active state
        if (testDataGenerator != null) {
            testDataGenerator.setCurrentMode(mode);
        }

        // Toggling between test mode and normal mode
        if (enableTestMode != wasTestModeActive) {
            BleConnectionManager currentConnectionState = enableTestMode ? connectionManager : mockConnectionManager;
            BleConnectionManager.ConnectionState state = currentConnectionState.getCurrentState();
            
            if (state == BleConnectionManager.ConnectionState.DISCONNECTED) {
                if (enableTestMode) {
                    testDataGenerator.setMockBleManager(mockConnectionManager);
                    btIntegration.cleanup();
                    btIntegration = new BluetoothServiceIntegration(mockConnectionManager);
                    btIntegration.addSensorDataListener(this);
                    testModeActive = true;
                    updateTestModeUI(mode);
                    showToast("Test mode enabled - Use Connect button to simulate connection");
                } else {
                    if (testDataGenerator != null) {
                        testDataGenerator.stopTestMode();
                    }
                    btIntegration.cleanup();
                    btIntegration = new BluetoothServiceIntegration(connectionManager);
                    btIntegration.addSensorDataListener(this);
                tvTestMode.setVisibility(View.GONE);
                    testModeActive = false;
                    showToast("Test mode disabled - Real BLE connection mode");
                }
            } else {
                // Revert test data generator's mode if can't change mode
                if (testDataGenerator != null) {
                    testDataGenerator.setCurrentMode(wasTestModeActive ? testDataGenerator.getCurrentMode() : TestDataGenerator.TestMode.OFF);
                }
                showToast("Please disconnect before changing test mode");
                invalidateOptionsMenu(); // Update menu to reflect reverted state
                return;
            }
        }
        
        // Update test mode UI and functionality regardless of whether we were already in test mode
        if (testModeActive) {
            if (mode == TestDataGenerator.TestMode.OFF) {
                testDataGenerator.stopTestMode();
                tvTestMode.setVisibility(View.GONE);
                testModeActive = false;
            } else {
                updateTestModeUI(mode);
                
                // If already connected in test mode, restart data generation with new mode
                if (mockConnectionManager.getCurrentState() == BleConnectionManager.ConnectionState.CONNECTED) {
                    testDataGenerator.stopTestMode(); // Stop current generation
                    testDataGenerator.startTestMode(mode); // Restart with new mode
                    // Show toast to indicate mode change
                    showToast("Test mode changed to: " + mode.name());
                }
            }
        }
        
        // Force menu to update
        invalidateOptionsMenu();
    }
    
    // legacy method for backward compatibility
    @Override
    public void onTestDataGenerated(SensorData data) {
        // for backward compat
        // the data will flow through the BluetoothServiceIntegration now
        if (data == null) return;
        
        // process the test data in the same way as real data
        onSensorData(data, data.getSensorType());
    }
    // endregion
}