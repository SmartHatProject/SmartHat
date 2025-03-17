# SmartHat BLE Configuration

## Device Info
- name: SmartHat
- service UUID: 12345678-1234-5678-1234-56789abcdef0

## Characteristics

### Sound Sensor
- UUID: abcd1234-5678-1234-5678-abcdef123456
- properties: read, notify
- format: JSON string
- range: 30-130 dB
- initial value: 40.0 dB (quiet room)
- example: `{"messageType":"SOUND_SENSOR_DATA","data":45.5,"timeStamp":1234567890}`

### Dust Sensor
- UUID: dcba4321-8765-4321-8765-654321fedcba
- properties: read, notify
- format: JSON string
- range: 0-1000 micrograms/m³
- initial value: 10.0 micrograms/m³ (clean air)
- example: `{"messageType":"DUST_SENSOR_DATA","data":25.5,"timeStamp":1234567890}`

## JSON Format
- messageType: "SOUND_SENSOR_DATA" or "DUST_SENSOR_DATA"
- data: float value in dB or micrograms/m³
- timeStamp: milliseconds since device boot

## Alert Thresholds
- dust: >50.0 micrograms/m³ (immediate alert)
- sound: >85.0 dB sustained for 4+ seconds

## Update Frequency
- readings update every ~1 second
- notifications sent when connected
- initial values sent immediately on connection
- auto-resume on reconnection

## ESP32 Implementation
- multiple readings averaged for stability
- raw values converted to standard units
- JSON validation with fallback values
- MTU request: 512 bytes
- detailed logging for troubleshooting

## Android Implementation
- scan for "SmartHat" device
- connect to service UUID
- subscribe to notifications
- parse JSON for display and alerts
- request maximum MTU after connection

## Sensor Details

### Dust Sensor
- IR LED control: active-low
- sampling window: 280µs
- formula: ((voltage - 0.6) / 0.5) * 100.0 = micrograms/m³
- clean air threshold: 0.6V
- 5 readings averaged

### Sound Sensor
- formula: 20.0 * log10(voltage / 0.01) + 40.0 = dB
- 5 readings averaged
- range clamped: 30.0-130.0 dB
- alert after 4 seconds above threshold

## Error Handling
- JSON validation with fallbacks
- out-of-range values logged with warnings
- invalid readings filtered during averaging
- voltage range filter: 0.001-3.3V
- default values: 30.0 dB (sound), 0.0 micrograms/m³ (dust)

## Fallback Behavior
- JSON creation failure → valid JSON with defaults
- invalid sound readings → 30.0 dB
- dust voltage below 0.6V → 0.0 micrograms/m³ (clean air)
- non-zero initial values prevent "zero value" display

## Compatibility
- exact device name and UUID matching
- consistent JSON structure
- notify property for real-time updates
- matching alert thresholds
- valid value ranges
