package com.team12.smarthat.viewmodels;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.MainThread;
import androidx.annotation.WorkerThread;

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
private final Handler mainHandler = new Handler(Looper.getMainLooper());

@MainThread
public void initialize(DatabaseHelper dbHelper) {
    this.dbHelper = dbHelper;
    connectionState.setValue(Constants.STATE_DISCONNECTED); // Use setValue on main thread
    
    // Initialize with default values to prevent null
    SensorData defaultDust = new SensorData("dust", 0.0f);
    SensorData defaultNoise = new SensorData("noise", 0.0f);
    dustSensorData.setValue(defaultDust);
    noiseSensorData.setValue(defaultNoise);
}

/**
 * Handles new sensor data - Thread-safe, can be called from any thread
 * @param data The sensor data to process
 */
@WorkerThread
public void handleNewData(final SensorData data) {
    if (data == null) {
        Log.e("ViewModel", "Received null data");
        return;
    }

    // Ensure UI updates happen on main thread
    if (Looper.myLooper() == Looper.getMainLooper()) {
        updateLiveDataOnMainThread(data);
    } else {
        mainHandler.post(() -> updateLiveDataOnMainThread(data));
    }

    // Database operations are already thread-safe (executed on background thread)
    if (dbHelper != null) {
        dbHelper.insertData(data);
    }
}

/**
 * Updates all LiveData objects on main thread
 * @param data The sensor data to update
 */
@MainThread
private void updateLiveDataOnMainThread(SensorData data) {
    // Update general sensor data
    sensorData.setValue(data);
    
    // Also update type-specific data
    String type = data.getSensorType();
    if (type != null) {
        if (type.equalsIgnoreCase("dust")) {
            Log.d(Constants.TAG_MAIN, "ViewModel - Updated dust data: " + data.getValue());
            dustSensorData.setValue(data);
        } else if (type.equalsIgnoreCase("noise")) {
            Log.d(Constants.TAG_MAIN, "ViewModel - Updated noise data: " + data.getValue());
            noiseSensorData.setValue(data);
        }
    }
}

/**
 * Updates connection state - Thread-safe, can be called from any thread
 * @param state The new connection state
 */
public void updateConnectionState(final String state) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        connectionState.setValue(state);
    } else {
        mainHandler.post(() -> connectionState.setValue(state));
    }
}

/**
 * Handles error messages - Thread-safe, can be called from any thread
 * @param error The error message to display
 */
public void handleError(final String error) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        errorMessage.setValue(error);
    } else {
        mainHandler.post(() -> errorMessage.setValue(error));
    }
}

public boolean isConnected() {
    String currentState = connectionState.getValue();
    return currentState != null && 
           (currentState.equals(Constants.STATE_CONNECTED) || currentState.equals("TEST MODE ACTIVE"));
}

public void disconnectDevice() {
    updateConnectionState(Constants.STATE_DISCONNECTED);
}

public void cleanupResources() {
    if (dbHelper != null) { dbHelper = null; }
}

public LiveData<String> getConnectionState() { return connectionState; }
public LiveData<SensorData> getSensorData() { return sensorData; }
public LiveData<SensorData> getDustSensorData() { return dustSensorData; }
public LiveData<SensorData> getNoiseSensorData() { return noiseSensorData; }
public LiveData<String> getErrorMessage() { return errorMessage; }

/**
 * Get the database helper instance
 * @return The database helper or null if not initialized
 */
public DatabaseHelper getDatabaseHelper() {
    return dbHelper;
}
}