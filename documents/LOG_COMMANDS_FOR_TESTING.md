# SmartHat Log Commands for testing
=

### Basic Log Commands

```bash
# View all app logs
adb logcat | grep com.team12.smarthat


# Filter by main activity logs:
adb logcat -s MainActivity



# Filter by ble related logs:
adb logcat -s BleConnectionManager:* BluetoothServiceIntegration:* MockBleManager:*



# Filter by database operations
adb logcat -s Database

# Filter by permission related logs
adb logcat -s BluetoothPermissionManager
```

### Test Mode Logs

```bash
# Monitor test data generation
adb logcat -s TestDataGenerator

# Track mock BLE operations:
adb logcat -s MockBleManager

# Monitor both test data generation and mock BLE operations
adb logcat -s TestDataGenerator MockBleManager
```

### Sensor Data Logs

```bash
# Monitor all sensor data processing
adb logcat | grep "Sensor data displayed"

# Filter dust sensor readings only
adb logcat | grep "dust="

# Filter noise sensor readings only
adb logcat | grep "noise="

# Watch for alerts being triggered
adb logcat | grep "triggered notification"

# Monitor database operations
adb logcat | grep "Saved .* data to database"
```

### Connection State Logs

```bash
# Monitor connection state changes
adb logcat | grep "Connection state"

# Track connection attempts
adb logcat | grep "connect"

# Monitor disconnection events
adb logcat | grep "disconnect"
```

## Log tags will update later




## Monitoring test mode operations

### Test mode activation

```bash
# Monitor test mode activation
adb logcat | grep "Starting test mode"

# Monitor test mode deactivation
adb logcat | grep "Test mode stopped"

# Track test mode ui updates
adb logcat | grep "Updated test mode UI"
```

### Test Data Generation

```bash
# Monitor simulated characteristic changes
adb logcat | grep "Simulated .* characteristic change"

# Track all test data value changes
adb logcat | grep "Test data displayed"
```

## Debugging Permission Issues

```bash
# Monitor permission status
adb logcat | grep "Permission"

# Check for permission request dialogues
adb logcat | grep "permissions granted"
adb logcat | grep "permissions denied"
```

## Database Operations

```bash
# Track database insertions
adb logcat | grep "Saved .* data to database"

# Monitor database performance
adb logcat | grep "database in .* ms"

# Check for database maintenance operations
adb logcat | grep "Running database maintenance"
```

