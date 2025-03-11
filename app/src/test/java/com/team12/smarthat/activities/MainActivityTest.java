package com.team12.smarthat.activities;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.MutableLiveData;

import com.team12.smarthat.R;
import com.team12.smarthat.bluetooth.core.BleConnectionManager;
import com.team12.smarthat.bluetooth.core.BluetoothServiceIntegration;
import com.team12.smarthat.database.DatabaseHelper;
import com.team12.smarthat.models.SensorData;
import com.team12.smarthat.permissions.BluetoothPermissionManager;
import com.team12.smarthat.utils.Constants;
import com.team12.smarthat.utils.NotificationUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowToast;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MainActivity} using Robolectric for simulating the Android framework.
 * Focuses on testing UI interactions with Bluetooth components.
 * Optimized for Android 12 (API 31) on Pixel 4a.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {31}) // Android 12 (API 31)
public class MainActivityTest {
    
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();
    
    @Mock private BleConnectionManager mockBleConnectionManager;
    @Mock private BluetoothServiceIntegration mockBtIntegration;
    @Mock private BluetoothPermissionManager mockPermissionManager;
    @Mock private DatabaseHelper mockDatabaseHelper;
    @Mock private NotificationUtils mockNotificationUtils;
    @Mock private BluetoothManager mockBluetoothManager;
    @Mock private BluetoothAdapter mockBluetoothAdapter;
    
    // LiveData for testing callbacks
    private MutableLiveData<BleConnectionManager.ConnectionState> connectionStateLiveData;
    
    // Class under test
    private MainActivity activity;
    
    // UI components
    private TextView tvStatus, tvDust, tvNoise;
    private Button btnConnect;
    private View connectionIndicator;
    
    @Before
    public void setUp() throws Exception {
        // Initialize Mockito annotations
        MockitoAnnotations.openMocks(this);
        
        // Create LiveData for connection state testing
        connectionStateLiveData = new MutableLiveData<>();
        connectionStateLiveData.setValue(BleConnectionManager.ConnectionState.DISCONNECTED);
        
        // Configure mock behavior
        when(mockBleConnectionManager.getConnectionState()).thenReturn(connectionStateLiveData);
        when(mockBluetoothManager.getAdapter()).thenReturn(mockBluetoothAdapter);
        when(mockPermissionManager.hasRequiredPermissions()).thenReturn(true);
        
        // Create activity using Robolectric
        activity = Robolectric.buildActivity(MainActivity.class)
                .create()
                .start()
                .resume()
                .get();
        
        // Use reflection to set private fields
        setPrivateField(activity, "permissionManager", mockPermissionManager);
        setPrivateField(activity, "connectionManager", mockBleConnectionManager);
        setPrivateField(activity, "btIntegration", mockBtIntegration);
        setPrivateField(activity, "notificationUtils", mockNotificationUtils);
        setPrivateField(activity, "systemBluetoothManager", mockBluetoothManager);
        setPrivateField(activity, "bluetoothAdapter", mockBluetoothAdapter);
        
        // Find UI elements
        tvStatus = activity.findViewById(R.id.tv_status);
        tvDust = activity.findViewById(R.id.tv_dust);
        tvNoise = activity.findViewById(R.id.tv_noise);
        btnConnect = activity.findViewById(R.id.btn_connect);
        connectionIndicator = activity.findViewById(R.id.connection_indicator);
    }
    
    /**
     * Helper method to set private fields using reflection
     */
    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
    
    /**
     * Test that sensor data updates the UI correctly
     */
    @Test
    public void onSensorData_updatesUI() {
        // Create test data
        SensorData dustData = new SensorData(SensorData.TYPE_DUST, 150.5f);
        SensorData noiseData = new SensorData(SensorData.TYPE_NOISE, 75.8f);
        
        // Trigger sensor data updates
        activity.onSensorData(dustData, SensorData.TYPE_DUST);
        activity.onSensorData(noiseData, SensorData.TYPE_NOISE);
        
        // Process pending UI updates
        ShadowLooper.idleMainLooper();
        
        // Verify UI was updated
        assertEquals("Dust: 150.5 µg/m³", tvDust.getText().toString());
        assertEquals("Noise: 75.8 dB", tvNoise.getText().toString());
    }
    
    /**
     * Test that high dust levels trigger an alert
     */
    @Test
    public void highDustLevels_triggerAlert() {
        // Create test data with high dust level (above threshold)
        float highDustValue = Constants.DUST_THRESHOLD + 50.0f;
        SensorData highDustData = new SensorData(SensorData.TYPE_DUST, highDustValue);
        
        // Trigger sensor data update
        activity.onSensorData(highDustData, SensorData.TYPE_DUST);
        
        // Process pending UI updates
        ShadowLooper.idleMainLooper();
        
        // Verify notification was triggered
        verify(mockNotificationUtils).showDustAlert(any(SensorData.class));
    }
    
    /**
     * Test that high noise levels trigger an alert
     */
    @Test
    public void highNoiseLevels_triggerAlert() {
        // Create test data with high noise level (above threshold)
        float highNoiseValue = Constants.NOISE_THRESHOLD + 20.0f;
        SensorData highNoiseData = new SensorData(SensorData.TYPE_NOISE, highNoiseValue);
        
        // Trigger sensor data update
        activity.onSensorData(highNoiseData, SensorData.TYPE_NOISE);
        
        // Process pending UI updates
        ShadowLooper.idleMainLooper();
        
        // Verify notification was triggered
        verify(mockNotificationUtils).showNoiseAlert(any(SensorData.class));
    }
    
    /**
     * Test that normal levels don't trigger alerts
     */
    @Test
    public void normalLevels_dontTriggerAlert() {
        // Create test data with normal levels (below threshold)
        float normalDustValue = Constants.DUST_THRESHOLD - 50.0f;
        float normalNoiseValue = Constants.NOISE_THRESHOLD - 20.0f;
        
        SensorData normalDustData = new SensorData(SensorData.TYPE_DUST, normalDustValue);
        SensorData normalNoiseData = new SensorData(SensorData.TYPE_NOISE, normalNoiseValue);
        
        // Trigger sensor data updates
        activity.onSensorData(normalDustData, SensorData.TYPE_DUST);
        activity.onSensorData(normalNoiseData, SensorData.TYPE_NOISE);
        
        // Process pending UI updates
        ShadowLooper.idleMainLooper();
        
        // Verify notifications were not triggered
        verify(mockNotificationUtils, never()).showDustAlert(any(SensorData.class));
        verify(mockNotificationUtils, never()).showNoiseAlert(any(SensorData.class));
    }
    
    /**
     * Test that connection state updates are reflected in the UI
     * This is particularly important for Android 12 on Pixel 4a devices
     */
    @Test
    public void connectionStateChanges_updateUI() {
        // Setup ShadowActivity to get access to UI updates
        ShadowActivity shadowActivity = org.robolectric.shadow.api.Shadow.extract(activity);
        
        // Simulate connected state
        connectionStateLiveData.setValue(BleConnectionManager.ConnectionState.CONNECTED);
        
        // Process pending UI updates
        ShadowLooper.idleMainLooper();
        
        // Verify UI was updated for connected state
        assertEquals("Connected", tvStatus.getText().toString());
        
        // Simulate disconnected state
        connectionStateLiveData.setValue(BleConnectionManager.ConnectionState.DISCONNECTED);
        
        // Process pending UI updates
        ShadowLooper.idleMainLooper();
        
        // Verify UI was updated for disconnected state
        assertEquals("Disconnected", tvStatus.getText().toString());
    }
} 