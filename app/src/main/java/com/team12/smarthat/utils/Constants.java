// need to update this when hardware is ready

package com.team12.smarthat.utils;

// removed java uuid import since we don't define uuids here anymore

public class Constants {
    public static final boolean DEV_MODE = true;
    
    // Control verbose debug logging across the app
    public static final boolean ENABLE_DEBUG_LOGGING = false;
    
   
    public static final boolean ENABLE_SERVICE_DISCOVERY_DEBUG = true;
    
    public static final String ESP32_MAC_ADDRESS = "EC:94:CB:4D:91:E2";
    
    // moved all ble uuids to esp32bluetoothspec.java to keep esp32 stuff together

    
    // scan settings
    public static final long SCAN_PERIOD = 10000; // 10 secs
    
    // hardware message types
    public static final String MESSAGE_TYPE_DUST = "DUST_SENSOR_DATA";
    public static final String MESSAGE_TYPE_SOUND = "SOUND_SENSOR_DATA";
    public static final String MESSAGE_TYPE_GAS = "GAS_SENSOR_DATA";

    // notification stuff
    public static final String NOTIFICATION_CHANNEL_ID = "sensor_alerts";
    public static final int NOTIFICATION_ID = 1001;
    public static final int NOTIFICATION_ID_DUST = 1002;
    public static final int NOTIFICATION_ID_NOISE = 1003;
    public static final int NOTIFICATION_ID_GAS = 1004;
    public static final int NOTIFICATION_ID_GENERAL = 1001;
    
    // cooldown times
    public static final long NOTIFICATION_COOLDOWN_GENERAL = 5000; // 5sec might bump this up later
    public static final long NOTIFICATION_COOLDOWN_TYPE = 20000; // 20sec between same type of alert

    // when to alert the user 
    public static final float DUST_PM25_THRESHOLD = 50.0f; // microg/m^3
    public static final float NOISE_THRESHOLD = 85.0f; // dB
    public static final float GAS_THRESHOLD = 1000.0f; // ppm
    public static final float DUST_THRESHOLD = DUST_PM25_THRESHOLD;
    
    // osha says these noise levels are bad for these durations
    public static final float[] OSHA_NOISE_LEVELS = {90.0f, 92.0f, 95.0f, 97.0f, 100.0f, 102.0f, 105.0f, 110.0f, 115.0f};
    public static final long[] OSHA_EXPOSURE_TIMES = {
        8 * 60 * 60 * 1000,  // 8 hrs
        6 * 60 * 60 * 1000,  // 6 hrs
        4 * 60 * 60 * 1000,  // 4 hrs
        3 * 60 * 60 * 1000,  // 3 hrs
        2 * 60 * 60 * 1000,  // 2 hrs
        1 * 60 * 60 * 1000 + 30 * 60 * 1000,  // 1.5 hrs
        1 * 60 * 60 * 1000,  // 1 hr
        30 * 60 * 1000,      // 30 mins
        15 * 60 * 1000       // 15 mins
    };
    
    // valid range for sensor readings
    public static final float DUST_MIN_VALUE = 0.0f;
    public static final float DUST_MAX_VALUE = 1000.0f;
    public static final float NOISE_MIN_VALUE = 0.0f;
    public static final float NOISE_MAX_VALUE = 140.0f;
    public static final float GAS_MIN_VALUE = 0.0f;
    public static final float GAS_MAX_VALUE = 5000.0f;

    // connection states moved to bleconnectionmanager.connectionstate enum
    
    // permission codes
    public static final int REQUEST_NOTIFICATION_PERMISSION = 998;
    public static final int REQUEST_ENABLE_BT = 1;

    // log tags for debugging
    public static final String TAG_MAIN = "MainActivity";
    public static final String TAG_BLUETOOTH = "Bluetooth";
    public static final String TAG_DATABASE = "Database";
    
    // database settings
    public static final int MAX_DATABASE_RECORDS = 10000; // don't store too much
    public static final long DATABASE_CLEANUP_INTERVAL = 86400000;
    
    // app preferences
    public static final String PREF_NAME = "app_prefs";
    public static final String PREF_NOTIFICATIONS_ENABLED = "notifications_enabled";
    public static final String PREF_DUST_NOTIFICATIONS_ENABLED = "dust_notifications_enabled";
    public static final String PREF_NOISE_NOTIFICATIONS_ENABLED = "noise_notifications_enabled";
    public static final String PREF_GAS_NOTIFICATIONS_ENABLED = "gas_notifications_enabled";
    public static final String PREF_DUST_THRESHOLD = "dust_threshold";
    public static final String PREF_NOISE_THRESHOLD = "noise_threshold";
    public static final String PREF_GAS_THRESHOLD = "gas_threshold";
    public static final String PREF_FILTER_START_DATE = "filter_start_date";
    public static final String PREF_FILTER_END_DATE = "filter_end_date";
    
    // test mode preferences
    public static final String PREF_TEST_MODE_ACTIVE = "test_mode_active";
    public static final String PREF_TEST_MODE_TYPE = "test_mode_type";
    public static final String PREF_CONNECTION_STATE = "connection_state";
}