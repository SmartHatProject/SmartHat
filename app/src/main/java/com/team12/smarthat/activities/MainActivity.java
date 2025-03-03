package com.team12.smarthat.activities;
// using ble for communication with the smarthat

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.team12.smarthat.R;
import com.team12.smarthat.bluetooth.BluetoothManager;
import com.team12.smarthat.bluetooth.BluetoothService;
import com.team12.smarthat.database.DatabaseHelper;
import com.team12.smarthat.models.SensorData;
import com.team12.smarthat.utils.Constants;
import com.team12.smarthat.utils.NotificationUtils;
import com.team12.smarthat.utils.PermissionUtils;
import com.team12.smarthat.viewmodels.BluetoothViewModel;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    // region class components
    private BluetoothViewModel viewModel;
    private final BluetoothManager bluetoothManager = new BluetoothManager(this);
    private BluetoothService bluetoothService;
    private DatabaseHelper databaseHelper; // our local sqlite database access
    private NotificationUtils notificationUtils;
    // endregion

    // region ui components
    private TextView tvStatus, tvDust, tvNoise;
    private Button btnConnect;
    // endregion

    // region activation system
    // system dialog result handler
    private final ActivityResultLauncher<Intent> bluetoothEnableLauncher =
    registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) { 
            attemptDeviceConnection(); // case successful
        } else { 
            showToast("Bluetooth required for connection"); // case declined
        }
    });
    // endregion

    // region lifecycle methods
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initializeComponents();
        setupUI();
        setupObservers();     // liveData listeners
        checkPermissions();   // runtime permissions verifications
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanupResources();   // prevent resource/memory leaks
    }
    // endregion

    // region component init
    private void initializeComponents() {
        //lifecycle for viewmodel
        viewModel = new ViewModelProvider(this).get(BluetoothViewModel.class);
        databaseHelper = new DatabaseHelper(this); // room later

        viewModel.initialize(databaseHelper);

        // passing bluetoothManager to service for ble ops
        bluetoothService = new BluetoothService(viewModel, bluetoothManager);

        notificationUtils = new NotificationUtils(this);
    }
    // endregion

    // region ui config
    private void setupUI() {
        tvStatus = findViewById(R.id.tv_status);
        tvDust = findViewById(R.id.tv_dust);
        tvNoise = findViewById(R.id.tv_noise);

        btnConnect = findViewById(R.id.btn_connect);

        // btn click connection logic
        btnConnect.setOnClickListener(v -> toggleConnectionState());
    }
    // endregion

    // region data observation
    private void setupObservers() {
        //state change monitor, update status disp
        viewModel.getConnectionState().observe(this, state -> {
            tvStatus.setText(state);
            updateConnectionButton(state);
        }); // toggle btn
        
        // new data received
        viewModel.getSensorData().observe(this, sensorData -> {
            updateSensorDisplays(sensorData);
            checkThresholdAlerts(sensorData);
        });

        // error msg from viewmodel
        viewModel.getErrorMessage().observe(this, error -> {
            showToast(error);
            notificationUtils.sendAlert("Device Error", error);
        });
    } 
    // endregion

    // region connection handling
    private void toggleConnectionState() {
        if (viewModel.isConnected()) {
            // disconnect flow
            if (bluetoothService != null) {
                bluetoothService.disconnect(); // close gatt connection
            }
            //update state in viewmodel
            viewModel.disconnectDevice();
        } else {
            startConnectionProcess();
        }
    }
    
    private void startConnectionProcess() {
        // bt check
        if (!bluetoothManager.isBluetoothSupported()) {
            showToast("Bluetooth not available");
            return;
        }

        // bt enable?
        if (!bluetoothManager.isBluetoothEnabled()) {
            bluetoothEnableLauncher.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            return;
        }

        // connect
        attemptDeviceConnection();
    }

    private void attemptDeviceConnection() {
        try {
            // we're using ble scanning to find our device
            startBleScan();
        } catch (SecurityException e) {
            // android 12+ permissions issue
            requestBluetoothPermissions();
        }
    }

    /** starts ble scan for devices */
    private void startBleScan() {
        // Start scanning for our device
        showToast("Scanning for SmartHat device...");
        bluetoothService.startScan();  
    }
    
    // still keeping in case we want direct connection
    private void connectToDevice() {
        BluetoothDevice device = bluetoothManager.getPairedDevice();
        if (device == null) {
            showPairingDialog(); // case not paired
            return;
        }
        bluetoothService.connect(device);
    }
    // endregion

    // region permission handling
    private void checkPermissions() {
        List<String> neededPermissions = PermissionUtils.getRequiredPermissions(this);
        if (!neededPermissions.isEmpty()) {
            requestPermissions(neededPermissions.toArray(new String[0]),
                Constants.REQUEST_BLUETOOTH_PERMISSIONS);
        } 
    }

    private void requestBluetoothPermissions() {
        // incase
        checkPermissions();
    }
    
    // region request result callbacks
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == Constants.REQUEST_BLUETOOTH_PERMISSIONS) {
            // check if all permissions were granted
            if (grantResults.length > 0 && allPermissionsGranted(grantResults)) {
                // retry now that permissions granted
                attemptDeviceConnection();
            } else {
                // find which permission was denied
                for (int i = 0; i < permissions.length; i++) {
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        if (permissions[i].equals(Manifest.permission.ACCESS_FINE_LOCATION) || 
                            permissions[i].equals(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                            showToast("Location permission required for BLE scanning. Please select 'Allow while using the app'");
                            return;
                        } else if (permissions[i].equals(Manifest.permission.BLUETOOTH_SCAN)) {
                            showToast("Bluetooth scanning permission required");
                            return;
                        } else if (permissions[i].equals(Manifest.permission.BLUETOOTH_CONNECT)) {
                            showToast("Bluetooth connection permission required");
                            return;
                        }
                    }
                }
                showToast("All Bluetooth permissions required for connection");
            }
        }
    }
    // endregion
    
    // region helpers
    private boolean allPermissionsGranted(int[] grantResults) {
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
    
    private void showPairingDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Device Not Paired")
                .setMessage("Please pair with the SmartHat device in Bluetooth settings first.")
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    Intent intent = new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
    
    private void updateConnectionButton(String state) {
        if (state.equals(Constants.STATE_CONNECTED)) {
            btnConnect.setText("Disconnect");
        } else {
            btnConnect.setText("Connect");
        }
    }
    
    private void updateSensorDisplays(SensorData data) {
        String sensorType = data.getSensorType();
        float value = data.getValue();
        
        if (sensorType.equalsIgnoreCase("dust")) {
            tvDust.setText(String.format("Dust: %.2f µg/m³", value));
        } else if (sensorType.equalsIgnoreCase("noise")) {
            tvNoise.setText(String.format("Noise: %.2f dB", value));
        }
    }
    
    private void checkThresholdAlerts(SensorData data) {
        String sensorType = data.getSensorType();
        float value = data.getValue();
        
        if (sensorType.equalsIgnoreCase("dust") && value > Constants.DUST_THRESHOLD) {
            notificationUtils.sendAlert("Dust Warning", 
                    String.format("Dust level (%.2f µg/m³) exceeds threshold", value));
        } else if (sensorType.equalsIgnoreCase("noise") && value > Constants.NOISE_THRESHOLD) {
            notificationUtils.sendAlert("Noise Warning", 
                    String.format("Noise level (%.2f dB) exceeds threshold", value));
        }
    }
    
    private void cleanupResources() {
        if (bluetoothService != null) {
            bluetoothService.disconnect();
        }
        viewModel.cleanupResources();
    }
}