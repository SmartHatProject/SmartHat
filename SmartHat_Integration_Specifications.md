# SmartHat Integration Specifications

This document outlines the critical specifications for integration between the SmartHat Android application and ESP32 hardware implementation. These specifications **must be followed exactly** to ensure proper communication between the hardware and software components.

## Table of Contents
- [1. BLE Integration Requirements](#1-ble-integration-requirements)
- [2. App → Hardware Specifications](#2-app--hardware-specifications)
- [3. Hardware → App Information](#3-hardware--app-information)
- [4. Implementation Example](#4-implementation-example)

## 1. BLE Integration Requirements

⚠️ **CRITICAL**: The ESP32 firmware **MUST** use Bluetooth Low Energy (BLE) protocol, not Classic Bluetooth or Bluetooth Serial. The current implementation using `BluetoothSerial` is **incompatible** with the Android application.

## 2. App → Hardware Specifications

The following specifications are provided by the Android app team and **must be implemented** by the hardware team:

| Category | Parameter | Value | Notes |
|----------|-----------|-------|-------|
| **BLE UUIDs** | Service UUID | `4fafc201-1fb5-459e-8fcc-c5c9c331914b` | **MUST match exactly** |
| | Dust Characteristic UUID | `beb5483e-36e1-4688-b7f5-ea07361b26a8` | **MUST match exactly** |
| | Noise Characteristic UUID | `beb5483e-36e1-4688-b7f5-ea07361b26a9` | **MUST match exactly** |
| | Client Config Descriptor UUID | `00002902-0000-1000-8000-00805f9b34fb` | Standard UUID for BLE notifications |
| **BLE Configuration** | Device Name | Must contain "SmartHat" | E.g., "SmartHat", "SmartHat_v1", etc. |
| | Protocol | BLE 4.2 or higher | **NOT** Classic Bluetooth |
| | Scan Response | Enabled | For better discovery |
| **Threshold Alerts** | Dust Threshold | 50.0 µg/m³ | Values above this trigger alerts |
| | Noise Threshold | 85.0 dB | Values above this trigger alerts |
| **Data Format Support** | Preferred Format | Raw string values ("42.5") | Simplest and most efficient |
| | Alternative Format | JSON with messageType | {"messageType":"DUST_SENSOR_DATA", "data":42.5, "timeStamp":1234567890} |
| | Message Types | "DUST_SENSOR_DATA", "SOUND_SENSOR_DATA" | Case-sensitive, use exactly as shown |
| **Connection Parameters** | Scan Timeout | 10,000 ms (10 sec) | Ensure device advertises during this period |
| | Connection Priority | High | For responsive data transmission |
| | Update Interval | 1000 ms (1 sec) recommended | Balance between responsiveness and power |

## 3. Hardware → App Information

The hardware team should complete this table and return it to the app team:

| Category | Parameter | Description | Value/Info (to be filled by HW team) | Notes/Comments |
|----------|-----------|-------------|--------------------------------------|----------------|
| **Sensor Specifications** | Dust Sensor Model | Model of dust sensor used | | For documentation |
| | Noise Sensor Model | Model of noise sensor used | | For documentation |
| | Value Units | Units for each measurement | | Confirm: Dust: µg/m³, Noise: dB |
| | Value Precision | Decimal precision | | Recommend 2 decimal places |
| | Value Ranges | Min/max sensor values | | Needed for display calibration |
| **Data Transmission** | Sampling Rate | Frequency of readings | | How often readings are sent |
| | Data Format | Format chosen from options | | Which format will be implemented |
| **Hardware Details** | Power Requirements | Battery life, power consumption | | For user documentation |
| | Physical Dimensions | Size and weight | | For user documentation |
| | LED Indicators | LED patterns and meanings | | For user troubleshooting |
| **Test Features** | Test Mode Support | Whether test mode is implemented | | For development testing |

## 4. Implementation Example

Here's a working example of ESP32 BLE implementation that meets these requirements:

```cpp
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// MUST match the UUIDs in the Android app exactly
#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define DUST_CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"
#define NOISE_CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a9"

// Function prototypes
float getDustSensorReading();
float getSoundSensorReading();

BLEServer* pServer = NULL;
BLECharacteristic* pDustCharacteristic = NULL;
BLECharacteristic* pNoiseCharacteristic = NULL;
bool deviceConnected = false;

class ServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
      deviceConnected = true;
      Serial.println("Device connected");
    };

    void onDisconnect(BLEServer* pServer) {
      deviceConnected = false;
      Serial.println("Device disconnected");
      // Restart advertising to allow reconnection
      BLEDevice::startAdvertising();
    }
};

void setup() {
  Serial.begin(115200);
  Serial.println("Starting SmartHat BLE device");
  
  // Initialize BLE
  BLEDevice::init("SmartHat");
  
  // Create server
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new ServerCallbacks());
  
  // Create service
  BLEService* pService = pServer->createService(SERVICE_UUID);
  
  // Create characteristics
  pDustCharacteristic = pService->createCharacteristic(
                     DUST_CHARACTERISTIC_UUID,
                     BLECharacteristic::PROPERTY_READ | 
                     BLECharacteristic::PROPERTY_NOTIFY
                   );
  
  pNoiseCharacteristic = pService->createCharacteristic(
                      NOISE_CHARACTERISTIC_UUID,
                      BLECharacteristic::PROPERTY_READ | 
                      BLECharacteristic::PROPERTY_NOTIFY
                    );
  
  // Add descriptor for notifications
  pDustCharacteristic->addDescriptor(new BLE2902());
  pNoiseCharacteristic->addDescriptor(new BLE2902());
  
  // Start service
  pService->start();
  
  // Start advertising
  BLEAdvertising* pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(false);
  pAdvertising->setMinPreferred(0x0);
  BLEDevice::startAdvertising();
  
  Serial.println("BLE service started, waiting for connections...");
}

void loop() {
  if (deviceConnected) {
    // Get sensor readings
    float dustValue = getDustSensorReading();
    float soundValue = getSoundSensorReading();
    
    // Option 1: Simple format (recommended)
    char dustStr[8];
    char soundStr[8];
    dtostrf(dustValue, 1, 2, dustStr);
    dtostrf(soundValue, 1, 2, soundStr);
    
    pDustCharacteristic->setValue(dustStr);
    pDustCharacteristic->notify();
    
    delay(500); // Small delay between characteristic updates
    
    pNoiseCharacteristic->setValue(soundStr);
    pNoiseCharacteristic->notify();
    
    Serial.printf("Sent Dust: %s, Sound: %s\n", dustStr, soundStr);
    delay(1000); // 1 second between readings
  }
}

// These are placeholders - use your actual sensor reading functions
float getDustSensorReading() {
  // Replace with your actual dust sensor code
  return random(0, 100); // Mock value between 0-100 µg/m³
}

float getSoundSensorReading() {
  // Replace with your actual sound sensor code
  return random(30, 90); // Mock value between 30-90 dB
}
```

## Important Notes

1. **JSON Format Option**: If you prefer to use your existing JSON message format, you can adapt your `Message` class to send data through BLE characteristics instead of BluetoothSerial. The app supports JSON with message types "DUST_SENSOR_DATA" and "SOUND_SENSOR_DATA".

2. **Testing Connection**: Once implemented, test the connection by opening the SmartHat Android app, enabling Bluetooth, and pressing "Connect". The app should discover the ESP32 device and establish a connection.

3. **Troubleshooting**: If connection issues occur, verify:
   - The UUIDs match exactly
   - The device name contains "SmartHat"
   - BLE (not BluetoothSerial) is being used
   - Bluetooth permissions are granted on the Android device

For any questions or clarifications, please contact the SmartHat app development team. 