package com.team12.smarthat.activities;
// using ble for communication with the smarthat

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
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
import androidx.lifecycle.ViewModelProvider;

import com.team12.smarthat.R;
import com.team12.smarthat.database.DatabaseHelper;
import com.team12.smarthat.models.SensorData;
import com.team12.smarthat.utils.Constants;
import com.team12.smarthat.utils.NotificationUtils;
import com.team12.smarthat.utils.PermissionUtils;
import com.team12.smarthat.viewmodels.BluetoothViewModel;
import com.team12.smarthat.bluetooth.BluetoothService;
// Note: We use the fully qualified name for our custom BluetoothManager in the code to avoid conflicts

/**
 * Main activity for the SmartHat app
 * 
 * Handles UI interactions, Bluetooth connections, and sensor data visualization
 */
public class MainActivity extends AppCompatActivity {

    // View model for data sharing between fragments
    private BluetoothViewModel bluetoothViewModel;
    
    // Android system Bluetooth components
    private BluetoothManager systemBluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    
    // SmartHat custom Bluetooth components
    private com.team12.smarthat.bluetooth.BluetoothManager smartHatBluetoothManager;
    private BluetoothService bluetoothService;
    
    // Database helper
    private DatabaseHelper databaseHelper; // our local sqlite database access
    private NotificationUtils notificationUtils;
    
    // UI components
    private TextView tvStatus, tvDust, tvNoise;
    private Button btnConnect;
    private TextView tvTestMode; // Test mode indicator
    private View connectionIndicator; // Connection status indicator
    private TextView connectionHelper; // Connection helper text
    
