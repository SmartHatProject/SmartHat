package com.team12.smarthat.bluetooth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Handler;
import android.os.Looper;

import com.team12.smarthat.bluetooth.core.BleConnectionManager;
import com.team12.smarthat.bluetooth.core.BluetoothServiceIntegration;
import com.team12.smarthat.bluetooth.devices.esp32.ESP32BluetoothSpec;
import com.team12.smarthat.models.SensorData;
import com.team12.smarthat.utils.Constants;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests for notification handling in BluetoothServiceIntegration
 * 
 * These tests verify that our implementation correctly handles notifications from the ESP32 hardware
 * according to the BLE Configuration specification, including:
 * - Processing both sound and dust sensor notifications
 * - Handling out-of-order notifications with timestamp validation
 * - Using fallback values for invalid JSON data
 * - Using initial values as specified in the hardware documentation
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public class NotificationHandlingTest {
    
    @Mock
    private BleConnectionManager mockConnectionManager;
    
    @Mock
    private BluetoothGatt mockGatt;
    
    private BluetoothServiceIntegration serviceIntegration;
    
    // Test UUIDs matching ESP32 specification
    private final UUID DUST_UUID = ESP32BluetoothSpec.DUST_CHARACTERISTIC_UUID;
    private final UUID SOUND_UUID = ESP32BluetoothSpec.SOUND_CHARACTERISTIC_UUID;
    private final UUID GAS_UUID = ESP32BluetoothSpec.GAS_CHARACTERISTIC_UUID;
    
    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockConnectionManager.getBluetoothGatt()).thenReturn(mockGatt);
        when(mockConnectionManager.getCurrentState()).thenReturn(BleConnectionManager.ConnectionState.CONNECTED);
        
        // Create the service integration with mocked connection manager
        serviceIntegration = new BluetoothServiceIntegration(mockConnectionManager);
        
        // Ensure the main thread is active for the handler callbacks
        ShadowLooper.shadowMainLooper().idle();
    }
    
    /**
     * Test that sound sensor notifications are properly processed and fallback values are used
     */
    @Test
    public void testSoundSensorNotificationProcessing() throws InterruptedException {
        // Create a characteristic for sound sensor data
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(
                SOUND_UUID, 
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ);
        
        // Create a valid JSON string as would be sent from ESP32
        String validJson = "{\"messageType\":\"SOUND_SENSOR_DATA\",\"data\":75.5,\"timeStamp\":1234567890}";
        characteristic.setValue(validJson.getBytes(StandardCharsets.UTF_8));
        
        // Create a CountDownLatch to wait for the notification
        CountDownLatch latch = new CountDownLatch(1);
        
        // Create a data listener to receive the processed data
        final AtomicReference<SensorData> capturedData = new AtomicReference<>();
        BluetoothServiceIntegration.SensorDataListener listener = (data, sensorType) -> {
            if (sensorType.equals(Constants.TYPE_NOISE)) {
                capturedData.set(data);
                latch.countDown();
            }
        };
        
        // Add the listener to the service integration
        serviceIntegration.addSensorDataListener(listener);
        
        // Trigger the notification
        serviceIntegration.onCharacteristicChanged(characteristic);
        
        // Process background thread messages
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        
        // Give time for processing to complete
        Thread.sleep(100);
        
        // Process all pending messages on main thread again
        ShadowLooper.shadowMainLooper().idle();
        
        // Verify the data was processed correctly
        SensorData data = capturedData.get();
        assertNotNull("Sensor data should not be null", data);
        assertEquals(75.5f, data.getValue(), 0.01f);
        assertEquals(1234567890L, data.getTimestamp());
    }
    
    /**
     * Test that dust sensor notifications are properly processed and fallback values are used
     */
    @Test
    public void testDustSensorNotificationProcessing() throws InterruptedException {
        // Create a characteristic for dust sensor data
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(
                DUST_UUID, 
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ);
        
        // Create a valid JSON string as would be sent from ESP32
        String validJson = "{\"messageType\":\"DUST_SENSOR_DATA\",\"data\":25.5,\"timeStamp\":1234567890}";
        characteristic.setValue(validJson.getBytes(StandardCharsets.UTF_8));
        
        // Create a CountDownLatch to wait for the notification
        CountDownLatch latch = new CountDownLatch(1);
        
        // Create a data listener to receive the processed data
        final AtomicReference<SensorData> capturedData = new AtomicReference<>();
        BluetoothServiceIntegration.SensorDataListener listener = (data, sensorType) -> {
            if (sensorType.equals(Constants.TYPE_DUST)) {
                capturedData.set(data);
                latch.countDown();
            }
        };
        
        // Add the listener to the service integration
        serviceIntegration.addSensorDataListener(listener);
        
        // Trigger the notification
        serviceIntegration.onCharacteristicChanged(characteristic);
        
        // Process background thread messages
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        
        // Give time for processing to complete
        Thread.sleep(100);
        
        // Process all pending messages on main thread again
        ShadowLooper.shadowMainLooper().idle();
        
        // Verify the data was processed correctly
        SensorData data = capturedData.get();
        assertNotNull("Sensor data should not be null", data);
        assertEquals(25.5f, data.getValue(), 0.01f);
        assertEquals(1234567890L, data.getTimestamp());
    }
    
    /**
     * Test that invalid JSON data uses the fallback values as specified in the ESP32 protocol
     */
    @Test
    public void testInvalidJsonFallbackBehavior() throws InterruptedException {
        // Create a characteristic for sound sensor data
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(
                SOUND_UUID, 
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ);
        
        // Create an invalid JSON string
        String invalidJson = "{This is not valid JSON";
        characteristic.setValue(invalidJson.getBytes(StandardCharsets.UTF_8));
        
        // Create a CountDownLatch to wait for the notification
        CountDownLatch latch = new CountDownLatch(1);
        
        // Create a data listener to receive the processed data
        final AtomicReference<SensorData> capturedData = new AtomicReference<>();
        BluetoothServiceIntegration.SensorDataListener listener = (data, sensorType) -> {
            if (sensorType.equals(Constants.TYPE_NOISE)) {
                capturedData.set(data);
                latch.countDown();
            }
        };
        
        // Add the listener to the service integration
        serviceIntegration.addSensorDataListener(listener);
        
        // Trigger the notification
        serviceIntegration.onCharacteristicChanged(characteristic);
        
        // Process background thread messages
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        
        // Give time for processing to complete
        Thread.sleep(100);
        
        // Process all pending messages on main thread again
        ShadowLooper.shadowMainLooper().idle();
        
        // Verify fallback value was used (initial sound value from ESP32BluetoothSpec)
        SensorData data = capturedData.get();
        assertNotNull("Sensor data should not be null", data);
        assertEquals(ESP32BluetoothSpec.NotificationParams.INITIAL_SOUND_VALUE, data.getValue(), 0.01f);
    }
    
    /**
     * Test that out-of-order timestamps are handled correctly
     */
    @Test
    public void testOutOfOrderTimestampHandling() throws InterruptedException {
        // Create a characteristic for sound sensor data
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(
                SOUND_UUID, 
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ);
        
        // Create CountDownLatch for each notification
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        
        // Create data listener
        final AtomicReference<SensorData> lastCapturedData = new AtomicReference<>();
        BluetoothServiceIntegration.SensorDataListener listener = (data, sensorType) -> {
            if (sensorType.equals(Constants.TYPE_NOISE)) {
                lastCapturedData.set(data);
                if (latch1.getCount() > 0) {
                    latch1.countDown();
                } else {
                    latch2.countDown();
                }
            }
        };
        serviceIntegration.addSensorDataListener(listener);
        
        // Send first notification with timestamp 2000
        String json1 = "{\"messageType\":\"SOUND_SENSOR_DATA\",\"data\":70.0,\"timeStamp\":2000}";
        characteristic.setValue(json1.getBytes(StandardCharsets.UTF_8));
        serviceIntegration.onCharacteristicChanged(characteristic);
        
        // Process background thread messages
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        
        // Give time for processing to complete
        Thread.sleep(100);
        
        // Process all pending messages on main thread again
        ShadowLooper.shadowMainLooper().idle();
        
        // Verify first notification processed
        assertNotNull("First notification data should not be null", lastCapturedData.get());
        assertEquals(70.0f, lastCapturedData.get().getValue(), 0.01f);
        
        // Send second notification with timestamp 1000 (out of order, earlier)
        String json2 = "{\"messageType\":\"SOUND_SENSOR_DATA\",\"data\":60.0,\"timeStamp\":1000}";
        characteristic.setValue(json2.getBytes(StandardCharsets.UTF_8));
        serviceIntegration.onCharacteristicChanged(characteristic);
        
        // Process background thread messages
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        
        // Give time for processing to complete
        Thread.sleep(100);
        
        // Process all pending messages on main thread again
        ShadowLooper.shadowMainLooper().idle();
        
        // Wait a bit more for processing
        Thread.sleep(100);
        ShadowLooper.shadowMainLooper().idle();
        
        // Verify the out-of-order notification was still processed
        // since it's within the acceptable deviation window
        assertNotNull("Second notification was not processed", lastCapturedData.get());
        assertEquals(60.0f, lastCapturedData.get().getValue(), 0.01f);
    }
} 