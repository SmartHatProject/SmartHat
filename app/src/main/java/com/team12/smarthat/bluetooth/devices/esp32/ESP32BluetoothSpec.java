package com.team12.smarthat.bluetooth.devices.esp32;

import java.util.UUID;


public class ESP32BluetoothSpec {

    /** Main service UUID for the SmartHat device */
    public static final UUID SERVICE_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef0");
    
    /** Dust sensor characteristic UUID */
    public static final UUID DUST_CHARACTERISTIC_UUID = UUID.fromString("dcba4321-8765-4321-8765-654321fedcba");
    
    /** Sound sensor characteristic UUID */
    public static final UUID SOUND_CHARACTERISTIC_UUID = UUID.fromString("abcd1234-5678-1234-5678-abcdef123456");
    
    /** Client Characteristic Configuration Descriptor (CCCD) UUID for enabling notifications */
    public static final UUID CLIENT_CONFIG_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    
    /** Gas sensor characteristic UUID */
    public static final UUID GAS_CHARACTERISTIC_UUID = UUID.fromString("b6fc48af-6b61-4f96-afdf-a359a8b2b1b1");

    
    public static class ConnectionParams {
        // Priority values for connection priority request
        // Changed to PRIORITY_HIGH for improved responsiveness
        public static final int PRIORITY_BALANCED = 1;
        public static final int PRIORITY_HIGH = 0;
        public static final int PRIORITY_LOW_POWER = 2;

        // MTU size - matches the config (512 bytes)
        public static final int RECOMMENDED_MTU = 512;

        public static final int PREFERRED_PHY = 2;
        
        // Connection retry parameters
        public static final int MAX_RETRY_COUNT = 3;
        public static final long CONNECTION_TIMEOUT = 8000;
        
        // Reconnection parameters
        public static final int MAX_RECONNECTION_ATTEMPTS = 5;
        public static final long BASE_RECONNECTION_DELAY = 1000;

        public static final int ANDROID12_CONN_INTERVAL = 24;  // ~30ms
        public static final int ANDROID12_SLAVE_LATENCY = 0;   
        public static final int ANDROID12_SUPERVISION_TIMEOUT = 400; // ~500ms
    }
    
    
    public static class SleepBehavior {
        // Light sleep timeout (30 seconds)
        public static final long LIGHT_SLEEP_TIMEOUT = 30000;

        // Deep sleep timeout (5 minutes)
        public static final long DEEP_SLEEP_TIMEOUT = 300000;
        public static final long WAKE_INTERVAL = 60000;
    }

    
    public static class ErrorCodes {
        // Common GATT error on Android 12
        public static final int GATT_ERROR = 133;

        // Connection timeout
        public static final int CONNECTION_TIMEOUT = 8;

        // Connection parameters rejected
        public static final int CONN_PARAM_REJECTED = 22;

        // Unsupported transport
        public static final int UNSUPPORTED_TRANSPORT = 257;
    }
    
   
    public static class NotificationParams {
        // Timeout to consider notifications missing (5 seconds)
        public static final long NOTIFICATION_TIMEOUT_MS = 5000;
        
        // Expected frequency of notifications (approximately 1 second)
        public static final long EXPECTED_NOTIFICATION_INTERVAL_MS = 1000;
        
        // Maximum time deviation for timestamp validation
        public static final long MAX_TIMESTAMP_DEVIATION_MS = 60000; // 60 seconds
        
        // Default initial values matching hardware
        public static final float INITIAL_SOUND_VALUE = 40.0f; // Default quiet room value
        public static final float INITIAL_DUST_VALUE = 10.0f;  // Default clean air value
    }
} 