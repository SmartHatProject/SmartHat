# SmartHat Bluetooth Unit Testing

This directory contains unit tests for the core Bluetooth components of the SmartHat application. These tests are designed to verify the functionality, reliability, and performance of the Bluetooth implementation for Android 12 on Pixel 4a devices connecting to ESP32 peripherals.

## Test Organization

The tests are organized to match the structure of the main codebase:

- `BleConnectionManagerTest.java` - Tests for the BLE connection management functionality
- `BluetoothServiceIntegrationTest.java` - Tests for the sensor data processing and notification handling
- `BleOperationQueueTest.java` - Tests for the operation queue that ensures thread safety

## Testing Approach

These tests follow a comprehensive testing strategy that focuses on:

1. **State Management** - Testing proper state transitions and error handling
2. **Thread Safety** - Testing behavior under concurrent access and with different threading models
3. **Error Resilience** - Testing recovery from failures and proper resource cleanup
4. **Lifecycle Management** - Testing proper resource initialization and cleanup
5. **Callback Handling** - Testing proper callback registration, unregistration, and invocation

The tests use:
- **Robolectric** for simulating Android framework behavior
- **Mockito** for mocking dependencies and verifying interactions
- **JUnit4** for test structure and assertions

## Running the Tests

To run all tests:

```
./gradlew testDebugUnitTest
```

To run a specific test class:

```
./gradlew testDebugUnitTest --tests "com.team12.smarthat.bluetooth.core.BleConnectionManagerTest"
```

## Mock Structure

These tests use careful mocking of Android Bluetooth components:

1. **BluetoothManager/Adapter** - Mocked to avoid system dependencies
2. **BluetoothGatt** - Mocked to simulate connections and service discovery
3. **BluetoothGattService/Characteristic** - Mocked to simulate sensor data
4. **Handler/Looper** - Shadowed by Robolectric for testing threading behavior

## Key Testing Patterns

1. **Argument Captors**
   - Used to capture callbacks registered with dependencies
   - Enables triggering of callbacks to simulate system responses

2. **Lifecycle Simulation**
   - Tests create a fake LifecycleOwner to test LiveData observers
   - Enables testing of lifecycle-aware components

3. **Thread Safety Testing**
   - Tests run operations concurrently to verify thread safety
   - Use CountDownLatches to synchronize across threads

4. **State Transition Testing**
   - Tests verify proper state machine behavior
   - Ensure recovery from invalid states

## Extending Tests

When adding new functionality, follow these guidelines:

1. **Test Isolation** - Each test should be independent and not rely on other test execution
2. **Clear Arrange-Act-Assert** - Structure tests with clear setup, action, and verification
3. **Test Edge Cases** - Include tests for error conditions and boundary values
4. **Document Test Purpose** - Include clear comments explaining what aspect is being tested

## Android Instrumented Tests

For tests requiring actual device interaction, see the androidTest directory which contains instrumented tests that run on a device or emulator.

## ESP32 Mock Testing

For simulating ESP32-specific behavior, the tests use preset characteristic values that match the expected data format from the ESP32 firmware. 