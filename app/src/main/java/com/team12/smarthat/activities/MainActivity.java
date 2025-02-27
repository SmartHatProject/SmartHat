package com.team12.smarthat.activities;

import android.Manifest; // constants for permissions

import android.bluetooth.BluetoothAdapter; // enable/disable HW management
//import android.bluetooth.BluetoothDevice; // device identification , not used for now might removee lster

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent; // prompt user
import android.content.IntentFilter;
import android.content.pm.PackageManager; // permission check
import android.os.Bundle; // store state
import android.os.Handler; // scheduled tasks post permission
import android.os.Looper; // handler loop
import android.os.Build; // device & os info
import android.util.Log; // debugging
import android.widget.TextView; //ui layout
import android.widget.Toast; // popups
import android.widget.Button;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher; // launch and callback
import androidx.activity.result.contract.ActivityResultContracts; // handle enable result
import androidx.annotation.Nullable; // avoid crash with no data
import androidx.appcompat.app.AppCompatActivity; // compat ui
import androidx.core.app.ActivityCompat; // run time permission
import androidx.core.content.ContextCompat; // pre request permission check
import androidx.lifecycle.ViewModelProvider;

import com.team12.smarthat.R; // xml
import com.team12.smarthat.bluetooth.BluetoothService; // bluetooth connection logic
import com.team12.smarthat.utils.Constants;  // constants storage
import com.team12.smarthat.viewmodels.BluetoothViewModel; // logic & livedata

import java.util.ArrayList; // updated just for oldies support might remove look at update in conditional in checkbluetootpermission
import java.util.List; // updated just for oldies support might remove

//this class (MainActivity) inherits parent AppCompatActivity looks!!
public class MainActivity extends AppCompatActivity {
    // updated arch


