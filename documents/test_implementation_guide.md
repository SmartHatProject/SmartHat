# smarthat test implementation 


## what we have right now

we currently have one main test class implemented - `NotificationHandlingTest.java` which tests how our app processes sensor data notifications sent over ble we check:

- basic sound sensor notification handling
- basic dust sensor notification handling
- fallback behavior when we receive invalid json
- proper handling of timestamps

all notification handling tests are passing! however, we still have a few failing tests in other classes that we will be working on , we think it might be related to mockito and other config

- `BleConnectionManagerTest`: 6 tests failing with NullPointerException
- `BleOperationQueueTest`: 1 test failing with AssertionError 
- `BluetoothServiceIntegrationTest`: 4 test failing with WantedButNotInvoked exceptions

# test mode ; on app

## helpful commands

to view test logs:
```
adb logcat -s "SmartHat:*" "MockBLE:*"
```

to run tests:
```
./gradlew test                               # run all tests
./gradlew test --tests NotificationHandlingTest  # run specific test class
```

## todo

we need to fix the 11 failing tests before adding more features. most issues appear to be related to mock object setup or missing interface implementation

todo
1. fix BleConnectionManagerTest NullPointerExceptions
2. address BluetoothServiceIntegrationTest WantedButNotInvoked issue
3. resolve the BleOperationQueueTest thread safety assertion error 