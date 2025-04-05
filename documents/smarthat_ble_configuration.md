# Smarthat BLE Configuration

## Device Info
- name: SmartHat
  > BleHandler.cpp: `BLEDevice::init("SmartHat");`
- service uuid: 12345678-1234-5678-1234-56789abcdef0
  > BleHandler.cpp: `#define SERVICE_UUID "12345678-1234-5678-1234-56789abcdef0"`
- uuids defined in: blehandler.cpp and smarthat-driver.ino

## Characteristics

### Sound Sensor
- uuid: abcd1234-5678-1234-5678-abcdef123456
  > BleHandler.cpp: `#define SOUND_CHARACTERISTIC_UUID "abcd1234-5678-1234-5678-abcdef123456"`
- properties: read, notify
  > BleHandler.cpp: `BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY`
- format: json string
- range: 30-130 db
  > NoiseSensor.cpp: `if (dbValue < 30.0) { dbValue = 30.0; } if (dbValue > 130.0) { dbValue = 130.0; }`
- initial value: 40.0 db (quiet room)
  > BleHandler.cpp: `Message initialSoundMessage = Message(Message::SOUND_SENSOR_DATA, 40.0f); // Default to 40 dB (quiet room)`
- example: `{"messageType":"SOUND_SENSOR_DATA","data":45.5,"timeStamp":1234567890}`

### Dust Sensor
- uuid: dcba4321-8765-4321-8765-654321fedcba
  > BleHandler.cpp: `#define DUST_CHARACTERISTIC_UUID "dcba4321-8765-4321-8765-654321fedcba"`
- properties: read, notify
  > BleHandler.cpp: `BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY`
- format: json string
- range: 0-1000 micrograms/m³
  > Message.cpp: `if (data < 0.0f || data > 1000.0f) { Serial.print("WARNING: Dust value out of typical range: "); }`
- initial value: 10.0 micrograms/m³ (clean air)
  > BleHandler.cpp: `Message initialDustMessage = Message(Message::DUST_SENSOR_DATA, 10.0f);  // Default to 10 µg/m³ (clean air)`
- example: `{"messageType":"DUST_SENSOR_DATA","data":25.5,"timeStamp":1234567890}`

### Gas Sensor
- uuid: b6fc48af-6b61-4f96-afdf-a359a8b2b1b1
  > ESP32BluetoothSpec.java: `public static final UUID GAS_CHARACTERISTIC_UUID = UUID.fromString("b6fc48af-6b61-4f96-afdf-a359a8b2b1b1");`
- properties: read, notify
  > Similar to other sensors: `BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY`
- format: json string
- range: 0-400 ppm (parts per million)
  > Constants.java: `public static final float GAS_MAX_VALUE = 400.0f;`
- example: `{"messageType":"GAS_SENSOR_DATA","data":42.5,"timeStamp":1680456789}`

## JSON Format
- messageType: "SOUND_SENSOR_DATA", "DUST_SENSOR_DATA", or "GAS_SENSOR_DATA" (exact strings required)
  > Message.cpp: `const char* Message::DUST_SENSOR_DATA = "DUST_SENSOR_DATA"; const char* Message::SOUND_SENSOR_DATA = "SOUND_SENSOR_DATA";`
  > Constants.java: `public static final String MESSAGE_TYPE_GAS = "GAS_SENSOR_DATA";`
- data: float value in db, micrograms/m³, or ppm
  > Message.cpp: `jsonDoc["data"] = this->data;`
- timeStamp: milliseconds since device boot (note camelCase with capital 'S')
  > Message.cpp: `jsonDoc["timeStamp"] = currentTimestamp;`
- size: <200 bytes (staticjsondocument constraint)
  > Message.cpp: `StaticJsonDocument<200> jsonDoc;`
- all fields required for valid json
  > Message.cpp: `if (jsonString.indexOf("messageType") == -1 || jsonString.indexOf("data") == -1 || jsonString.indexOf("timeStamp") == -1) {`

## Alert Thresholds
- dust: >50.0 micrograms/m³ (immediate alert)
  > smarthat-driver.ino: `#define DUST_ALERT_THRESHOLD 50.0`
- sound: >85.0 db sustained for 4 seconds
  > NoiseSensor.cpp: `const float SOUND_ALERT_THRESHOLD = 85.0; if (elapsedTime >= 4) { triggerAlert(); }`
