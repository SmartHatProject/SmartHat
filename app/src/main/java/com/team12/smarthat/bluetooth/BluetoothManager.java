package com.team12.smarthat.bluetooth;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import androidx.core.content.ContextCompat;
import com.team12.smarthat.utils.Constants;
import java.io.IOException;
// ble note: would replace the enire class with ble gatt
public class BluetoothManager {

 private final BluetoothAdapter bluetoothAdapter;

    private final Context context;
//ble -> vluetoothLeScanner and BluetoothGatt
    public BluetoothManager(Context context) {
     this.context = context;
  this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();}
//ble -> 18+ api
public boolean isBluetoothSupported() {
        return bluetoothAdapter != null;
    }

 public boolean isBluetoothEnabled() {
        return bluetoothAdapter.isEnabled();
    }

 // mac address
  //ble -> scanner or scanner call back
 public BluetoothDevice getEsp32Device() throws SecurityException {
    try {
    return bluetoothAdapter.getRemoteDevice(Constants.ESP32_MAC_ADDRESS);
 } catch (IllegalArgumentException e) {
   Log.e(Constants.TAG_BLUETOOTH, "Invalid MAC: " + Constants.ESP32_MAC_ADDRESS);
   return null;}
    }

  public BluetoothSocket createSppSocket(BluetoothDevice device) throws IOException {
 if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
  throw new SecurityException("BLUETOOTH_CONNECT permission not granted");}
  return device.createRfcommSocketToServiceRecord(Constants.SPP_UUID);
    }

    public boolean isDevicePaired() {
// Check for BLUETOOTH_CONNECT permission
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
  throw new SecurityException("BLUETOOTH_CONNECT permission not granted");
}
 for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
 if (device.getAddress().equals(Constants.ESP32_MAC_ADDRESS)) {
  return true;
 }}
        return false;
    }

    // updated mainactivity
  public BluetoothDevice getPairedDevice() throws SecurityException {
 return getEsp32Device();
    }
}