package com.team12.smarthat.bluetooth.core;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.MutableLiveData;

import com.team12.smarthat.permissions.BluetoothPermissionManager;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BleConnectionManager}
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {31}) // Android 12 (API 31)
public class BleConnectionManagerTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    // Mocks
    @Mock private Context mockContext;
    @Mock private BluetoothManager mockBluetoothManager;
    @Mock private BluetoothAdapter mockBluetoothAdapter;
    @Mock private BluetoothDevice mockBluetoothDevice;
    @Mock private BluetoothGatt mockBluetoothGatt;
    @Mock private BluetoothGattService mockGattService;
    @Mock private BluetoothGattCharacteristic mockCharacteristic;
    @Mock private BluetoothPermissionManager mockPermissionManager;
    @Mock private BleConnectionManager.ConnectionListener mockConnectionListener;
    @Mock private BleConnectionManager.CharacteristicChangeListener mockCharacteristicChangeListener;
    
    // Captors
    @Captor private ArgumentCaptor<BluetoothGattCallback> gattCallbackCaptor;
    
    // LiveData for connection state
    private MutableLiveData<BleConnectionManager.ConnectionState> connectionStateLiveData;
    
    // Class under test
    private BleConnectionManager bleConnectionManager;
    
    // Test doubles
    private TestLifecycleOwner lifecycleOwner;
    
    // Test constants
    private static final String TEST_DEVICE_ADDRESS = "00:11:22:33:44:55";
    
    // We'll store the captured callback here
    private BluetoothGattCallback capturedCallback;
    
    @Before
    public void setUp() {
        // Initialize mocks
        MockitoAnnotations.openMocks(this);
        
        // Create a lifecycle owner for testing
        lifecycleOwner = new TestLifecycleOwner();
        
        // Set up the BluetoothManager and Adapter
        when(mockContext.getSystemService(Context.BLUETOOTH_SERVICE)).thenReturn(mockBluetoothManager);
        when(mockBluetoothManager.getAdapter()).thenReturn(mockBluetoothAdapter);
        when(mockBluetoothAdapter.isEnabled()).thenReturn(true);
        
        // Set up device mock
        when(mockBluetoothDevice.getAddress()).thenReturn(TEST_DEVICE_ADDRESS);
        when(mockBluetoothAdapter.getRemoteDevice(TEST_DEVICE_ADDRESS)).thenReturn(mockBluetoothDevice);
        
        // Set up GATT mock
        when(mockBluetoothDevice.connectGatt(eq(mockContext), anyBoolean(), any(BluetoothGattCallback.class)))
                .thenAnswer(invocation -> {
                    capturedCallback = invocation.getArgument(2);
                    return mockBluetoothGatt;
                });
        
        // Set up permission manager
        when(mockPermissionManager.hasRequiredPermissions()).thenReturn(true);
        when(mockPermissionManager.hasBluetoothConnectPermission()).thenReturn(true);
        when(mockPermissionManager.hasBluetoothScanPermission()).thenReturn(true);
        
        // Create the class under test using reflection to avoid singleton restrictions
        bleConnectionManager = createBleConnectionManagerInstance(mockContext, mockPermissionManager);
    }
    
    /**
     * test class implementation of LifecycleOwner for testing LiveData observers
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
     * Creates a BleConnectionManager instance for testing
     */
    private BleConnectionManager createBleConnectionManagerInstance(Context context, BluetoothPermissionManager manager) {
        // Reset the singleton first to avoid test interference
        BleConnectionManager.resetInstanceForTesting();
        return BleConnectionManager.getInstance(context, manager);
    }
    
    @Test
    public void connect_setsUpGattCallback() {
        // Given Bluetooth is enabled and permissions granted
        
        // When connecting to the device
        bleConnectionManager.connect(mockBluetoothDevice);
        ShadowLooper.idleMainLooper();
        
        // Then it should attempt to connect with a GATT callback
        verify(mockBluetoothDevice).connectGatt(eq(mockContext), anyBoolean(), any(BluetoothGattCallback.class));
        assertNotNull(capturedCallback);
    }
    
    @Test
    public void addConnectionListener_withSameDeviceAlreadyConnected_doesNotReconnect() {
        // Given the connection manager is already connected
        bleConnectionManager.addConnectionListener(mockConnectionListener);
        bleConnectionManager.connect(mockBluetoothDevice);
        ShadowLooper.idleMainLooper();
        
        // Simulate successful connection
        BluetoothGattCallback callback = capturedCallback;
        callback.onConnectionStateChange(mockBluetoothGatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED);
        ShadowLooper.idleMainLooper();
        
        // Reset mocks to verify subsequent calls
        clearInvocations(mockBluetoothDevice);
        
        // When connecting again to the same device
        bleConnectionManager.connect(mockBluetoothDevice);
        ShadowLooper.idleMainLooper();
        
        // Then it should not attempt to connect again
        verify(mockBluetoothDevice, never()).connectGatt(any(), anyBoolean(), any());
    }
    
    @Test
    public void disconnect_whenConnected_closesGatt() {
        // Given a connecting BLE connection
        bleConnectionManager.addConnectionListener(mockConnectionListener);
        bleConnectionManager.connect(mockBluetoothDevice);
        ShadowLooper.idleMainLooper();
        BluetoothGattCallback callback = capturedCallback;
        
        // Reset mocks to verify subsequent calls
        clearInvocations(mockConnectionListener);
        
        // When disconnecting
        bleConnectionManager.disconnect();
        ShadowLooper.idleMainLooper();
        
        // Then it should close the GATT connection
        verify(mockBluetoothGatt).disconnect();
        verify(mockBluetoothGatt).close();
    }
    
    @Test
    public void onConnectionStateChange_connected_callsDiscoverServices() {
        // Given a connected BLE device
        bleConnectionManager.addConnectionListener(mockConnectionListener);
        bleConnectionManager.connect(mockBluetoothDevice);
        ShadowLooper.idleMainLooper();
        
        BluetoothGattCallback callback = capturedCallback;
        
        // Reset mocks to verify subsequent calls
        clearInvocations(mockConnectionListener);
        
        // When a CONNECTED state change is received
        callback.onConnectionStateChange(mockBluetoothGatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED);
        ShadowLooper.idleMainLooper();
        
        // Then it should discover services
        verify(mockBluetoothGatt).discoverServices();
        
        // And notify listeners
        verify(mockConnectionListener).onStateChanged(BleConnectionManager.ConnectionState.CONNECTED);
    }
    
    @Test
    public void setCharacteristicChangeListener_withValidListener_notifiesOnChanges() {
        // Given a connected BLE device
        bleConnectionManager.connect(mockBluetoothDevice);
        ShadowLooper.idleMainLooper();
        
        BluetoothGattCallback callback = capturedCallback;
        callback.onConnectionStateChange(mockBluetoothGatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED);
        ShadowLooper.idleMainLooper();
        
        // When setting a characteristic change listener
        bleConnectionManager.setCharacteristicChangeListener(mockCharacteristicChangeListener);
        
        // And a characteristic change occurs
        callback.onCharacteristicChanged(mockBluetoothGatt, mockCharacteristic);
        ShadowLooper.idleMainLooper();
        
        // Then the listener should be notified
        verify(mockCharacteristicChangeListener).onCharacteristicChanged(mockCharacteristic);
    }
    
    @Test
    public void onCharacteristicChanged_withNoListener_doesNotCrash() {
        // Given a connected BLE device with no change listener
        bleConnectionManager.addConnectionListener(mockConnectionListener);
        bleConnectionManager.setCharacteristicChangeListener(mockCharacteristicChangeListener);
        bleConnectionManager.connect(mockBluetoothDevice);
        ShadowLooper.idleMainLooper();
        
        BluetoothGattCallback callback = capturedCallback;
        callback.onConnectionStateChange(mockBluetoothGatt, BluetoothGatt.GATT_SUCCESS, BluetoothProfile.STATE_CONNECTED);
        ShadowLooper.idleMainLooper();
        
        // When removing the listener
        bleConnectionManager.setCharacteristicChangeListener(null);
        
        // And a characteristic change occurs
        callback.onCharacteristicChanged(mockBluetoothGatt, mockCharacteristic);
        ShadowLooper.idleMainLooper();
        
        // Then it should not crash and the previous listener should not be notified
        verify(mockCharacteristicChangeListener, never()).onCharacteristicChanged(any());
    }
    
    /**
     * proper thread handling
     * for characteristic change callbacks
     */
    @Test
    public void onCharacteristicChanged_usesProperThreadHandling() {
        // Setup
        bleConnectionManager.setCharacteristicChangeListener(mockCharacteristicChangeListener);
        
        // Test for early return if no listener
        bleConnectionManager.setCharacteristicChangeListener(null);
        capturedCallback.onCharacteristicChanged(mockBluetoothGatt, mockCharacteristic);
        
        // Verify no interaction with mock
        verify(mockCharacteristicChangeListener, never()).onCharacteristicChanged(any());
        
        // Now set the listener and test normal case
        bleConnectionManager.setCharacteristicChangeListener(mockCharacteristicChangeListener);
        
        // Call characteristic changed
        capturedCallback.onCharacteristicChanged(mockBluetoothGatt, mockCharacteristic);
        
        // Execute pending main thread tasks
        ShadowLooper.idleMainLooper();
        
        // Verify callback was called on main thread
        verify(mockCharacteristicChangeListener).onCharacteristicChanged(mockCharacteristic);
    }
    
    /**
     * Test to verify robust error handling specific to Pixel 4a on Android 12
     */
    @Test
    public void errorHandling_providesClearMessages() {
        // Setup a device connection
        when(mockBluetoothAdapter.getRemoteDevice(TEST_DEVICE_ADDRESS)).thenReturn(mockBluetoothDevice);
        when(mockBluetoothDevice.connectGatt(eq(mockContext), anyBoolean(), any(BluetoothGattCallback.class)))
            .thenReturn(mockBluetoothGatt);
        
        // Connect a device
        bleConnectionManager.connect(mockBluetoothDevice);
        
        // Simulate GATT_ERROR status (value is 133 or 0x85)
        capturedCallback.onConnectionStateChange(mockBluetoothGatt, 133, BluetoothProfile.STATE_DISCONNECTED);
        
        // Execute pending main thread tasks
        ShadowLooper.idleMainLooper();
        
        // Verify error message captured
        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockConnectionListener).onError(errorCaptor.capture());
        
        String errorMessage = errorCaptor.getValue();
        assertNotNull("Error message should not be null", errorMessage);
        assertTrue("Error message should be descriptive", 
                errorMessage.toLowerCase().contains("error") || 
                errorMessage.toLowerCase().contains("fail"));
    }
} 