    //constants
    private static final String TAG = "MainActivity"; // contant log tag debugging
    private static final int REQUEST_ENABLE_BT = 1; // contant for prompting user later
    //private static final int REQUEST_BLUETOOTH_PERMISSIONS= 2; // runtime request
//spp
    private final BroadcastReceiver pairingReceiver = createPairingReceiver();
    private static final long CONNECTION_TIMEOUT_MS = 15000;
    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());

    //UI components
    private Button btnConnect; //declare btn
    private TextView tvStatus, tvDust, tvNoise; // tv vars
    private BluetoothViewModel bluetoothViewModel; //update: switched from bluetooth service to this for now, bluetooth service commented out
    private NotificationUtils notificationUtils;
    //bluetooth activation
    private final ActivityResultLauncher<Intent> enableBluetoothLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) {
            handleBluetoothEnabled();
        } else {
            showToast(getString(R.string.bluetooth_required));
        }
    });
    // Connection retry
    //private static final int MAX_RETRIES = 3; // will try another approach if this isn't enough (progressive delays maybe!?)
    //private int retryCount = 0; // counter var

    //bluetooth
    //private BluetoothService bluetoothService; // declare instance will use connection logic later
    //private final Handler connectionHandler = new Handler(Looper.getMainLooper()); //new var declaration,store handler object,method call to wq21get main thread loop

    @Override
    protected void onCreate(Bundle saveInstanceState) {
        super.onCreate(savedInstanceState); // parent's method call, init activity & restore

        setContentView(R.layout.activity_main);// resource xml

        // initializeBlutoothService();
        notificationUtils = new NotificationUtils(this);
        registerReceiver(pairingReceiver, new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED));
        requestNotificationPermission();
        initializeUI();
        setupViewModel();
        checkBluetoothPermissions();
    }

    private void handleBluetoothEnabled() {
        Log.i(Constants.TAG_BLUETOOTH, "Bluetooth enabled, ready to connect.");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(pairingReceiver);
        timeoutHandler.removeCallbacksAndMessages(null);
        if (bluetoothViewModel != null) {
            bluetoothViewModel.cleanupResources();
        }
    }

    private void initializeUI() {
        // grabbing stuff from xml
        btnConnect = findViewById(R.id.btn_connect);
        tvStatus = findViewById(R.id.tv_status);
        tvNoise = findViewById(R.id.tv_noise);
        tvDust = findViewById(R.id.tv_dust);

        btnConnect.setOnClickListener(v -> handleConnection()); //calling method on our var btn, new method defined action passed

    }

    private void setupViewModel() {
        bluetoothViewModel = new ViewModelProvider(this).get(BluetoothViewModel.class);
        bluetoothViewModel.getConnectionState().observe(this, state -> {
            switch (state) {
                case CONNECTING:

                    updateUI(getString(R.string.connecting), false, R.string.connect);
                    break;
                case CONNECTED:
                    updateUI(getString(R.string.connected), true, R.string.disconnect);
                    break;
                case DISCONNECTED:

                    updateUI(getString(R.string.disconnected), true, R.string.connect);
                    break;
            }
        });
        bluetoothViewModel.getSensorData().observe(this, json -> {
            try {
                JSONObject data = new JSONObject(json);
                String type = data.getString("sensor");
                float value = (float) data.getDouble("value");
                if (type.equals("dust")) {
                    tvDust.setText(getString(R.string.dust_display, value));
                    checkDustThreshold(value);
                } else if (type.equals("noise")) {
                    tvNoise.setText(getString(R.string.noise_display, value));
                    checkNoiseThreshold(value);
                }

            } catch (JSONException e) {
                Log.e(Constants.TAG_BLUETOOTH, "JSON error" + e.getMessage());
            }
        });
    }

    private void handleBluetoothEnabled() {
        Log.i(Constants.TAG_BLUETOOTH, "Bluetooth enabled, ready to connect.");
        bluetoothViewModel.connectToDevice(Constants.ESP32_MAC_ADDRESS); // start connection after enabling Bluetooth
    }

    private void handleConnection() {
        if (bluetoothViewModel.isConnected()) {
            bluetoothViewModel.disconnectDevice();
        } else {
            checkPairingAndConnect();
        }
    }

    private void checkPairingAndConnect() {
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(Constants.ESP32_MAC_ADDRESS);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // LOGCAT FIX : TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        if (device.getBondState() == BluetoothDevice.BOND_BONDED) {

            attemptConnection();
        } else {
            showPairingDialog(device);
        }
    }

    private void attemptConnection() {
        if (validateBluetoothAdapter()) {
            bluetoothViewModel.connectToDevice(Constants.ESP32_MAC_ADDRESS);
            startConnectionTimer();
        }
    }

    private void startConnectionTimer() { //update: instead of retry that commented out using this
        timeoutHandler.postDelayed(() -> {
            if (!bluetoothViewModel.isConnected()) {
                showToast(getString(R.string.connection_timeout));
                bluetoothViewModel.disconnectDevice();
            }
        }, CONNECTION_TIMEOUT_MS);
    }

    private BroadcastReceiver createPairingReceiver() {
        return new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())) {
                    int state = intent.getIntExtra(
                            BluetoothDevice.EXTRA_BOND_STATE, -1);
                    if (state == BluetoothDevice.BOND_BONDED) {
                        attemptConnection();
                    }
                }
            }
        };
    }

    private void handleBluetoothEnabled() {
        Log.i(Constants.TAG_BLUETOOTH, "Bluetooth enabled, ready to connect.");
        bluetoothViewModel.connectToDevice(Constants.ESP32_MAC_ADDRESS); // Start connection after enabling Bluetooth
    }

    private void showPairingDialog(BluetoothDevice device) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // LOGCAT FIX: TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Pair With Device")
                .setMessage("Pair with " + device.getName() + " to continue")
                .setPositiveButton("Pair", (d, w) -> device.createBond())
                .setNegativeButton("Cancel", null)
                .show();
      }
        private void checkBluetoothPermissions() {
            // check if already granted -> skip if not ->send request
            //update: an error related to the BLUETOOTH_CONNECT permission requiring api level 31, but our app's minSdk is set to 24, i'll ask about the device we have but for now we add a conition before the request
            //updated:list to store missing permissions
            List<String> neededPermissions = new ArrayList<>();
//adding not granted s to the list
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH)
                    != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(Manifest.permission.BLUETOOTH);
            }
            // updated for android api 31+ = S (12+ device):
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
                    neededPermissions.add(Manifest.permission.BLUETOOTH_CONNECT);
                }
            }
            //if android11 or lower(api 30 or below) = R
            // location required for ble & classic scanning on older devices
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
                    neededPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
                }
            }
//request missing permissions from user
            if (!neededPermissions.isEmpty()) {
                ActivityCompat.requestPermissions(
                        this,
                        neededPermissions.toArray(new String[0]), //list to string array

                        Constants.REQUEST_BLUETOOTH_PERMISSIONS //code request


                );
            }
        }}
//private void initializeBluetoothService(){ //new instance of bluetooth service
//bluetoothService = new BluetoothService(this, new BluetoothService.ConnectionCallback(){
   // @Override
   // public void onConnected(){
        //update ui main thread
       // runOnUiThread(()->{
           // tvStatus.setText(("Connected");
           // btnConnect.setText("Disconnect");
           // retryCount = 0; //reset rety counter
       // });
      //  }
       //failed connection
     //  @Override
 //   public void onConnectionFailed(String error){

