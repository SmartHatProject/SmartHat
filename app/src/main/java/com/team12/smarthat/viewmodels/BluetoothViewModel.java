package com.team12.smarthat.viewmodels;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.team12.smarthat.database.DatabaseHelper;
import com.team12.smarthat.models.SensorData;

public class BluetoothViewModel extends ViewModel {
private final MutableLiveData<String> connectionState = new MutableLiveData<>();
 private final MutableLiveData<SensorData> sensorData = new MutableLiveData<>();
private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
 private DatabaseHelper dbHelper;

  public void initialize(DatabaseHelper dbHelper) {

    this.dbHelper = dbHelper;
 connectionState.postValue("DISCONNECTED"); //initial state for connection

    }

 public void handleNewData(SensorData data) {
  sensorData.postValue(data);

    dbHelper.insertData(data);
    }

 public void updateConnectionState(String state) {
        connectionState.postValue(state);
    }

  public void handleError(String error) {
        errorMessage.postValue(error);
    }


public boolean isConnected() {
String currentState = connectionState.getValue();

   return currentState != null && currentState.equals("CONNECTED");
}

//will modify this but for now just simple state update if disc
public void disconnectDevice() {
    updateConnectionState("DISCONNECTED");
}

//dbHelper ->close() method

public void cleanupResources() {
     if (dbHelper != null) { dbHelper = null;}
  }

 public LiveData<String> getConnectionState() { return connectionState; }
   public LiveData<SensorData> getSensorData() { return sensorData; }

 public LiveData<String> getErrorMessage() { return errorMessage; }
}