package com.team12.smarthat.bluetooth.devices.esp32;

import java.util.UUID;

public class ESP32BluetoothSpec {

    public static final UUID SERVICE_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef0");
    

    public static final UUID DUST_CHARACTERISTIC_UUID = UUID.fromString("dcba4321-8765-4321-8765-654321fedcba");
    

    public static final UUID SOUND_CHARACTERISTIC_UUID = UUID.fromString("abcd1234-5678-1234-5678-abcdef123456");
    

    public static final UUID CLIENT_CONFIG_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    public static class ConnectionParams {
        public static final int PRIORITY_BALANCED = 1;

        public static final int PRIORITY_HIGH = 0;
        
        // low power mode
        public static final int PRIORITY_LOW_POWER = 2;

        public static final int RECOMMENDED_MTU = 185;

        public static final int PREFERRED_PHY = 2;
        
        // how many times to retry
        public static final int MAX_RETRY_COUNT = 3;

        public static final long CONNECTION_TIMEOUT = 8000;
        
        // max reconnection attempts
        public static final int MAX_RECONNECTION_ATTEMPTS = 5;
        
        // time between reconnection tries
        public static final long BASE_RECONNECTION_DELAY = 1000;

        public static final int ANDROID12_CONN_INTERVAL = 80;

        public static final int ANDROID12_SLAVE_LATENCY = 0;

        public static final int ANDROID12_SUPERVISION_TIMEOUT = 200;
    }
    

    public static class SleepBehavior {

        public static final long LIGHT_SLEEP_TIMEOUT = 30000;

        public static final long DEEP_SLEEP_TIMEOUT = 300000;
        public static final long WAKE_INTERVAL = 60000;
    }

    public static class ErrorCodes {

        public static final int GATT_ERROR = 133;

        public static final int CONNECTION_TIMEOUT = 8;

        public static final int CONN_PARAM_REJECTED = 22;

        public static final int UNSUPPORTED_TRANSPORT = 257;
    }
} 