// need to update this later WHENHW ready

package com.team12.smarthat.utils;

import java.util.UUID;

public class Constants {
    public static final boolean DEV_MODE = true;
    
    // Enable this to log all available services/characteristics when the exact UUIDs aren't found
    // This can help identify the correct UUIDs used by your ESP32
    public static final boolean ENABLE_SERVICE_DISCOVERY_DEBUG = true;
    
    // SmartHat device MAC address - used as fallback when UUID-based scanning doesn't find a device
    // IMPORTANT: UUID-based scanning is now the primary method for device discovery
    public static final String ESP32_MAC_ADDRESS = "EC:94:CB:4D:91:E2";
    
    // BLE Service and Characteristic UUIDs - DEVICE DISCOVERY IS NOW BASED ON SERVICE_UUID
    // NOTE: These must match exactly what is used on your ESP32 device
    // The app will scan for devices advertising this specific service UUID
    public static final UUID SERVICE_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef0");
    public static final UUID DUST_CHARACTERISTIC_UUID = UUID.fromString("dcba4321-8765-4321-8765-654321fedcba");
    public static final UUID SOUND_CHARACTERISTIC_UUID = UUID.fromString("abcd1234-5678-1234-5678-abcdef123456");
    
    // Standard Client Configuration Descriptor UUID for enabling notifications
    public static final UUID CLIENT_CONFIG_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    
    // ble constants
    public static final long SCAN_PERIOD = 10000; // 10 seconds
    public static final long CONNECTION_TIMEOUT = 8000; // 8 seconds (optimized for Pixel 4a)
    public static final long RETRY_DELAY = 1000; // 1 second delay between retry attempts
    public static final int MAX_CONNECTION_RETRIES = 3;

    // HW TYPE !!!!
    public static final String MESSAGE_TYPE_DUST = "DUST_SENSOR_DATA";
    public static final String MESSAGE_TYPE_SOUND = "SOUND_SENSOR_DATA";

    // notification constants
    public static final String NOTIFICATION_CHANNEL_ID = "sensor_alerts";
    public static final int NOTIFICATION_ID = 1001;
    public static final int NOTIFICATION_ID_DUST = 1002;
    public static final int NOTIFICATION_ID_NOISE = 1003;
    public static final int NOTIFICATION_ID_GENERAL = 1001;
    
    // cooldown
    public static final long NOTIFICATION_COOLDOWN_GENERAL = 5000; // 5sec but might increase
    public static final long NOTIFICATION_COOLDOWN_TYPE = 30000; // 30sec same type apart

    // sensor thresholds 
    public static final float DUST_PM25_THRESHOLD = 50.0f; // microg/m^3
    public static final float NOISE_THRESHOLD = 85.0f; // dB
    public static final float DUST_THRESHOLD = DUST_PM25_THRESHOLD;
    
    // OSHA noise exposure standards
    public static final float[] OSHA_NOISE_LEVELS = {90.0f, 92.0f, 95.0f, 97.0f, 100.0f, 102.0f, 105.0f, 110.0f, 115.0f};
    public static final long[] OSHA_EXPOSURE_TIMES = {
        8 * 60 * 60 * 1000,  // 8 hours in milliseconds
        6 * 60 * 60 * 1000,  // 6 hours
        4 * 60 * 60 * 1000,  // 4 hours
        3 * 60 * 60 * 1000,  // 3 hours
        2 * 60 * 60 * 1000,  // 2 hours
        1 * 60 * 60 * 1000 + 30 * 60 * 1000,  // 1.5 hours
        1 * 60 * 60 * 1000,  // 1 hour
        30 * 60 * 1000,      // 30 minutes
        15 * 60 * 1000       // 15 minutes
    };
    
    // sensor value ranges (for validation)
    public static final float DUST_MIN_VALUE = 0.0f;
    public static final float DUST_MAX_VALUE = 1000.0f;
    public static final float NOISE_MIN_VALUE = 0.0f;
    public static final float NOISE_MAX_VALUE = 140.0f;

    // connection states
    public static final int STATE_DISCONNECTED = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;

    // permission request codes
    // Note: Location permission is also required for BLE scanning on most Android versions
    public static final int REQUEST_BLUETOOTH_CONNECT = 999;
    public static final int REQUEST_NOTIFICATION_PERMISSION = 998;
    public static final int REQUEST_ENABLE_BT = 1;

    // log tags
    public static final String TAG_MAIN = "MainActivity";
    public static final String TAG_BLUETOOTH = "Bluetooth";
    public static final String TAG_DATABASE = "Database";
    
    // database constants
    public static final int MAX_DATABASE_RECORDS = 10000; // max records
    public static final long DATABASE_CLEANUP_INTERVAL = 86400000;
}