/**
 * Protocol 
 */

#include <BluetoothSerial.h>
#include <ArduinoJson.h>
#include "Message.h"

// Bluetooth communication
BluetoothSerial SerialBT;

// Pin Definitions (to be filled out)
// #define SENSOR1_PIN 34
// #define SENSOR2_PIN 35
// ... add more as needed

// Function prototypes
void setupSensors();
void setupActuators();
void processSensorData();
void handleIncomingMessages();

// Sensor reading functions
float readDustSensor();
float readSoundSensor();
float readTemperatureSensor();
float readHumiditySensor();
float readVibrationSensor();

// Actuator control functions
void controlLed(float value);
void controlMotor(float value);
void sendSystemStatus();

void setup() {
    // Initialize serial communication
    Serial.begin(115200);
    SerialBT.begin("SmartHat");
    
    // Initialize sensors and actuators
    setupSensors();
    setupActuators();
    
    Serial.println("SmartHat protocol initialized and ready");
}

void loop() {
    // Process sensor data and send messages
    processSensorData();
    
    // Handle incoming command messages
    handleIncomingMessages();
    
    // Delay to control loop frequency
    delay(100);  // Adjust as needed
}

void setupSensors() {
    // Initialize sensor pins and configurations
    // pinMode(SENSOR1_PIN, INPUT);
    // ... configure other sensors
}

void setupActuators() {
    // Initialize actuator pins and configurations
    // pinMode(ACTUATOR1_PIN, OUTPUT);
    // ... configure other actuators
}

void processSensorData() {
    // Read sensor data periodically and send messages
    // Example:
    // float dustValue = readDustSensor();
    // if (dustValue > threshold) {
    //     Message dustMessage(Message::DUST_DATA_MESSAGE, dustValue);
    //     serializeJson(dustMessage.getJsonMessage(), SerialBT);
    //     SerialBT.println();
    // }
}

void handleIncomingMessages() {
    // Check for and process incoming messages
    if (SerialBT.available()) {
        String jsonString = SerialBT.readStringUntil('\n');
        
        // Parse the message
        Message message = Message::parseMessage(jsonString.c_str());
        
        // Handle different message types
        if (message.getMessageType() == Message::LED_CONTROL_MESSAGE) {
            controlLed(message.getData());
        } 
        else if (message.getMessageType() == Message::MOTOR_CONTROL_MESSAGE) {
            controlMotor(message.getData());
        }
        else if (message.getMessageType() == Message::SYSTEM_STATUS_REQUEST) {
            sendSystemStatus();
        }
    }
}

// Implement sensor reading functions
float readDustSensor() {
    // TODO: Implement dust sensor reading logic
    return 0.0;
}

float readSoundSensor() {
    // TODO: Implement sound sensor reading logic
    return 0.0;
}

float readTemperatureSensor() {
    // TODO: Implement temperature sensor reading logic
    return 0.0;
}

float readHumiditySensor() {
    // TODO: Implement humidity sensor reading logic
    return 0.0;
}

float readVibrationSensor() {
    // TODO: Implement vibration sensor reading logic
    return 0.0;
}

// Implement actuator control functions
void controlLed(float value) {
    // TODO: Implement LED control logic
    Serial.print("LED control value: ");
    Serial.println(value);
}

void controlMotor(float value) {
    // TODO: Implement motor control logic
    Serial.print("Motor control value: ");
    Serial.println(value);
}

void sendSystemStatus() {
    // TODO: Implement system status reporting
    Message statusMessage(Message::SYSTEM_STATUS_RESPONSE, 1.0);  // 1.0 = OK status
    serializeJson(statusMessage.getJsonMessage(), SerialBT);
    SerialBT.println();
}
