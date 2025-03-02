package com.team12.smarthat.bluetooth;

import android.bluetooth.BluetoothSocket;
import android.util.Log;
import com.team12.smarthat.models.SensorData;
import com.team12.smarthat.utils.Constants;
import com.team12.smarthat.viewmodels.BluetoothViewModel;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.io.InputStream;

public class BluetoothService {
// holds the connection
    private BluetoothSocket socket;
 // updates ui state,sensor data,errors
 private final BluetoothViewModel viewModel;
    public BluetoothService(BluetoothViewModel viewModel) {
        this.viewModel = viewModel;
    }
    // sets socket & listens for new data on new thread
    public void connect(BluetoothSocket socket) {
this.socket = socket;
 // background thread
        new Thread(this::listenForData).start();// ble: will use bluetoothGatt callbacks here instead of socket
    }
    // listen for data
    private void listenForData() {
  try (InputStream input = socket.getInputStream()) {
 // buffer to read
            byte[] buffer = new byte[1024];
  // keep reading while connected
  while (socket.isConnected()) {
     int bytesRead = input.read(buffer);
 if (bytesRead > 0) {
// convert raw bytes into a trimmed string
  String rawData = new String(buffer, 0, bytesRead).trim();
  // process raw data
  processData(rawData);
    }
  }
        } catch (IOException e) {
 // update viewmodel with error if connection is lost
            viewModel.handleError("connection lost: " + e.getMessage());
    // for ble: will handle disconnect in callback method
        }
    }
    // process sensor raw json
    private void processData(String rawData) {
        try {
   // json object from rawdata string
 JSONObject json = new JSONObject(rawData);
 //sensor data object using json
            SensorData data = new SensorData(
   json.getString("sensor"),
        (float) json.getDouble("value")
            );
      // update viewmodel: new sensor val
       viewModel.handleNewData(data);
        } catch (JSONException e) {
     // case json parsing failed -> invalid format notif for viewmodel
         viewModel.handleError("invalid data format");
  // for ble well adjust parsing logic
        }
    }

    // closes the bluetooth socket cleanly
    public void disconnect() {
   try {
      if (socket != null) socket.close();
   // ble: will disconnect from the gatt server here
        } catch (IOException e) {
    // log disconnect error using our constants tag
     Log.e(Constants.TAG_BLUETOOTH, "disconnect error: " + e.getMessage());
        }
    }
}