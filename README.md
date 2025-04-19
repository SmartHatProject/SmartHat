# smarthat

an android application that interfaces with a smart hardhat device to monitor environmental hazards through bluetooth low energy connectivity.

## what it does

smarthat connects to an esp32-based sensor system to monitor:
- dust particles (μg/m³)
- noise levels (db)
- gas concentration (ppm)

alerts are triggered when sensor readings exceed safety thresholds defined by osha standards.

## getting started

### requirements
- android studio arctic fox or newer
- android sdk 31 (android 12) or higher
- java 17

### building
1. clone the repository
   ```
   git clone https://github.com/SmartHatProject/SmartHat.git
   ```
2. open the project in android studio
3. build the project
   ```
   ./gradlew assembleDebug
   ```

### testing
run the tests with:
```
./gradlew test
```

## features
- monitoring environmental hazards
- bluetooth low energy (ble) connection to sensor hardware (esp 32 board)
- customizable alert thresholds
- historical data tracking

