import com.team12.smarthat.activities; // package
import android.Manifest; // constants for permissions

import android.bluetooth.BluetoothAdapter; // enable/disable HW management
import android.bluetooth.BluetoothDevice; // device identification
import android.bluetooth.BluetoothGattService;
import android.content.Intent; // prompt user
import android.content.pm.PackageManager; // permission check
import android.os.Bundle; // store state
import android.os.Handler; // scheduled tasks post permission
import android.os.Looper; // handler loop
import android.util.Log; // debugging
import android.widget.TextView; //ui layout
import android.widget.Toast; // popups
import android.widget.Button;

import androidx.annotation.Nullable; // avoid crash with no data
import androidx.appcompat.app.AppCompatActivity; // cute ui
import androidx.core.app.ActivityCompat; // run time permission
import androidx.core.content.ContextCompat; // pre request permission check
import com.team12.smarthat.R; // xml
import com.team12.smarthat.bluetooth.BluetoothService; // bluetooth connection logic
import com.team12.smarthat.utils.Constants;  // constants storage
import java.util.ArrayList; // updated just for oldies support might remove look at update in conditional in checkbluetootpermission
import java.util.List; // updated just for oldies support might remove
//this class (MainActivity) inherits parent AppCompatActivity looks!!
public class MainActivity extends AppCompatActivity{
    //constants
    private static final String TAG = "MainActivity"; // contant log tag debugging
    private static final int REQUEST_ENABLE_BT = 1; // contant for prompting user later
    private static final int REQUEST_BLUETOOTH_PERMISSIONS= 2; // runtime request

    //UI components
    private Button btnConnect; //declare btn
    private TextView tvStatus,tvDust,tvNoise; // tv vars

    // Connection retry
    private static final int MAX_RETRIES = 3; // will try another approach if this isn't enough (progressive delays maybe!?)
    private int retryCount = 0; // counter var

    //bluetooth
    private BluetoothDevice bluetoothService; // declare instance will use connection logic later
    private final Handler connectionHandler = new Handler(Looper.getMainLooper()); //new var declaration,store handler object,method call to wq21get main thread loop

    @Override
    protected void onCreate(Bundle saveInstanceState) {
        super.onCreate(savedInstanceState); // parent's method call, init activity & restore

        setContentView(R.layout.activity_main);// resource xml
        initializeUI();
        checkBluetoothPermissions();
        initializeBlutoothService();
    }
        private void initializeUI() {
            // grabbing stuff from xml
            btnConnect = findViewByID(R.id.btn_connect);
            tvStatus = findViewBy(R.id.tv_status);
            tvNoise = findViewById(R.id.tv_Noise);

            btnConnect.setOnClickListener(V -> handleConnection()); //calling method on our var btn, new method defined action passed

        }
        private void checkBluetoothPermissions() {
            // check if already granted -> skip if not ->send request
        //update: an error related to the BLUETOOTH_CONNECT permission requiring api level 31, but our app's minSdk is set to 24, i'll ask about the device we have but for now we add a conition before the request
        //updated:list to store missing permissions
        List<String> neededPermissions = new ArrayList<>();
//adding not granted s to the list
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH)
            != PackageManager.PERMISSION_GRANTED){
                neededPermissions.add(Manifest.permission.BLUETOOTH);}
            // updated for android api 31+ = S (12+ device):
        if(Build.VERSION.SDK_INT >= build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        }
        //if android11 or lower(api 30 or below) = R
        // location required for ble & classic scanning on older devices
if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.R){
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
    != PackageManager.PERMISSION_GRANTED) {
        neededPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
    }}
//request missing permissions from user
if (!neededPermissions.isEmpty()){
                ActivityCompat.requestPermission(
                        this,
                        neededPermissions.toArray(new String[0]), //list to string array

                        REQUEST_BLUETOOTH_PERMISSIONS //code request


                );
            }

private void initializeBluetoothService(){ //new instance of bluetooth service
bluetoothService = new BluetoothGattService(this, new BluetoothGattService.ConnectionCallback(){
    @Override
    public void onConnected(){
        //update ui main thread
        runOnUiThread(()->{
            tvStatus.setText(("Connected")
            btnConnect.setText("Disconnect");
            retryCount = 0; //reset rety counter
        });
        }
       //failed connection
       @Override
    public void onConnectionFailed(String error){

    }
})
            }
            }
        }