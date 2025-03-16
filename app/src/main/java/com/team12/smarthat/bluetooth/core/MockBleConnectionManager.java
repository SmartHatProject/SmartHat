package com.team12.smarthat.bluetooth.core;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import com.team12.smarthat.permissions.BluetoothPermissionManager;
import com.team12.smarthat.utils.Constants;

/**
 * mock implementation of bleconnectionmanager that simulates connection states
 * without requiring HW
 
 */
@SuppressLint("MissingPermission")
public class MockBleConnectionManager extends BleConnectionManager {
    private static final String TAG = "MockBleManager";
    
    // simulation delay constants
    private static final long CONNECTING_DELAY_MS = 1500; // time to show "connecting" state
    private static final long DISCONNECT_DELAY_MS = 800;  // time to show "disconnecting" state
    
    private final Handler mockHandler = new Handler(Looper.getMainLooper());
    private static MockBleConnectionManager instance;
    
    /**
     * get the singleton instance of mockbleconnectionmanager
     */
    public static synchronized MockBleConnectionManager getInstance(Context context, BluetoothPermissionManager manager) {
        if (instance == null) {
            instance = new MockBleConnectionManager(context, manager);
        }
        return instance;
    }
    
    /**
     * private constructor for singleton pattern
     */
    private MockBleConnectionManager(Context context, BluetoothPermissionManager manager) {
        super(context, manager);
        Log.d(TAG, "MockBleConnectionManager initialized");
    }
    
    /**
     * simulate a connection to a device
     * this overrides the real connection method and simulates state changes
     */
    @Override
    public void connect(BluetoothDevice device) {
        Log.d(TAG, "Simulating connection to device: " + (device != null ? device.getAddress() : "null"));
        
        // start with connecting state
        mockHandler.post(() -> {
            // update to connecting state
            super.updateState(ConnectionState.CONNECTING);
            
            // simulate connection delay
            mockHandler.postDelayed(() -> {
                // after delay, update to connected state
                super.updateState(ConnectionState.CONNECTED);
                Log.d(TAG, "Mock device connected");
            }, CONNECTING_DELAY_MS);
        });
    }
    
    /**
     * simulate disconnection from a device
     */
    @Override
    public void disconnect() {
        Log.d(TAG, "Simulating disconnection");
        
        ConnectionState currentState = getCurrentState();
        
        // only process if we're in a state where disconnection makes sense
        if (currentState == ConnectionState.CONNECTED || 
            currentState == ConnectionState.CONNECTING) {
            
            // update to disconnecting state
            mockHandler.post(() -> {
                super.updateState(ConnectionState.DISCONNECTING);
                
                // simulate disconnection delay
                mockHandler.postDelayed(() -> {
                    // after delay, update to disconnected state
                    super.updateState(ConnectionState.DISCONNECTED);
                    Log.d(TAG, "Mock device disconnected");
                }, DISCONNECT_DELAY_MS);
            });
        } else {
            // force disconnected state immediately if already disconnecting/disconnected
            super.updateState(ConnectionState.DISCONNECTED);
        }
    }
    
    /**
     * simulate a characteristic change
     */
    public void simulateCharacteristicChange(BluetoothGattCharacteristic characteristic) {
        if (characteristic != null) {
            CharacteristicChangeListener listener = getCharacteristicChangeListener();
            if (listener != null) {
                mockHandler.post(() -> listener.onCharacteristicChanged(characteristic));
            }
        }
    }
    
    /**
     * get the characteristic change listener
     */
    public CharacteristicChangeListener getCharacteristicChangeListener() {
        return super.getCharacteristicChangeListener();
    }
    
    /**
     * reset the singleton instance (for testing)
     */
    public static void resetInstance() {
        if (instance != null) {
            instance.cleanup();
        }
        instance = null;
    }
    
    /**
     * cleanup resources
     */
    @Override
    public void cleanup() {
        mockHandler.removeCallbacksAndMessages(null);
        super.cleanup();
    }
} 