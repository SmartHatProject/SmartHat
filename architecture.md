# smarthat architecture

we have layered architecture with the following components:

```
┌───────────────────┐
│ ui (activities/   │
│ fragments)        │
└─────────┬─────────┘
          │
┌─────────▼─────────┐
│ service layer     │
│ (ble integration) │
└─────────┬─────────┘
          │
┌─────────▼─────────┐
│ data management   │
│ (repository)      │
└─────────┬─────────┘
          │
┌─────────▼─────────┐
│ local storage     │
│ (room database)   │
└───────────────────┘
```

## key components

### ui layer

- **mainactivity**: centralized for sensor data display and device connection
- **settingsactivity**: manages application and sensor threshold settings
- **thresholdhistoryactivity**: displays historical sensor data exceeding thresholds

### service layer

- **bluetoothserviceintegration**: handles communication with ble device (smarthat/esp32)
- **bleconnectionmanager**: manages ble connection lifecycle
- **esp32bluetoothspec**: defines esp32 specific ble characteristics and uuids 

### data layer

- **sensordata**: model representing sensor readings
- **databasehelper**: database access interface
- **sensordatadao**: data access object for sensor readings
- **sensordatabase**: room database config

### utility layer

- **testdatagenerator**: simulates sensor data for testing
- **notificationutils**: manages notification display
- **constants**: constants and thresholds
- **loghelper**: centralized logging utility
- **datafilterhelper**: filters historical data

## design patterns

1. **singleton pattern**: used for database access and connection management
2. **observer pattern**: implemented using livedata for ui updates
3. **repository pattern**: data operations from the ui
4. **factory pattern**: creates sensor data objects
5. **strategy pattern**: different notification strategies based on sensor types

## data flow

1. sensor data is received via ble notifications from the esp32 device
2. data is parsed into sensordata objects
3. values are validated and checked against thresholds
4. ui is updated with readings
5. data exceeding thresholds trigger notifications
6. all readings are stored in the local database
7. historical data can be filtered and displayed

## testing strategy

- **unit tests**: test individual components (sensordata, threshold validation)
- **integration tests**: test ble notification handling
- **mock testing**: test mode with simulated data for development without hardware

## security considerations

- bluetooth permissions following android 12+ requirements
- data validation to prevent sensor value manipulation
- secure local storage using room database

## future expansion

the architecture supports future expansion through:
- modular ble device support (currently esp32-specific)
- additional sensor types
- cloud synchronization
- enhanced data analytics 