- gas: >100.0 ppm (immediate alert)
  > Constants.java: `public static final float GAS_THRESHOLD = 100.0f;`

## Update Frequency
- readings update every ~1 second
  > smarthat-driver.ino: `const unsigned long UPDATE_INTERVAL = 1000;`
- notifications sent when connected
  > BleHandler.cpp: `if (deviceConnected) { ... pSoundCharacteristic->notify(); }`
- initial values sent immediately on connection
  > smarthat-driver.ino: `if (currentConnectionStatus != lastConnectionStatus) { if (currentConnectionStatus) { ... bleHandler.updateDustLevel(dustReading); } }`
- auto-resume on reconnection
  > BleHandler.cpp: `BLEDevice::startAdvertising(); Serial.println("Started advertising again");`

## Notification Implementation

### Enabling Notifications
- write value 0x0100 to descriptor uuid 0x2902 (cccd) on each characteristic
- sequence: discover services → get characteristics → write to descriptor → register callback
- re-enable notifications after any reconnection
- both characteristics must have notifications enabled separately
- esp32 automatically sends notifications when values change (~1 second interval)
  > BleHandler.cpp: `pSoundCharacteristic->notify(); Serial.println("Sound notification sent");`

### Notification Verification
- after enabling notifications, verify you receive initial data within 5 seconds
- if no data received, try disabling and re-enabling notifications
- notifications should arrive approximately every 1 second while connected
- missing notifications for >5 seconds indicates a potential connection issue

### Notification Timing
- first notification: immediately after enabling notifications (initial values)
- subsequent notifications: every ~1 second
  > smarthat-driver.ino: `if (currentTime - lastUpdateTime >= UPDATE_INTERVAL) { ... bleHandler.updateDustLevel(dustSensorReading); }`
- potential burst of notifications after reconnection (catch-up data)
- each notification contains a timeStamp for ordering/validation
  > Message.cpp: `jsonDoc["timeStamp"] = currentTimestamp;`

## Connection Handling for App Developers
- implement connection state management
- negotiate 512 byte mtu immediately after connection
  > BleHandler.cpp: `#define MAX_MTU_SIZE 512` and `pServer->updatePeerMTU(pServer->getConnId(), MAX_MTU_SIZE);`
- implement 30-second connection timeout
- handle unexpected disconnects with auto-reconnect
- after reconnection, re-enable notifications on all characteristics
- store last valid readings in case of disconnection
- implement exponential backoff for reconnection attempts (5s, 10s, 20s, etc.)
- note: connection parameters (15-30ms interval) are recommendations for android implementation, not esp32 defaults

## JSON Parsing for App Developers
- verify all three required fields exist before parsing
- handle field name case sensitivity ("messageType" and "timeStamp" with exact camelCase)
  > Message.cpp: `jsonDoc["messageType"] = this->messageType;` and `jsonDoc["timeStamp"] = currentTimestamp;`
- validate messageType against expected constants before processing
- type-check data field as float/double
- implement range validation (30-130 db, 0-1000 micrograms/m³, 0-400 ppm)
  > Constants.java: `public static final float DUST_MAX_VALUE = 1000.0f; public static final float NOISE_MAX_VALUE = 140.0f; public static final float GAS_MAX_VALUE = 400.0f;`
- handle timeStamps for ordering (can be reset if device reboots)
- json errors should not crash app - maintain last valid reading
- implement gas sensor anomaly detection for better reliability
  > GasDataHandler.java: `private boolean isRealisticGasChange(float newValue, float oldValue, long timeDiffMs)`

## Initial Value Behavior
- during first connection, initial values are:
  - sound: 40.0 db
    > BleHandler.cpp: `Message initialSoundMessage = Message(Message::SOUND_SENSOR_DATA, 40.0f);`
  - dust: 10.0 micrograms/m³
    > BleHandler.cpp: `Message initialDustMessage = Message(Message::DUST_SENSOR_DATA, 10.0f);`
  - gas: 0.0 ppm (clean air)
    > GasDataHandler.java: `private final AtomicReference<Float> lastGasValue = new AtomicReference<>(0.0f);`
- app should display these immediately rather than "0.0" or empty values
- actual sensor readings will arrive within ~1 second after connection
- values never default to 0.0 (addresses previous "zero values" issue)
  > Message.cpp: `if (strcmp(msgType, DUST_SENSOR_DATA) == 0) { defaultValue = 10.0f; } else { defaultValue = 40.0f; }`