    // region activation system
    private ActivityResultLauncher<Intent> bluetoothEnableLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            if (result.getResultCode() == Activity.RESULT_OK) {
                // Bluetooth was enabled, proceed with connection
                Log.d(Constants.TAG_MAIN, "Bluetooth enabled successfully via system dialog");
                connectToSmartHat();
            } else {
                // User declined to enable Bluetooth
                Log.d(Constants.TAG_MAIN, "User declined to enable Bluetooth via system dialog");
                showToast("Bluetooth is required to connect to SmartHat device");
                updateConnectionUI(Constants.STATE_DISCONNECTED);
            }
        }
    );
    // endregion

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Set title for the app
        setTitle("SmartHat");
        
        // Initialize components
        initializeComponents();
        
        // Setup UI components
        setupUI();
        
        // Setup observers for data changes
        setupObservers();
        
        // Check and request necessary permissions
        checkAndRequestPermissions();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem testModeItem = menu.findItem(R.id.action_test_mode);
        if (testModeItem != null && bluetoothService != null) {
            testModeItem.setChecked(bluetoothService.isTestModeEnabled());
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_test_mode) {
            boolean newState = !item.isChecked();
            item.setChecked(newState);
            
            if (bluetoothService != null) {
                if (newState) {

                    enableFullTestMode();
                } else {

                    bluetoothService.setTestMode(false);
                    
                    // Reset UI
                    showToast("Test mode disabled - Ready to connect to real device");
                    tvTestMode.setVisibility(View.GONE);
                    updateConnectionUI(Constants.STATE_DISCONNECTED);
                }
            }
            return true;
        } else if (id == R.id.action_history) {
            // threshold history activity launch
            Intent historyIntent = new Intent(this, ThresholdHistoryActivity.class);
            startActivity(historyIntent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothService != null) {
            bluetoothService.disconnect();
        }
        if (bluetoothViewModel != null) {
            bluetoothViewModel.cleanupResources();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == Constants.REQUEST_BLUETOOTH_CONNECT) {
            boolean allPermissionsGranted = true;
            boolean anyPermanentlyDenied = false;
            
            Log.d(Constants.TAG_MAIN, "Permission result received for " + permissions.length + " permissions");
            
            // Check if all requested permissions were granted
            for (int i = 0; i < permissions.length; i++) {
                boolean granted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                boolean permanentlyDenied = !granted && !shouldShowRequestPermissionRationale(permissions[i]);
                
                Log.d(Constants.TAG_MAIN, "Permission: " + permissions[i] + 
                      " - " + (granted ? "GRANTED" : "DENIED") + 
                      (permanentlyDenied ? " (PERMANENTLY)" : ""));
                
                if (!granted) {
                    allPermissionsGranted = false;
                    
                    // Check for permanent denial
                    if (permanentlyDenied) {
                        anyPermanentlyDenied = true;
                    }
                }
            }
            
            if (allPermissionsGranted) {
                // All permissions granted, connect to device
                Log.d(Constants.TAG_MAIN, "All required BLE permissions granted, connecting to device");
                connectToSmartHat();
            } else if (anyPermanentlyDenied) {
                // At least one permission was permanently denied, show settings dialog
                Log.d(Constants.TAG_MAIN, "Some permissions permanently denied, showing settings dialog");
                showSettingsPermissionDialog();
            } else {
                // Some permissions denied but not permanently, explain again
                Log.e(Constants.TAG_MAIN, "Some required BLE permissions denied, requesting again");
                showToast("Bluetooth permissions are required to connect to your SmartHat device");
                checkAndRequestPermissions(); // Try again with better explanation
            }
        }
    }
    
    /**
     * Shows a dialog explaining how to enable permissions from Settings
     * when user has permanently denied a permission
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
                updateConnectionUI(Constants.STATE_DISCONNECTED);
                showToast("Cannot connect without required permissions");
            })
            .setCancelable(false)
            .show();
    }
    
    private void checkAndRequestPermissions() {
        Log.d(Constants.TAG_MAIN, "Checking permissions on Android " + Build.VERSION.SDK_INT);
        
        // Use utility methods to check if we have necessary permissions
        boolean hasPermissions = PermissionUtils.hasRequiredBluetoothPermissions(this);
        Log.d(Constants.TAG_MAIN, "Has all required permissions: " + hasPermissions);
        
        if (hasPermissions) {
            // All permissions granted, connect to device
            Log.d(Constants.TAG_MAIN, "All required Bluetooth permissions granted, connecting to device");
            connectToSmartHat();
        } else {
            // Request appropriate permissions based on Android version
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Log.d(Constants.TAG_MAIN, "Missing permissions on Android 12+, showing explanation dialog");
                showBluetoothPermissionExplanationDialog();
            } else {
                Log.d(Constants.TAG_MAIN, "Missing permissions on pre-Android 12, showing explanation dialog");
                showLocationPermissionExplanationDialog();
            }
        }
    }
    
    /**
     * Shows an explanatory dialog before requesting Bluetooth permissions on Android 12+
     */
    private void showBluetoothPermissionExplanationDialog() {
        Log.d(Constants.TAG_MAIN, "Showing Bluetooth permission explanation dialog");
        new AlertDialog.Builder(this)
            .setTitle("Bluetooth permission required")
            .setMessage("SmartHat needs permission to access Bluetooth in order to connect to your SmartHat device.")
            .setCancelable(false)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                // Request necessary Bluetooth permissions
                Log.d(Constants.TAG_MAIN, "User accepted permission explanation, requesting permissions");
                requestPermissions(new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                }, Constants.REQUEST_BLUETOOTH_CONNECT);
            })
            .show();
    }
    
    /**
     * Shows an explanatory dialog before requesting Location permissions on Android 6-11
     */
    private void showLocationPermissionExplanationDialog() {
        Log.d(Constants.TAG_MAIN, "Showing Location permission explanation dialog");
        new AlertDialog.Builder(this)
            .setTitle("Location permission required")
            .setMessage("On Android 6-11, location permission is required to scan for Bluetooth devices. " +
                        "SmartHat doesn't track or store your location data.")
            .setCancelable(false)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                // Request location permission
                Log.d(Constants.TAG_MAIN, "User accepted location explanation, requesting permission");
                requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION
                }, Constants.REQUEST_BLUETOOTH_CONNECT);
            })
            .show();
    }

    // region component init
    private void initializeComponents() {

        bluetoothViewModel = new ViewModelProvider(this).get(BluetoothViewModel.class);
        

        tvStatus = findViewById(R.id.tv_status);
        tvDust = findViewById(R.id.tv_dust);
        tvNoise = findViewById(R.id.tv_noise);
        btnConnect = findViewById(R.id.btn_connect);
        tvTestMode = findViewById(R.id.tv_test_mode);
        connectionIndicator = findViewById(R.id.connection_indicator);
        connectionHelper = findViewById(R.id.connection_helper);
        

        databaseHelper = new DatabaseHelper(this);
        

        bluetoothViewModel.initialize(databaseHelper);
        

        systemBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (systemBluetoothManager != null) {
            bluetoothAdapter = systemBluetoothManager.getAdapter();
        }
        

        smartHatBluetoothManager = new com.team12.smarthat.bluetooth.BluetoothManager(this);
        
        // Initialize bte with our custom manager
        bluetoothService = new BluetoothService(bluetoothViewModel, smartHatBluetoothManager);
        
        // Initialize notification utils
        notificationUtils = new NotificationUtils(this);
    }
    // endregion

    // region ui config
    private void setupUI() {
        // Set initial text values for UI components
        tvDust.setText("Dust: 0.0 µg/m³");
        tvNoise.setText("Noise: 0.0 dB");
        
        // Hide test mode indicator initially
        tvTestMode.setVisibility(View.GONE);
        
        // Set up connect button click handler
        btnConnect.setOnClickListener(v -> toggleConnectionState());
    }
    // endregion

    // region observers
    private void setupObservers() {
        // Observe connection state changes
        bluetoothViewModel.getConnectionState().observe(this, this::updateConnectionUI);
        
        // Observe sensor data changes - observe both dust and noise separately
        bluetoothViewModel.getDustSensorData().observe(this, data -> {
            if (data != null) {
                updateSensorDisplays(data);
                
                // Check threshold and show notification if needed
                if (data.getValue() > Constants.DUST_THRESHOLD) {
                    notificationUtils.showDustAlert(data);
                }
            }
        });
        
        bluetoothViewModel.getNoiseSensorData().observe(this, data -> {
            if (data != null) {
                updateSensorDisplays(data);
                
                // Check threshold and show notification if needed
                if (data.getValue() > Constants.NOISE_THRESHOLD) {
                    notificationUtils.showNoiseAlert(data);
                }
            }
        });
        
        // Observe test mode changes
        bluetoothViewModel.getTestModeStatus().observe(this, this::updateTestModeDisplay);
        
        // Observe error messages
        bluetoothViewModel.getErrorMessage().observe(this, message -> {
            if (message != null && !message.isEmpty()) {
                showToast(message);
            }
        });
    }
    // endregion

    // region connection management
    /**
     * Toggle connection state between connected and disconnected
     */
    private void toggleConnectionState() {
        Log.d(Constants.TAG_MAIN, "Toggle connection state requested");
        int currentState = bluetoothViewModel.getConnectionState().getValue();
        
        if (currentState == Constants.STATE_CONNECTED) {
            // If connected, disconnect
            Log.d(Constants.TAG_MAIN, "Currently connected, disconnecting");
            if (bluetoothService != null) {
                bluetoothService.disconnect();
            }
        } else if (currentState == Constants.STATE_DISCONNECTED) {
            // If disconnected, start the connection process
            Log.d(Constants.TAG_MAIN, "Currently disconnected, starting connection process");
            
            // Use PermissionUtils to safely handle Bluetooth operations
            PermissionUtils.runWithBluetoothPermission(
                this,
                () -> {
                    // Check if Bluetooth is enabled and prompt if not
                    if (!isBluetoothEnabled()) {
                        promptEnableBluetooth();
                    } else {
                        // Bluetooth is enabled, proceed with connection
                        startConnectionProcess();
                    }
                },
                () -> {
                    // Handle permission errors
                    Log.e(Constants.TAG_MAIN, "Missing Bluetooth permissions, checking and requesting");
                    checkAndRequestPermissions();
                },
                true,  // Requires BLUETOOTH_CONNECT
                true   // Requires BLUETOOTH_SCAN for discovery
            );
        } else {
            // If connecting or disconnecting, don't do anything
            Log.d(Constants.TAG_MAIN, "Already connecting/disconnecting, ignoring toggle request");
        }
    }
    
    /**
     * Check if Bluetooth is enabled
     */
    private boolean isBluetoothEnabled() {
        boolean enabled = bluetoothAdapter != null && bluetoothAdapter.isEnabled();
        Log.d(Constants.TAG_MAIN, "Bluetooth enabled check: " + enabled);
        return enabled;
    }
    
    /**
     * Prompt the user to enable Bluetooth
     */
    private void promptEnableBluetooth() {
        Log.d(Constants.TAG_MAIN, "Prompting user to enable Bluetooth");
        
        // Use PermissionUtils to safely handle Bluetooth operations
        PermissionUtils.runWithBluetoothPermission(
            this,
            () -> {
                try {
                    // Make sure we have a valid adapter
                    if (bluetoothAdapter == null) {
                        Log.e(Constants.TAG_MAIN, "BluetoothAdapter is null, cannot enable Bluetooth");
                        showToast("Bluetooth not available on this device");
                        return;
                    }
                    
                    Log.d(Constants.TAG_MAIN, "Launching Bluetooth enable intent");
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    bluetoothEnableLauncher.launch(enableBtIntent);
                } catch (Exception e) {
                    Log.e(Constants.TAG_MAIN, "Error requesting Bluetooth enable: " + e.getMessage(), e);
                    showToast("Error requesting Bluetooth enable: " + e.getMessage());
                }
            },
            () -> {
                // Handle permission errors
                Log.e(Constants.TAG_MAIN, "BLUETOOTH_CONNECT permission denied, cannot enable Bluetooth");
                checkAndRequestPermissions();
            },
            true,  // Requires BLUETOOTH_CONNECT
            false  // Doesn't require BLUETOOTH_SCAN
        );
    }
    
    /**
     * Start the connection process by checking permissions first
     */
    private void startConnectionProcess() {
        Log.d(Constants.TAG_MAIN, "Starting connection process");
        
        // Check if bluetooth adapter is available
        if (bluetoothAdapter == null) {
            Log.e(Constants.TAG_MAIN, "BluetoothAdapter not available");
            showToast("Bluetooth not available on this device");
            return;
        }
        
        // Call directly to permission check, which will connect if permissions granted
        checkAndRequestPermissions();
    }
    
    private void connectToSmartHat() {
        Log.d(Constants.TAG_MAIN, "Attempting to connect to SmartHat device");
        showToast("Connecting to SmartHat...");
        
        if (bluetoothService != null) {
            // Use PermissionUtils to safely handle connection operation
            PermissionUtils.runWithBluetoothPermission(
                this,
                () -> {
                    // Try to connect to the specific SmartHat device by MAC address
                    Log.d(Constants.TAG_MAIN, "BluetoothService available, calling connectToSpecificSmartHat()");
                    bluetoothService.connectToSpecificSmartHat();
                },
                () -> {
                    Log.e(Constants.TAG_MAIN, "Bluetooth permissions denied, cannot connect to SmartHat");
                    showToast("Bluetooth permissions required to connect to SmartHat");
                    checkAndRequestPermissions();
                },
                true,  // Requires BLUETOOTH_CONNECT
                true   // Requires BLUETOOTH_SCAN for discovery
            );
        } else {
            Log.e(Constants.TAG_MAIN, "BluetoothService not initialized");
            showToast("Error: Bluetooth service not available");
        }
    }
    
    /**
     * Enable test mode with simulated data
     * @param fullyEnabled if true, connect automatically and generate data, otherwise just prepare test mode
     */
    private void enableTestMode(boolean fullyEnabled) {
        if (bluetoothService == null) {
            Log.e(Constants.TAG_MAIN, "BluetoothService not available for test mode");
            return;
        }
        
        Log.d(Constants.TAG_MAIN, "Setting up test mode (fully enabled: " + fullyEnabled + ")");
        
        // First disconnect from any real device
        bluetoothService.disconnect();
        
        if (fullyEnabled) {
            // Enable test mode and connect
            bluetoothService.setTestMode(true);
            bluetoothService.forceGenerateTestData();
            
            // Update UI for connected state
            tvTestMode.setVisibility(View.VISIBLE);
            tvTestMode.setText("TEST MODE");
            updateConnectionUI(Constants.STATE_CONNECTED);
            showToast("Test mode enabled - using simulated data");
        } else {
            // Just prepare test mode without connecting
            bluetoothService.prepareTestMode();
            
            // Update UI for disconnected state
            tvTestMode.setVisibility(View.VISIBLE);
            tvTestMode.setText("DEMO MODE");
            updateConnectionUI(Constants.STATE_DISCONNECTED);
            showToast("Demo mode ready - press connect to start simulation");
        }
    }

    /**
     * Enable full test mode with automatic connection
     */
    private void enableFullTestMode() {
        enableTestMode(true);
    }

    /**
     * Prepare test mode without connecting, so user can control connection
     */
    private void prepareTestMode() {
        enableTestMode(false);
    }
    // endregion
    
    // region ui updates
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
    
    private void updateConnectionUI(int connectionState) {
        runOnUiThread(() -> {
            switch (connectionState) {
                case Constants.STATE_CONNECTED:
                    tvStatus.setText(R.string.connected);
                    tvStatus.setTextColor(ContextCompat.getColor(this, R.color.connected_green));
                    btnConnect.setText(R.string.disconnect);
                    connectionIndicator.setBackgroundResource(R.drawable.circle_green);
                    connectionHelper.setText(R.string.helper_connected);
                    break;
                    
                case Constants.STATE_CONNECTING:
                    tvStatus.setText(R.string.connecting);
                    tvStatus.setTextColor(ContextCompat.getColor(this, R.color.connecting_yellow));
                    btnConnect.setText(R.string.cancel);
                    connectionIndicator.setBackgroundResource(R.drawable.circle_yellow);
                    connectionHelper.setText(R.string.helper_connecting);
                    break;
                    
                case Constants.STATE_DISCONNECTED:
                default:
                    tvStatus.setText(R.string.disconnected);
                    tvStatus.setTextColor(ContextCompat.getColor(this, R.color.disconnected_red));
                    btnConnect.setText(R.string.connect);
                    connectionIndicator.setBackgroundResource(R.drawable.circle_red);
                    connectionHelper.setText(R.string.helper_disconnected);
                    break;
            }
        });
    }
    
    private void updateSensorDisplays(SensorData data) {
        if (data == null) return;

        String type = data.getSensorType();
        float value = data.getValue();
        
        // Add test mode indicator if this is test data
        String sourceIndicator = data.isTestData() ? " [TEST]" : "";
        
        if ("dust".equalsIgnoreCase(type)) {
            tvDust.setText(String.format("Dust: %.1f µg/m³%s", value, sourceIndicator));
        } else if ("noise".equalsIgnoreCase(type)) {
            tvNoise.setText(String.format("Noise: %.1f dB%s", value, sourceIndicator));
        }
    }
    
    private void updateTestModeDisplay(Boolean testModeEnabled) {
        if (testModeEnabled != null) {
            if (testModeEnabled) {
                tvTestMode.setVisibility(View.VISIBLE);
                tvTestMode.setText("TEST MODE");
            } else {
                tvTestMode.setVisibility(View.GONE);
            }
        }
    }
    // endregion
}