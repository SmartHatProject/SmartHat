# smarthat android app 

## Table of Contents
- [What We've Completed](#what-weve-completed)
- [Our Architecture](#our-architecture)
- [Note for UI](#note-for-ui)
  - [Items to Keep Unchanged](#these-are-what-we-keep-unchanged-to-avoid-redundancy-inconsistency-duplications-and-bigger-issues)
  - [UI Development Guidelines](#when-building-ui-please)
  - [Using ViewBinding](#heres-how-to-use-viewbinding-in-activity)
  - [Accessing Functionality](#you-will-need)
  - [UI Tasks](#todo)
- [Project Structure](#project-structure)
- [Testing Your Changes](#testing-your-changes)

## what we've completed 
- **bluetooth connection** 
our BleConnectionManager is totally handled now after trying many different approaches in out git history. it handles discovery, connections, all android permission and we optimized it for 12+
- **data handling**
we have the full pipeline working, ble-> sensor model -> room database with good validations. we simulate using test mode
- **permissions** 
handled runtime permissions for ble, location services, and notifications 
- **notifications**  
our notification system is clean with channels that alert users when high readings, we might be able to improve this after we test with real mode
- **testing**  
our mock data systme 
- **basic ui** 
we have a smoothly functioning interface, but we will keep improving this

## our architecture 

we are using a modified modern mvm. we have tried the classic mvvm structure first (in earlier git history) but this works better for us
our layers : data, business logic, ui

1. **data layer**
   * sensordata model for readings & validation
   * room database for storage
   * databasehelper as our single db access point

2. **business logic layer**
   * bluetoothserviceintegration managing ble operations
   * bleconnectionmanager handling connection state
   * utility classes for permissions and other stuff

3. **ui layer**
   * activities as containers
   * adapters for recyclerviews
   * viewbinding connecting ui components

## note for ui 

### these are what we keep unchanged to avoid redundency, inconsistency, duplications and bigger issues

1. **ble code** (BleConnectionManager & BluetoothServiceIntegration)
   * took us trying multiple totally different approahed to get it right, we hand to change our whole architecture for it

2. **data models** (SensorData & DatabaseHelper)
   * contains important validation logic 
3. **permission flow** in MainActivity
   *sequenced for different android versions, this is a really tricky part we did tests and trials to get this 
4. **package structure & manifest permissions**
   * for us to follow the same structure we should not move files around and try understand the architecture were using to avoid mismatches 

### when building ui please:

* don't put business logic in ui components
* don't query the database directly always use databasehelper
* don't try to change any ble code to adjust it to your new code, if you encounter anny issues with ble code let's discuss it in the team meetings first
* use livedata for updates
* use viewbinding for all ui work to avoid bugs and runtime crashes use it instead of findviewById, its a part of our architecture
* create separate ui only components when possible

here's how to use viewbinding in activity


   ```java
   private ActivityMainBinding binding;
   
   @Override
   protected void onCreate(Bundle savedInstanceState) {
       super.onCreate(savedInstanceState);
       binding = ActivityMainBinding.inflate(getLayoutInflater());
       setContentView(binding.getRoot());
       
       // we can access views directly through binding
       binding.statusTextView.setText("Connected");
       binding.readingValueText.setText("25.4");
   }
   ```
e
 **when creating adapter views**:
   ```java
   // in onCreateViewHolder
   ItemSensorReadingBinding itemBinding = ItemSensorReadingBinding.inflate(
       LayoutInflater.from(parent.getContext()), parent, false);
   return new ViewHolder(itemBinding.getRoot(), itemBinding);
   ```


### you will need

1. **getting sensor data:**
   ```java
   // to get the singleton instance
   DatabaseHelper db = DatabaseHelper.getInstance();
   
   // get latest reading
   SensorData latest = db.getLatestReading("dust");
   
 **checking connection status:**
   ```java

   // check current state
   int state = BleConnectionManager.getInstance().getConnectionState();
   boolean isConnected = (state == BleConnectionManager.STATE_CONNECTED);
 
   ```
 **showing notifications:**
   ```java
   NotificationUtils.showAlert(context, title, message, priority);
   ```
**updating the ui:**
   ```java
   // observe livedata for updates
   db.getLatestReadingLiveData("dust").observe(this, reading -> {
       binding.dustValue.setText(String.valueOf(reading.getValue()));
   });
   ```

### todo

1. improving layouts NOTE: please don't rename existing view ids
2. adding screens for better ux
3. adding data visualizations, charts graphs and transitions
4. creating custom ui components (put them in a new views/ package)
5. handle text in strings.xml
6. creating customizable alert threshold settings

## project structure

```
app/
├─ src/
│  ├─ main/
│  │  ├─ java/com/team12/smarthat/
│  │  │  ├─ activities/         # ui screens
│  │  │  ├─ adapters/           # recyclerview adapters
│  │  │  ├─ bluetooth/          # ble communication
│  │  │  │  ├─ core/            # connection logic
│  │  │  │  ├─ devices/         # device specs
│  │  │  ├─ database/           # data storage
│  │  │  ├─ models/             # data models
│  │  │  ├─ permissions/        # permission handling
│  │  │  ├─ utils/              # helper functions
│  │  │  └─ AppController.java  # app entry point
│  │  ├─ res/                   # resources
│  │  └─ AndroidManifest.xml
│  └─ test/                     # unit tests
```



## testing your changes
for test run these in your terminal
```bash
#unit tesr
./gradlew test

#instrumented test
./gradlew connectedAndroidTest
```