## Sensor Details

### Gas Sensor (MQ135)
- hardware: MQ135 gas sensor connected to ESP32
- pin: analog input 32
  > ESP32 Gas Code: `const int mq135Pin = 32;`
- load resistance (RL): 10.0 ohms
  > ESP32 Gas Code: `const float RL = 10.0;`
- calibration: dynamic baseline resistance (R0) calculation on startup
  > ESP32 Gas Code: `R0 = calibrateR0();`
- warm-up time: 10 seconds minimum recommended
  > ESP32 Gas Code: `delay(10000);  // Allow warm-up (10s minimum)`
- sampling: 100 readings averaged for stability
  > ESP32 Gas Code: `const int NUM_SAMPLES = 100;`
- adc: 12-bit resolution (0-4095), 3.3v reference
  > ESP32 Gas Code: `float voltage = analogValue * (3.3 / 4095.0);`
- conversion formula: log(ppm) = -2.769 * log10(Rs/R0) + 2.602
  > ESP32 Gas Code: `float a = -2.769; float b = 2.602; float log_ppm = a * log10(ratio) + b;`
- Rs calculation: Rs = (3.3 - voltage) * RL / voltage
  > ESP32 Gas Code: `float rs = (3.3 - voltage) * RL / voltage;`
- calibrated for CO2-like gases
  > ESP32 Gas Code: `// Curve for CO2-like behavior (adjust as needed dependent on specific gas)`
- update rate: readings every 10ms
  > ESP32 Gas Code: `delay(10);`

### Dust Sensor
- ir led control: active-low
  > DustSensor.cpp: `digitalWrite(LED_PIN, LOW);`
- sampling window: 280µs
  > DustSensor.cpp: `delayMicroseconds(280);`
- formula: ((voltage - 0.6) / 0.5) * 100.0 = micrograms/m³
  > DustSensor.cpp: `float density = ((v0 - 0.6) / 0.5) * 100.0;`
- clean air threshold: 0.6v
  > DustSensor.cpp: `if (v0 < 0.6f) { Serial.println("[WARN] Dust sensor voltage below detection threshold: " + String(v0, 3) + "V"); }`
- 5 readings averaged
  > smarthat-driver.ino: `const int numReadings = 5;`
- pins: sensor pin 35, led pin 25
  > smarthat-driver.ino: `#define DUST_SENSOR_PIN 35` and `#define DUST_SENSOR_LED_PIN 25`
- adc: 12-bit resolution (0-4095), 3.3v reference
  > smarthat-driver.ino: `analogReadResolution(12);` and `DustSensor dustSensor(DUST_SENSOR_PIN, DUST_SENSOR_LED_PIN, 4095, 3.3);`

### Sound Sensor
- formula: 20.0 * log10(voltage / 0.01) + 40.0 = db
  > NoiseSensor.cpp: `float dbValue = 20.0 * log10(avgVoltage / 0.01) + 40.0;`
- 5 readings averaged
  > NoiseSensor.cpp: `const int numReadings = 5;`
- range clamped: 30.0-130.0 db
  > NoiseSensor.cpp: `if (dbValue < 30.0) { Serial.println("[WARN] Calculated dB value below minimum, clamping to 30.0 dB"); dbValue = 30.0; }`
- alert after 4 seconds above threshold
  > NoiseSensor.cpp: `if (elapsedTime >= 4) { triggerAlert(); }`
- pin: analog input 34
  > smarthat-driver.ino: `NoiseSensor noiseSensor(34, 1.0, 20);`

## Error Handling
- json validation with fallbacks
  > Message.cpp: `return createFallbackJson();`
- out-of-range values logged with warnings
  > Message.cpp: `Serial.print("WARNING: Dust value out of typical range: ");`
- invalid readings filtered during averaging
  > NoiseSensor.cpp: `if (_samples[i] >= 30.0 && _samples[i] <= 130.0) { sum += _samples[i]; validSamples++; }`
- voltage range filter: 0.001-3.3v
  > NoiseSensor.cpp: `if (voltage > 0.001 && voltage < 3.3) { sum += voltage; validReadings++; }`
- default values: 30.0 db (sound), 0.0 micrograms/m³ (dust), 0.0 ppm (gas)
  > NoiseSensor.cpp: `return 30.0; // Minimum valid value in dB range` and DustSensor.cpp: `float finalDensity = max(density, 0.0f);`
