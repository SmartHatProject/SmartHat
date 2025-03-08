# SmartHat ESP32 BLE Implementation Guide

This document outlines the **exact requirements** for implementing Bluetooth Low Energy (BLE) on the ESP32 for compatibility with the SmartHat Android application.

## Critical Requirements

### 1. BLE Protocol (NOT Classic Bluetooth)

You **MUST** use Bluetooth Low Energy (BLE), not Classic Bluetooth or Bluetooth Serial. Your current implementation using `BluetoothSerial` is **incompatible** with our application.

### 2. UUIDs (MUST MATCH EXACTLY)

The ESP32 BLE implementation must use these **exact** UUIDs:

```cpp
// Main service that contains all characteristics
#define SERVICE_UUID "4fafc201-1fb5-459e-8fcc-c5c9c331914b"

// Characteristics for sensor data
#define DUST_CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"
#define NOISE_CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a9"

// Standard BLE Client Configuration Descriptor (for notifications)
#define CLIENT_CONFIG_DESCRIPTOR_UUID "00002902-0000-1000-8000-00805f9b34fb"
```

### 3. Device Name

Set your BLE device name to include "SmartHat" (our app scans for this string):

```cpp
BLEDevice::init("SmartHat");
```

## Data Format

Our app supports multiple data formats for flexibility. Choose ONE of these formats:

### Option 1: Simple Numeric Values (Recommended)
Send plain string values through each characteristic:
```
"42.5"  // Just the number as string
```

### Option 2: JSON Format with Message Types (Compatible with your current code)
```json
{
  "messageType": "DUST_SENSOR_DATA",
  "data": 42.5,
  "timeStamp": 1234567890
}
```

Message type must be one of:
- `"DUST_SENSOR_DATA"` for dust sensor readings
- `"SOUND_SENSOR_DATA"` for noise sensor readings

## Implementation Example

Here's a complete, working example for your ESP32:

```cpp
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// MUST match the UUIDs in the Android app exactly
#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define DUST_CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"
#define NOISE_CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a9"

// Function prototypes from your existing code
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
    
    // Option 2: JSON Format (alternatively, if you prefer to keep using JSON)
    /*
    char jsonBuffer[100];
    sprintf(jsonBuffer, "{\"messageType\":\"DUST_SENSOR_DATA\",\"data\":%.2f,\"timeStamp\":%ld}", 
            dustValue, millis());
    pDustCharacteristic->setValue(jsonBuffer);
    pDustCharacteristic->notify();
    
    delay(500);
    
    sprintf(jsonBuffer, "{\"messageType\":\"SOUND_SENSOR_DATA\",\"data\":%.2f,\"timeStamp\":%ld}", 
            soundValue, millis());
    pNoiseCharacteristic->setValue(jsonBuffer);
    pNoiseCharacteristic->notify();
    */
    
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

## Replacing Your Current Implementation

Your current code uses `BluetoothSerial` and JSON messages:

```cpp
#include <BluetoothSerial.h>
#include <ArduinoJson.h>
#include "Message.h"

BluetoothSerial SerialBT;

// ...

void loop() {
    float dustSensorReading = getDustSensorReading();
    float soundSensorReading = getSoundSensorReading();

    Message dustMessage(Message::DUST_DATA_MESSAGE, dustSensorReading);
    serializeJson(dustMessage.getJsonMessage(), SerialBT);

    Message soundMessage(Message::SOUND_DATA_MESSAGE, soundSensorReading);
    serializeJson(soundMessage.getJsonMessage(), SerialBT);

    SerialBT.println();
    delay(1000);
}
```

You need to **completely replace** this with the BLE implementation shown above. The Message class can be adapted if you choose the JSON format option.

## Testing Connection with the Android App

1. Install the SmartHat Android app
2. Implement this BLE code on your ESP32
3. Open the app and press "Connect"
4. The app should discover your device and connect
5. Sensor readings should appear in the app

## Need Help?

If you need assistance implementing this BLE code or have questions, please contact us.

**Note: This implementation is non-negotiable for compatibility between the ESP32 and the Android app.** 