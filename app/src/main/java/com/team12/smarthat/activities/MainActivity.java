package com.team12.smarthat.activities;
// might switch to ble later, will expand imports

//classic bluetooth kit

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
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

import java.io.IOException;
import java.util.List;
//ble note : currently spp , if switch -> wold need GATT callback in this class
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

    // region activation system spp
// system dialog result handler
private final ActivityResultLauncher<Intent> bluetoothEnableLauncher =
registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {

if (result.getResultCode() == RESULT_OK) { attemptDeviceConnection(); // case successful

         }  else { showToast("Bluetooth required for connection"); // case declined
     }});
     // endregion

    // region lifecycle methods
 @Override
  protected void onCreate(Bundle savedInstanceState)
 {
 super.onCreate(savedInstanceState);
setContentView(R.layout.activity_main);
initializeComponents();
setupUI();

 setupObservers();     // liveData listeners
  checkPermissions();     // runtime permissions verifications
     // ble note: will add loading state during init
 }

 @Override
protected void onDestroy() {
 super.onDestroy();
 cleanupResources();        // prevent resource/memory  leaks
//ble note: will add gat.disconnect()
    }

    // endregion

    // region component init
 private void initializeComponents() {
 //lifecycle for viewmodel
viewModel = new ViewModelProvider(this).get(BluetoothViewModel.class);
databaseHelper = new DatabaseHelper(this); // room later

 viewModel.initialize(databaseHelper);

bluetoothService = new BluetoothService(viewModel); // ble:would replce with bleService

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
// might add progress indicator for ui here
 }
    // endregion

     // region data observation
 private void setupObservers() {

 //state change monitor, update status disp
  viewModel.getConnectionState().observe(this, state -> {
 tvStatus.setText(state);
 updateConnectionButton(state); }); // toggle btn
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
    } // endregion

    // CORE LOGIC!!
 private void toggleConnectionState() {
if (viewModel.isConnected()) {
// disc flow
if (bluetoothService != null) {
   bluetoothService.disconnect(); //close socket
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
 return;}

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
   connectToDevice(); //main connection
    } catch (SecurityException e) {
     // android 12+?
    requestBluetoothPermissions();
        }
    }

  /** connects via spp, ble would need gatt stuff here */
    private void connectToDevice() {
 BluetoothDevice device = bluetoothManager.getPairedDevice();
  if (device == null) {
   showPairingDialog(); // case not paired
 return;
}
  try {
 BluetoothSocket socket = device.createRfcommSocketToServiceRecord(Constants.SPP_UUID);
 bluetoothService.connect(socket);
  viewModel.updateConnectionState(Constants.STATE_CONNECTED);
     } catch (IOException | SecurityException e) {
 viewModel.handleError("Connection failed: " + e.getMessage());
      }
    }
    // endregion

    // region permission handling
 private void checkPermissions() {
  List<String> neededPermissions = PermissionUtils.getRequiredPermissions(this);
   if (!neededPermissions.isEmpty()) {
     requestPermissions(neededPermissions.toArray(new String[0]),
    Constants.REQUEST_BLUETOOTH_PERMISSIONS);
        } }

    private void requestBluetoothPermissions() {
        // incase
        checkPermissions();
    }

 @Override
 public void onRequestPermissionsResult(int requestCode,
  @NonNull String[] permissions, @NonNull int[] grantResults) {
 super.onRequestPermissionsResult(requestCode, permissions, grantResults);
 if (requestCode == Constants.REQUEST_BLUETOOTH_PERMISSIONS) {
    for (int result : grantResults) {
     if (result != PackageManager.PERMISSION_GRANTED) {
showToast("Required permissions denied");
    finish();
  return;} }
      attemptDeviceConnection();} }
    // endregion

    // region data disp
    private void updateConnectionButton(String state) {
  if (Constants.STATE_CONNECTED.equals(state)) {
   btnConnect.setText(R.string.disconnect);
        } else {
   btnConnect.setText(R.string.connect);
        } }

 private void updateSensorDisplays(SensorData data) { runOnUiThread(() -> {
 if ("dust".equals(data.getSensorType())) {
     tvDust.setText(String.format("PM2.5: %.1f µg/m³", data.getValue()));
 } else if ("noise".equals(data.getSensorType()))
    { tvNoise.setText(String.format("Noise: %.1f dB", data.getValue()));
            }
        });
    }  //DOUBLE CHECK

private void checkThresholdAlerts(SensorData data) {
if ("dust".equals(data.getSensorType()) && data.getValue() > Constants.DUST_THRESHOLD) {
    notificationUtils.sendAlert("Dust Alert!",
  "High particulate levels detected: " + data.getValue());
        }
    }
    // endregion

    // region utilities
    private void showToast(String message) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }



    private void showPairingDialog() {
        new AlertDialog.Builder(this)
 .setTitle("Pair Device")
    .setMessage("Please pair with your SmartHat device first")
 .setPositiveButton("Open Settings", (d, w) ->
  startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)))
.setNegativeButton("Cancel", null)
 .show();
    }
 private void cleanupResources() {
 if (viewModel != null) {
  viewModel.cleanupResources();
        } //ble note: might add BluetoothGatt.close()
    }
    // endregion
}