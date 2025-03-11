# SmartHat Unit Test Results



```
> Task :app:testDebugUnitTest
- 20 tests completed
- 11 tests failed
- 9 tests passed
```

### Failed Tests

1. `BleConnectionManagerTest` tests:
   - `connect_setsUpGattCallback` - NullPointerException at line 148
   - `addConnectionListener_withSameDeviceAlreadyConnected_doesNotReconnect` - NullPointerException at line 148
   - `setCharacteristicChangeListener_withValidListener_notifiesOnChanges` - NullPointerException at line 148
   - `onConnectionStateChange_connected_callsDiscoverServices` - NullPointerException at line 148
   - `disconnect_whenConnected_closesGatt` - NullPointerException at line 148
   - `onCharacteristicChanged_withNoListener_doesNotCrash` - NullPointerException at line 148

2. `BleOperationQueueTest` tests:
   - `concurrentAccess_maintainsThreadSafety` - AssertionError at line 252

3. `BluetoothServiceIntegrationTest` tests:
   - `onCharacteristicChanged_dustData_parsesCorrectly` - WantedButNotInvoked at line 195
   - `addSensorDataListener_registersListener` - WantedButNotInvoked at line 249
   - `onCharacteristicChanged_noiseData_parsesCorrectly` - WantedButNotInvoked at line 215
   - `multipleListeners_allReceiveUpdates` - WantedButNotInvoked at line 284