- gas sensor anomaly detection: stuck readings, unrealistic rates of change
  > GasDataHandler.java: `if (consecutiveIdenticalReadings >= MAX_IDENTICAL_READINGS) { anomalyType = ProcessResult.ANOMALY_STUCK_READINGS; }`

## Fallback Behavior
- json creation failure → valid json with defaults
  > Message.cpp: `String Message::createFallbackJson() { StaticJsonDocument<200> fallbackDoc; ... }`
- invalid sound readings → 30.0 db
  > NoiseSensor.cpp: `if (validSamples == 0) { return 30.0; }`
- dust voltage below 0.6v → 0.0 micrograms/m³ (clean air)
  > DustSensor.cpp: `float finalDensity = max(density, 0.0f);`
- non-zero initial values prevent "zero value" display
  > BleHandler.cpp: `Message initialSoundMessage = Message(Message::SOUND_SENSOR_DATA, 40.0f);`
- gas sensor requires 10 second minimum warm-up time
  > ESP32 Gas Code: `delay(10000);  // Allow warm-up (10s minimum)`

## Compatibility
- exact device name and uuid matching
- consistent json structure
- notify property for realtime updates
- matching alert thresholds
- valid value ranges (30-130 db, 0-1000 micrograms/m³, 0-400 ppm)
- robust connection handling
- gas sensor requires calibration period after startup

## Code Structure
- main loop: connection detection, sensor reading, threshold checking, ble updates
  > smarthat-driver.ino: `void loop() { bool currentConnectionStatus = bleHandler.isDeviceConnected(); ... }`
- blehandler: manages ble service, characteristics, and notifications
  > BleHandler.cpp: `class BleHandler { ... }`
- message: creates and validates json messages
  > Message.cpp: `String Message::getJsonMessage() { ... }`
- dustsensor: handles dust sensor reading and conversion
  > DustSensor.cpp: `float DustSensor::readDustSensor() { ... }`
- noisesensor: handles sound sensor reading, averaging, and alert detection
  > NoiseSensor.cpp: `void NoiseSensor::update() { ... }`
- update frequency controlled by UPDATE_INTERVAL (1000ms)
  > smarthat-driver.ino: `const unsigned long UPDATE_INTERVAL = 1000;`

## Hardware Notification Implementation

### CCCD Setup
```cpp
// CCCD setup
BLE2902* descriptor = new BLE2902();
descriptor->setNotifications(true);
characteristic->addDescriptor(descriptor);
```
> BleHandler.cpp: `BLE2902* soundDescriptor = new BLE2902(); soundDescriptor->setNotifications(true); pSoundCharacteristic->addDescriptor(soundDescriptor);`

### Notification Sending
```cpp
// Update characteristic value and send notification
characteristic->setValue(jsonString.c_str());
characteristic->notify();
```
> BleHandler.cpp: `pSoundCharacteristic->setValue(jsonMessage.c_str()); pSoundCharacteristic->notify();`

## App Notification Implementation

### Enabling Notifications (Android Example)
```java
// Enable notifications
BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
gatt.writeDescriptor(descriptor);
```

### Handling Notifications (Android Example)
```java
@Override
public void onCharacteristicChanged(BluetoothGatt gatt, 
                                  BluetoothGattCharacteristic characteristic) {
    // Get the notification data
    byte[] data = characteristic.getValue();
    String jsonStr = new String(data);
    
    try {
        // Parse JSON string
        JSONObject json = new JSONObject(jsonStr);
        String messageType = json.getString("messageType");
        double value = json.getDouble("data");
        long timestamp = json.getLong("timeStamp");
        
        // Verify message type and handle accordingly
        if (messageType.equals("SOUND_SENSOR_DATA")) {
            // Handle sound data
            updateSoundDisplay(value);
            
            // Check alert threshold and duration
            if (value > 85.0) {
                trackHighNoise(timestamp);
            }
        } 
        else if (messageType.equals("DUST_SENSOR_DATA")) {
            // Handle dust data
            updateDustDisplay(value);
            
            // Check dust alert threshold
            if (value > 50.0) {
                showDustAlert();
            }
        }
    } catch (JSONException e) {
        Log.e("JSONParsing", "Failed to parse notification: " + e.getMessage());
        // Keep last valid reading
    }
}
``` 