# SmartHat Instrumented Tests

This directory contains instrumented tests for the SmartHat application. These tests run on an actual Android device or emulator and test real interactions with the system, including Bluetooth functionality.

## Test Organization

The tests are organized by component:

- `activities/MainActivityTest.java` - Tests for the main UI and its interaction with Bluetooth

## Running the Tests

To run the instrumented tests:

```
./gradlew connectedAndroidTest
```

### Running on a Specific Device

To run on a specific connected device:

```
./gradlew connectedAndroidTest -PtestDevice=<DEVICE_ID>
```

Where `<DEVICE_ID>` is the ID from `adb devices`.

## Test Requirements

These tests require:

1. A device or emulator running Android 12 (API 31) or higher
2. Bluetooth hardware support
3. Bluetooth enabled on the device
4. Required permissions granted (handled automatically in the tests)
5. Optional: An ESP32 device nearby for connection tests

## Test Structure

The instrumented tests use:

- **Espresso** for UI interaction and verification
- **ActivityScenario** for launching and controlling activities
- **GrantPermissionRule** for automatically granting permissions

## Known Limitations

1. **ESP32 Dependency** - Some tests may fail if an ESP32 device is not available for connection
2. **Bluetooth Timing** - Bluetooth operations may have different timing on different devices, causing test flakiness
3. **Permission Handling** - On some devices, permission grants may require user interaction

## Test Categories

### UI Tests

These tests verify that the UI components are displayed correctly and respond to user interaction as expected.

### Connection Tests

These tests verify that the Bluetooth connection functionality works correctly, including scanning, connecting, and handling disconnect events.

### Sensor Data Tests

These tests verify that sensor data is properly received, parsed, and displayed in the UI.

## Debugging Tests

To get more detailed logs during test execution:

```
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.debug=true
```

## Adding New Tests

When adding new instrumented tests:

1. Place them in the appropriate package corresponding to the component being tested
2. Use the AndroidJUnit4 runner
3. Handle permissions using GrantPermissionRule
4. Consider the timing and stability of Bluetooth operations
5. Use idling resources for asynchronous operations 