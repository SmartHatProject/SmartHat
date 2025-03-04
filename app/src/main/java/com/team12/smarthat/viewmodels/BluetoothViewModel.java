package com.team12.smarthat.viewmodels;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import android.util.Log;

import com.team12.smarthat.database.DatabaseHelper;
import com.team12.smarthat.models.SensorData;
import com.team12.smarthat.utils.Constants;

public class BluetoothViewModel extends ViewModel {
private final MutableLiveData<String> connectionState = new MutableLiveData<>();
private final MutableLiveData<SensorData> sensorData = new MutableLiveData<>();
private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
private final MutableLiveData<SensorData> dustSensorData = new MutableLiveData<>();
private final MutableLiveData<SensorData> noiseSensorData = new MutableLiveData<>();
private DatabaseHelper dbHelper;

public void initialize(DatabaseHelper dbHelper) {
    this.dbHelper = dbHelper;
    connectionState.postValue("DISCONNECTED"); // initial state for connection
    
    // Initialize with default values to prevent null
    SensorData defaultDust = new SensorData("dust", 0.0f);
    SensorData defaultNoise = new SensorData("noise", 0.0f);
    dustSensorData.postValue(defaultDust);
    noiseSensorData.postValue(defaultNoise);
}

public void handleNewData(SensorData data) {
    if (data == null) {
        Log.e("ViewModel", "Received null data");
        return;
    }

    // Update general sensor data
    sensorData.postValue(data);
    
    // Also update type-specific data
    String type = data.getSensorType();
    if (type != null) {
        if (type.equalsIgnoreCase("dust")) {
            Log.d(Constants.TAG_MAIN, "ViewModel - Updated dust data: " + data.getValue());
            dustSensorData.postValue(data);
        } else if (type.equalsIgnoreCase("noise")) {
            Log.d(Constants.TAG_MAIN, "ViewModel - Updated noise data: " + data.getValue());
            noiseSensorData.postValue(data);
        }
    }

    // Save to database
    if (dbHelper != null) {
        dbHelper.insertData(data);
    }
}

public void updateConnectionState(String state) {
    connectionState.postValue(state);
}

public void handleError(String error) {
    errorMessage.postValue(error);
}

public boolean isConnected() {
    String currentState = connectionState.getValue();
    return currentState != null && 
           (currentState.equals("CONNECTED") || currentState.equals("TEST MODE ACTIVE"));
}

public void disconnectDevice() {
    updateConnectionState("DISCONNECTED");
}

public void cleanupResources() {
    if (dbHelper != null) { dbHelper = null; }
}

public LiveData<String> getConnectionState() { return connectionState; }
public LiveData<SensorData> getSensorData() { return sensorData; }
public LiveData<SensorData> getDustSensorData() { return dustSensorData; }
public LiveData<SensorData> getNoiseSensorData() { return noiseSensorData; }
public LiveData<String> getErrorMessage() { return errorMessage; }
}