package com.team12.smarthat.viewmodels;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.MainThread;
import androidx.annotation.WorkerThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.team12.smarthat.database.DatabaseHelper;
import com.team12.smarthat.models.SensorData;
import com.team12.smarthat.utils.Constants;

/**
 * Thread-safe ViewModel for Bluetooth operations
 * All LiveData updates are guaranteed to happen on the main thread
 */
public class BluetoothViewModel extends ViewModel {
    private final MutableLiveData<Integer> connectionState = new MutableLiveData<>(Constants.STATE_DISCONNECTED);
    private final MutableLiveData<SensorData> sensorData = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<SensorData> dustSensorData = new MutableLiveData<>();
    private final MutableLiveData<SensorData> noiseSensorData = new MutableLiveData<>();
    private DatabaseHelper dbHelper;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // LiveData for test mode status
    private final MutableLiveData<Boolean> testModeStatus = new MutableLiveData<>(false);

    /**
     * Initialize the ViewModel with database access
     * Must be called on the main thread
     */
    @MainThread
    public void initialize(DatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
        // Safe to call directly since we're on the main thread
        connectionState.setValue(Constants.STATE_DISCONNECTED);
        
        // Initialize with default values to prevent null
        SensorData defaultDust = new SensorData("dust", 0.0f);
        SensorData defaultNoise = new SensorData("noise", 0.0f);
        dustSensorData.setValue(defaultDust);
        noiseSensorData.setValue(defaultNoise);
    }

    /**
     * Handle new sensor data from Bluetooth
     * @param data The SensorData object
     */
    public void handleNewData(@NonNull final SensorData data) {
        if (data == null) return;
        
        // Update the generic sensor data
        sensorData.postValue(data);
        
        // Also update the specific sensor type data
        String type = data.getSensorType();
        if (type != null) {
            type = type.toLowerCase();
            if (type.contains("dust")) {
                dustSensorData.postValue(data);
            } else if (type.contains("noise") || type.contains("sound")) {
                noiseSensorData.postValue(data);
            }
        }
        
        // Store in database if initialized
        if (dbHelper != null) {
            try {
                dbHelper.insertData(data);
            } catch (Exception e) {
                Log.e(Constants.TAG_BLUETOOTH, "Error storing sensor data: " + e.getMessage());
            }
        }
    }

    /**
     * Update the connection state
     * @param state The new connection state (use Constants.STATE_* constants)
     */
    public void updateConnectionState(int state) {
        connectionState.postValue(state);
    }

    /**
     * Get the current connection state live data
     * @return LiveData containing the current connection state
     */
    @NonNull
    public LiveData<Integer> getConnectionState() {
        return connectionState;
    }

    /**
     * Thread-safe method to handle error messages
     * Can be called from any thread
     */
    public void handleError(@NonNull final String error) {
        mainHandler.post(() -> {
            try {
                errorMessage.setValue(error);
            } catch (Exception e) {
                Log.e(Constants.TAG_MAIN, "Error handling error message: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Check if the device is currently connected
     */
    public boolean isConnected() {
        Integer currentState = connectionState.getValue();
        return currentState != null && 
               (currentState.equals(Constants.STATE_CONNECTED) || 
                currentState.equals("TEST MODE ACTIVE"));
    }

    /**
     * Set the device as disconnected
     */
    public void disconnectDevice() {
        updateConnectionState(Constants.STATE_DISCONNECTED);
    }

    /**
     * Clean up resources when ViewModel is no longer used
     */
    public void cleanupResources() {
        dbHelper = null;
    }

    // LiveData getters
    @NonNull
    public LiveData<SensorData> getSensorData() { return sensorData; }
    @NonNull
    public LiveData<SensorData> getDustSensorData() { return dustSensorData; }
    @NonNull
    public LiveData<SensorData> getNoiseSensorData() { return noiseSensorData; }
    @NonNull
    public LiveData<String> getErrorMessage() { return errorMessage; }
    
    /**
     * Get the database helper instance
     */
    public DatabaseHelper getDatabaseHelper() {
        return dbHelper;
    }

    /**
     * Update the test mode status
     * @param enabled Whether test mode is enabled
     */
    public void updateTestModeStatus(boolean enabled) {
        testModeStatus.postValue(enabled);
    }
    
    /**
     * Get the test mode status live data
     * @return LiveData containing whether test mode is enabled
     */
    @NonNull
    public LiveData<Boolean> getTestModeStatus() {
        return testModeStatus;
    }
}