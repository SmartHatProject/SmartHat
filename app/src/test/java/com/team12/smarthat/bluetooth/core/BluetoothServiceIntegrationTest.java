package com.team12.smarthat.bluetooth.core;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.os.Handler;
import android.os.Looper;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.team12.smarthat.bluetooth.devices.esp32.ESP32BluetoothSpec;
import com.team12.smarthat.models.SensorData;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BluetoothServiceIntegration}
 * 
 *tests focus on verifying sensor data parsing, notification handling,
 * and callback management
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {31}) // Android 12 (API 31)
public class BluetoothServiceIntegrationTest {

    // Rule for testing LiveData
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();
    
    // Mocks
    @Mock private BleConnectionManager mockConnectionManager;
    @Mock private BluetoothGatt mockBluetoothGatt;
    @Mock private BluetoothGattService mockGattService;
    @Mock private BluetoothGattCharacteristic mockDustCharacteristic;
    @Mock private BluetoothGattCharacteristic mockSoundCharacteristic;
    @Mock private BluetoothGattDescriptor mockDescriptor;
    @Mock private BluetoothServiceIntegration.SensorDataListener mockSensorDataListener;
    
    // Capture arguments
    @Captor private ArgumentCaptor<BleConnectionManager.CharacteristicChangeListener> characteristicChangeListenerCaptor;
    @Captor private ArgumentCaptor<Observer<BleConnectionManager.ConnectionState>> connectionStateObserverCaptor;
    
    // LiveData for connection state
    private MutableLiveData<BleConnectionManager.ConnectionState> connectionStateLiveData;
    
    // Class under test
    private BluetoothServiceIntegration bluetoothService;
    
    // Test doubles
    private TestLifecycleOwner lifecycleOwner;
    
    // Test constants
    private static final byte[] DUST_SENSOR_DATA = createDustSensorData(120.5f);
    private static final byte[] NOISE_SENSOR_DATA = createNoiseSensorData(85.2f);
    
    // Constants for sensor types
    private static final String SENSOR_TYPE_DUST = SensorData.TYPE_DUST;
    private static final String SENSOR_TYPE_NOISE = SensorData.TYPE_NOISE;
    
    @Before
    public void setUp() {
        // Initialize mocks
        MockitoAnnotations.openMocks(this);
        
        // Create a lifecycle owner for testing
        lifecycleOwner = new TestLifecycleOwner();
        
        // Set up connection state LiveData
        connectionStateLiveData = new MutableLiveData<>(BleConnectionManager.ConnectionState.DISCONNECTED);
        when(mockConnectionManager.getConnectionState()).thenReturn(connectionStateLiveData);
        
        // Set up characteristics
        UUID dustUuid = ESP32BluetoothSpec.DUST_CHARACTERISTIC_UUID;
        UUID soundUuid = ESP32BluetoothSpec.SOUND_CHARACTERISTIC_UUID;
        
        when(mockDustCharacteristic.getUuid()).thenReturn(dustUuid);
        when(mockSoundCharacteristic.getUuid()).thenReturn(soundUuid);
        
        // Set up service
        when(mockGattService.getUuid()).thenReturn(ESP32BluetoothSpec.SERVICE_UUID);
        when(mockGattService.getCharacteristic(dustUuid)).thenReturn(mockDustCharacteristic);
        when(mockGattService.getCharacteristic(soundUuid)).thenReturn(mockSoundCharacteristic);
        
        // Set up GATT
        when(mockBluetoothGatt.getService(ESP32BluetoothSpec.SERVICE_UUID)).thenReturn(mockGattService);
        
        // Set up connection manager
        when(mockConnectionManager.getBluetoothGatt()).thenReturn(mockBluetoothGatt);
        
        // Set up descriptor for notifications
        UUID clientConfigUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
        when(mockDustCharacteristic.getDescriptor(clientConfigUuid)).thenReturn(mockDescriptor);
        when(mockSoundCharacteristic.getDescriptor(clientConfigUuid)).thenReturn(mockDescriptor);
        
        // Create the class under test
        bluetoothService = new BluetoothServiceIntegration(mockConnectionManager);
        
        // Capture CharacteristicChangeListener
        verify(mockConnectionManager).setCharacteristicChangeListener(characteristicChangeListenerCaptor.capture());
    }
    
