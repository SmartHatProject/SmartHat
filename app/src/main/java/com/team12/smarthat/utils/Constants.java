//will update
package com.team12.smarthat.utils;

import java.util.UUID;

public class Constants {
// bluetooth config
public static final String ESP32_MAC_ADDRESS = "00:11:22:33:44:55"; //will update this later - ask Rasham maybe
public static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

// ble stuff
public static final UUID SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b"); // our service
public static final UUID DUST_CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8"); // dust data
public static final UUID NOISE_CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a9"); // noise data
public static final int SCAN_PERIOD = 10000; // scan timeout in ms

// notif config
public static final String NOTIFICATION_CHANNEL_ID = "sensor_alerts";
public static final int NOTIFICATION_ID = 1001;

//tresholds
    //will update based on sensor guys later
public static final float DUST_PM25_THRESHOLD = 50.0f; // microg/m^3
public static final float NOISE_THRESHOLD = 85.0f; // dB
// last update on mainactivity
public static final float DUST_THRESHOLD = DUST_PM25_THRESHOLD;

public static final String STATE_CONNECTED = "CONNECTED";
public static final String STATE_DISCONNECTED = "DISCONNECTED";
public static final String STATE_CONNECTING = "CONNECTING"; // added for ble which has more states

//permission request codes
public static final int REQUEST_BLUETOOTH_PERMISSIONS = 101;
public static final int REQUEST_NOTIFICATION_PERMISSION = 102;
public static final int REQUEST_LOCATION_PERMISSION = 103; // needed for ble scanning

//loggig tags
public static final String TAG_MAIN = "MainActivity";
public static final String TAG_BLUETOOTH = "Bluetooth";
public static final String TAG_DATABASE = "Database";
public static final String TAG_PERMISSIONS = "Permissions";

public static final int REQUEST_ENABLE_BT = 201;
public static final String INVALID_MAC = "Invalid MAC address";
}