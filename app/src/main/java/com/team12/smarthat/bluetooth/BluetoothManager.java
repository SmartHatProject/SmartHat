package com.team12.smarthat.bluetooth;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.core.content.ContextCompat;
import com.team12.smarthat.utils.Constants;
import com.team12.smarthat.viewmodels.BluetoothViewModel;

import java.util.ArrayList;
import java.util.List;

public class BluetoothManager {
    private final BluetoothAdapter bluetoothAdapter;
    private final Context context;
    private BluetoothLeScanner bluetoothLeScanner;
    private boolean scanning;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothGatt bluetoothGatt;
    private BluetoothViewModel viewModel;
    
    // callbacks so we can return to service
    private BluetoothGattCallback gattCallback;
    private ScanCallback scanCallback;
    private BluetoothDevice targetDevice;

    public BluetoothManager(Context context) {
        this.context = context;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            this.bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }
    }

    // basic bluetooth checks
    public boolean isBluetoothSupported() {
        return bluetoothAdapter != null;
    }

    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }
    
    // getting device by mac is not recommended for ble
    // keeping for compatibility but we'll use scanning instead
    public BluetoothDevice getDeviceByMac() throws SecurityException {
        try {
            return bluetoothAdapter.getRemoteDevice(Constants.ESP32_MAC_ADDRESS);
        } catch (IllegalArgumentException e) {
            Log.e(Constants.TAG_BLUETOOTH, "Invalid MAC: " + Constants.ESP32_MAC_ADDRESS);
            return null;
        }
    }

    // check if our device is already paired
    public boolean isDevicePaired() {
        // Check for BLUETOOTH_CONNECT permission
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

    // still used by main activity in some code paths
    public BluetoothDevice getPairedDevice() throws SecurityException {
        return getDeviceByMac();
    }

    // new methods for ble
    public void setViewModel(BluetoothViewModel viewModel) {
        this.viewModel = viewModel;
    }
    
    public void setGattCallback(BluetoothGattCallback callback) {
        this.gattCallback = callback;
    }
    
    public BluetoothGatt connectToBleDevice(BluetoothDevice device) {
        if (device == null) return null;
        
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) 
                != PackageManager.PERMISSION_GRANTED) {
            viewModel.handleError("BLUETOOTH_CONNECT permission not granted");
            return null;
        }
        
        // update connecting state
        if (viewModel != null) {
            viewModel.updateConnectionState(Constants.STATE_CONNECTING);
        }
        
        // connect to gatt server, using the callback from service
        return device.connectGatt(context, false, gattCallback);
    }
    
    public void scanForDevices(ScanCallback callback) {
        this.scanCallback = callback;
        
        // check permissions for scan
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) 
                != PackageManager.PERMISSION_GRANTED) {
            viewModel.handleError("BLUETOOTH_SCAN permission not granted");
            return;
        }
        
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) 
                != PackageManager.PERMISSION_GRANTED) {
            viewModel.handleError("Location permission not granted");
            return;
        }
        
        if (!scanning) {
            // scan timeout
            handler.postDelayed(this::stopScan, Constants.SCAN_PERIOD);
            
            scanning = true;
            
            // scan settings - balanced power mode
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                    .build();
                    
            // filter by service uuid if needed
            List<ScanFilter> filters = new ArrayList<>();
            // we're not filtering here so we find all ble devices
            
            bluetoothLeScanner.startScan(filters, settings, scanCallback);
            Log.d(Constants.TAG_BLUETOOTH, "started scanning");
        } else {
            scanning = false;
            bluetoothLeScanner.stopScan(scanCallback);
            Log.d(Constants.TAG_BLUETOOTH, "stopped scan - already scanning");
        }
    }
    
    public void stopScan() {
        if (scanning && bluetoothLeScanner != null && scanCallback != null) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) 
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            scanning = false;
            bluetoothLeScanner.stopScan(scanCallback);
            Log.d(Constants.TAG_BLUETOOTH, "stopped scanning");
        }
    }
    
    public void disconnect() {
        if (bluetoothGatt != null) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) 
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            bluetoothGatt.disconnect();
        }
    }
    
    public void setBluetoothGatt(BluetoothGatt gatt) {
        this.bluetoothGatt = gatt;
    }
}