    @After
    public void tearDown() {
        // Clean up
        bluetoothService.cleanup();
        ShadowLooper.idleMainLooper();
    }
    
    /**
     * Test class implementation of LifecycleOwner for testing LiveData observers
     */
    private static class TestLifecycleOwner implements LifecycleOwner {
        private final LifecycleRegistry registry;
        
        TestLifecycleOwner() {
            registry = new LifecycleRegistry(this);
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE);
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START);
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME);
        }
        
        @Override
        public Lifecycle getLifecycle() {
            return registry;
        }
    }
    
    /**
     * Creates a properly formatted JSON string for dust sensor data
     */
    private static byte[] createDustSensorData(float value) {
        String jsonData = String.format("{\"type\":\"%s\",\"value\":%.1f,\"timestamp\":%d}",
                SENSOR_TYPE_DUST, value, System.currentTimeMillis());
        return jsonData.getBytes();
    }
    
    /**
     * Creates a properly formatted JSON string for noise sensor data
     */
    private static byte[] createNoiseSensorData(float value) {
        String jsonData = String.format("{\"type\":\"%s\",\"value\":%.1f,\"timestamp\":%d}",
                SENSOR_TYPE_NOISE, value, System.currentTimeMillis());
        return jsonData.getBytes();
    }
    
    @Test
    public void onCharacteristicChanged_dustData_parsesCorrectly() {
        // Given a dust sensor characteristic
        when(mockDustCharacteristic.getValue()).thenReturn(DUST_SENSOR_DATA);
        
        // Register a sensor data listener
        bluetoothService.addSensorDataListener(mockSensorDataListener);
        
        // When the characteristic changes
        characteristicChangeListenerCaptor.getValue().onCharacteristicChanged(mockDustCharacteristic);
        
        // Then the dust data should be parsed correctly and sent to the listener
        ArgumentCaptor<SensorData> sensorDataCaptor = ArgumentCaptor.forClass(SensorData.class);
        verify(mockSensorDataListener).onSensorData(sensorDataCaptor.capture(), eq(SensorData.TYPE_DUST));
        
        SensorData capturedData = sensorDataCaptor.getValue();
        assertEquals(SensorData.TYPE_DUST, capturedData.getSensorType());
        assertEquals(120.5f, capturedData.getValue(), 0.01f);
    }
    
    @Test
    public void onCharacteristicChanged_noiseData_parsesCorrectly() {
        // Given a noise sensor characteristic
        when(mockSoundCharacteristic.getValue()).thenReturn(NOISE_SENSOR_DATA);
        
        // Register a sensor data listener
        bluetoothService.addSensorDataListener(mockSensorDataListener);
        
        // When the characteristic changes
        characteristicChangeListenerCaptor.getValue().onCharacteristicChanged(mockSoundCharacteristic);
        
        // Then the noise data should be parsed correctly and sent to the listener
        ArgumentCaptor<SensorData> sensorDataCaptor = ArgumentCaptor.forClass(SensorData.class);
        verify(mockSensorDataListener).onSensorData(sensorDataCaptor.capture(), eq(SensorData.TYPE_NOISE));
        
        SensorData capturedData = sensorDataCaptor.getValue();
        assertEquals(SensorData.TYPE_NOISE, capturedData.getSensorType());
        assertEquals(85.2f, capturedData.getValue(), 0.01f);
    }
    
    @Test
    public void onCharacteristicChanged_invalidData_doesNotCrash() {
        // Given an invalid characteristic data
        when(mockDustCharacteristic.getValue()).thenReturn(new byte[] {0x03, 0x00, 0x00, 0x00, 0x00});
        
        // Register a sensor data listener
        bluetoothService.addSensorDataListener(mockSensorDataListener);
        
        // When the characteristic changes
        characteristicChangeListenerCaptor.getValue().onCharacteristicChanged(mockDustCharacteristic);
        
        // Then no listener methods should be called (no crash)
        verify(mockSensorDataListener, times(0)).onSensorData(any(), any());
    }
    
    @Test
    public void addSensorDataListener_registersListener() {
        // Given a listener
        BluetoothServiceIntegration.SensorDataListener listener = mock(BluetoothServiceIntegration.SensorDataListener.class);
        
        // When adding the listener
        bluetoothService.addSensorDataListener(listener);
        
        // Then it should be registered
        // We can't directly test the internal list, but we can test functionality
        when(mockDustCharacteristic.getValue()).thenReturn(DUST_SENSOR_DATA);
        characteristicChangeListenerCaptor.getValue().onCharacteristicChanged(mockDustCharacteristic);
        verify(listener).onSensorData(any(), eq(SensorData.TYPE_DUST));
    }
    
    @Test
    public void removeSensorDataListener_unregistersListener() {
        // Given a registered listener
        BluetoothServiceIntegration.SensorDataListener listener = mock(BluetoothServiceIntegration.SensorDataListener.class);
        bluetoothService.addSensorDataListener(listener);
        
        // When removing the listener
        bluetoothService.removeSensorDataListener(listener);
        
        // Then it should be unregistered
        // We can't directly test the internal list, but we can test functionality
        when(mockDustCharacteristic.getValue()).thenReturn(DUST_SENSOR_DATA);
        characteristicChangeListenerCaptor.getValue().onCharacteristicChanged(mockDustCharacteristic);
        verify(listener, times(0)).onSensorData(any(), any());
    }
    
    @Test
    public void multipleListeners_allReceiveUpdates() {
        // Given multiple registered listeners
        List<BluetoothServiceIntegration.SensorDataListener> listeners = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            BluetoothServiceIntegration.SensorDataListener listener = mock(BluetoothServiceIntegration.SensorDataListener.class);
            listeners.add(listener);
            bluetoothService.addSensorDataListener(listener);
        }
        
        // When a characteristic change occurs
        when(mockDustCharacteristic.getValue()).thenReturn(DUST_SENSOR_DATA);
        characteristicChangeListenerCaptor.getValue().onCharacteristicChanged(mockDustCharacteristic);
        
        // Then all listeners should receive the update
        for (BluetoothServiceIntegration.SensorDataListener listener : listeners) {
            verify(listener).onSensorData(any(), eq(SensorData.TYPE_DUST));
        }
    }
    
    @Test
    public void observeConnectionState_setsUpNotifications() {
        // When
        bluetoothService.observeConnectionState(lifecycleOwner);
        
        // Verify listener was setup
        verify(mockConnectionManager, times(1)).getConnectionState();
        
        // Simulate connection
        connectionStateLiveData.setValue(BleConnectionManager.ConnectionState.CONNECTED);
        
        // Execute pending main thread tasks
        ShadowLooper.idleMainLooper();
        
        // Then notifications should be setup for both characteristics
        verify(mockBluetoothGatt, times(1)).getService(ESP32BluetoothSpec.SERVICE_UUID);
    }
    
    /**
     * Test to ensure early returns in parser avoid unnecessary processing
     * This is a battery optimization for Pixel 4a devices on Android 12
     */
    @Test
    public void noListeners_savesBatteryBySkippingProcessing() {
        // Create mock objects directly in test (not using @Mock) for this special test
        BluetoothGattCharacteristic dustCharacteristic = mock(BluetoothGattCharacteristic.class);
        when(dustCharacteristic.getUuid()).thenReturn(ESP32BluetoothSpec.DUST_CHARACTERISTIC_UUID);
        when(dustCharacteristic.getValue()).thenReturn(DUST_SENSOR_DATA);
        
        // Create a spy to verify internal method calls
        BluetoothServiceIntegration spyService = org.mockito.Mockito.spy(bluetoothService);
        
        // Don't register any listeners
        
        // When characteristic changes
        characteristicChangeListenerCaptor.getValue().onCharacteristicChanged(dustCharacteristic);
        
        // Execute pending main thread tasks
        ShadowLooper.idleMainLooper();
        
        // Then the listener shouldn't be called (since there are none)
        verify(mockSensorDataListener, never()).onSensorData(any(SensorData.class), any(String.class));
        
        // Now add a listener and verify it works
        spyService.addSensorDataListener(mockSensorDataListener);
        
        // When characteristic changes again
        characteristicChangeListenerCaptor.getValue().onCharacteristicChanged(dustCharacteristic);
        
        // Execute pending main thread tasks
        ShadowLooper.idleMainLooper();
        
        // Then the listener should be called
        verify(mockSensorDataListener).onSensorData(any(SensorData.class), eq(SENSOR_TYPE_DUST));
    }
} 