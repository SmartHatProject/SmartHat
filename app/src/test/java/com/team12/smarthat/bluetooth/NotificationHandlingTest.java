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

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class NotificationHandlingTest {
    
    @Mock
    private BleConnectionManager mockConnectionManager;
    
    @Mock
    private BluetoothGatt mockGatt;
    
    private BluetoothServiceIntegration serviceIntegration;
    
    
    private final UUID DUST_UUID = ESP32BluetoothSpec.DUST_CHARACTERISTIC_UUID;
    private final UUID SOUND_UUID = ESP32BluetoothSpec.SOUND_CHARACTERISTIC_UUID;
    
    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockConnectionManager.getBluetoothGatt()).thenReturn(mockGatt);
        when(mockConnectionManager.getCurrentState()).thenReturn(BleConnectionManager.ConnectionState.CONNECTED);
        
        
        serviceIntegration = new BluetoothServiceIntegration(mockConnectionManager);
    }
    
    @Test
    public void testSoundSensorNotificationProcessing() throws InterruptedException {
        
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(
                SOUND_UUID, 
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ);
        
        
        String validJson = "{\"messageType\":\"SOUND_SENSOR_DATA\",\"data\":75.5,\"timeStamp\":1234567890}";
        characteristic.setValue(validJson.getBytes(StandardCharsets.UTF_8));
        
        
        CountDownLatch latch = new CountDownLatch(1);
        
        
        final AtomicReference<SensorData> capturedData = new AtomicReference<>();
        BluetoothServiceIntegration.SensorDataListener listener = (data, type) -> {
            if (SensorData.TYPE_NOISE.equals(type)) {
                capturedData.set(data);
                latch.countDown();
            }
        };
        
        
        serviceIntegration.addSensorDataListener(listener);
        
        
        serviceIntegration.onCharacteristicChanged(characteristic);
        
        
        ShadowLooper.shadowMainLooper().idle();
        
        
        latch.await(1, TimeUnit.SECONDS);
        
        
        ShadowLooper.shadowMainLooper().idle();
        
        
        SensorData data = capturedData.get();
        assertEquals(75.5f, data.getValue(), 0.01f);
        assertEquals(1234567890L, data.getTimestamp());
    }
    
    @Test
    public void testDustSensorNotificationProcessing() throws InterruptedException {
        
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(
                DUST_UUID, 
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ);
        
        
        String validJson = "{\"messageType\":\"DUST_SENSOR_DATA\",\"data\":25.5,\"timeStamp\":1234567890}";
        characteristic.setValue(validJson.getBytes(StandardCharsets.UTF_8));
        
        
        CountDownLatch latch = new CountDownLatch(1);
        
        
        final AtomicReference<SensorData> capturedData = new AtomicReference<>();
        BluetoothServiceIntegration.SensorDataListener listener = (data, type) -> {
            if (SensorData.TYPE_DUST.equals(type)) {
                capturedData.set(data);
                latch.countDown();
            }
        };
        
        
        serviceIntegration.addSensorDataListener(listener);
        
        
        serviceIntegration.onCharacteristicChanged(characteristic);
        
        
        ShadowLooper.shadowMainLooper().idle();
        
        
        latch.await(1, TimeUnit.SECONDS);
        
        
        ShadowLooper.shadowMainLooper().idle();
        
        
        SensorData data = capturedData.get();
        assertEquals(25.5f, data.getValue(), 0.01f);
        assertEquals(1234567890L, data.getTimestamp());
    }
    
    @Test
    public void testInvalidJsonFallbackBehavior() throws InterruptedException {
        
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(
                SOUND_UUID, 
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ);
        
        
        String invalidJson = "{This is not valid JSON";
        characteristic.setValue(invalidJson.getBytes(StandardCharsets.UTF_8));
        
        
        CountDownLatch latch = new CountDownLatch(1);
        
        
        final AtomicReference<SensorData> capturedData = new AtomicReference<>();
        BluetoothServiceIntegration.SensorDataListener listener = (data, type) -> {
            if (SensorData.TYPE_NOISE.equals(type)) {
                capturedData.set(data);
                latch.countDown();
            }
        };
        
        
        serviceIntegration.addSensorDataListener(listener);
        
        
        serviceIntegration.onCharacteristicChanged(characteristic);
        
        
        ShadowLooper.shadowMainLooper().idle();
        
        
        latch.await(1, TimeUnit.SECONDS);
        
        
        ShadowLooper.shadowMainLooper().idle();
        
        
        SensorData data = capturedData.get();
        assertEquals(ESP32BluetoothSpec.NotificationParams.INITIAL_SOUND_VALUE, data.getValue(), 0.01f);
    }
    
    @Test
    public void testOutOfOrderTimestampHandling() throws InterruptedException {
        
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(
                SOUND_UUID, 
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ);
        
        
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        
        
        final AtomicReference<SensorData> lastCapturedData = new AtomicReference<>();
        BluetoothServiceIntegration.SensorDataListener listener = (data, type) -> {
            if (SensorData.TYPE_NOISE.equals(type)) {
                lastCapturedData.set(data);
                if (latch1.getCount() > 0) {
                    latch1.countDown();
                } else {
                    latch2.countDown();
                }
            }
        };
        serviceIntegration.addSensorDataListener(listener);
        
        
        String json1 = "{\"messageType\":\"SOUND_SENSOR_DATA\",\"data\":70.0,\"timeStamp\":2000}";
        characteristic.setValue(json1.getBytes(StandardCharsets.UTF_8));
        serviceIntegration.onCharacteristicChanged(characteristic);
        
        
        ShadowLooper.shadowMainLooper().idle();
        
        
        ShadowLooper.idleMainLooper();
        
        
        latch1.await(1, TimeUnit.SECONDS);
        
        
        ShadowLooper.shadowMainLooper().idle();
        
        
        assertNotNull(lastCapturedData.get());
        assertEquals(70.0f, lastCapturedData.get().getValue(), 0.01f);
        
        
        String json2 = "{\"messageType\":\"SOUND_SENSOR_DATA\",\"data\":60.0,\"timeStamp\":1000}";
        characteristic.setValue(json2.getBytes(StandardCharsets.UTF_8));
        serviceIntegration.onCharacteristicChanged(characteristic);
        
        
        ShadowLooper.shadowMainLooper().idle();
        
        
        ShadowLooper.idleMainLooper();
        
        boolean processed = latch2.await(2, TimeUnit.SECONDS);
        
        ShadowLooper.shadowMainLooper().idle();
        

        assertNotNull("Second notification was not processed", lastCapturedData.get());
        assertEquals(60.0f, lastCapturedData.get().getValue(), 0.01f);
    }
} 