# Threshold History Filtering Feature 





enhancing UX of our SmartHat app with filtering capabilities for Threshold History screen, helps the to analyze specific patterns or issues 

## features 

Currently our app displays all threshold breaches in a simple list in the ThresholdHistoryActivity we like to add a user friendly filtering system so users can narrow down the history based on:

- Time periods (like "Last 24 hours", "Last week", or custom date ranges)
- Sensor readings (min value)
- Sensor types (dust, noise)
- ???



## Importance
We are recording so many sensor readings in treshold history, without this feature it will be kind of useless (someone needs to teach me a more polite word for this word I ues it alot in a not rude way). 



## You will need



1. to create a new branch from our main branch

2. You will need these files
   - `ThresholdHistoryActivity.java`
   - `activity_threshold_history.xml`
   - `item_threshold_breach.xml`
   - `database` 

## Steps

### Step 1: Design the filter UI

First you can update layout files to include filter controls. Consider using:

- a filter panel at the top (expandable maybe)
- Date range picker (ou can use Android's DatePicker or a custom one)
- min (maybe max too but why?) value sliders for sensor readings
- Sensor type checkbox
- reset filter button
- maybe visual chips showing active filters 

don't worry about functionality for this step first just get the UI looking good and matching our app's design language(you will know by checking the files I mentioned earlier)

##step 2: Create the data structures 
When ui is done create the classes to manage filters create a model to represent all filter options:

```java
public class ThresholdFilter {
    private Date startDate;
    private Date endDate;
    private float minValue;
    private float maxValue;
    private List<String> sensorTypes;
    private int severityLevel;

    // add getters, setters, and a builder pattern if you like
}

// create a manager class to handle filter operations
public class FilterManager {
    // use methods to update filters and track state
}
```

### Step 3: Connect to our data layer 

we'll need to extend our database query capabilities to support this filtering:


1. look at `DatabaseHelper.java` to see how we currently fetch threshold data
2. Add new methods that accept filter parameters 
3. Make sure to maintain our LiveData pattern for real time updates

NOTE!!!
Don't modify the existing db structure just add new query methods

### Step 4: Update ThresholdHistoryActivity

we need to connect ui elements to your filter logic:

1. add listeners to all your filter ui elements
2. When filters change update your FilterManager
3. 0bserve changes to the filter and refresh the data
4. Update the RecyclerView adapter to display filtered results
5. Make sure to handle empty states with something like ("No matching results found")

###Step 5: final touch

1. save filter preferences when the user exits the activity
2. restore their last used filters when they return 
3. Implement proper loading states when queries are running 

### Step 6: test

we can test this with our test mode readings that are already being recorded. 


current app uses constants for the threshold values (check Constants.java for DUST_THRESHOLD and NOISE_THRESHOLD). when implementing new query methods in DatabaseHelper.java we might need to add a "WHERE" clause that can handle empty filter params. also, our SensorData model already has timeStamps as unix timeStamps (milliseconds), so date filtering should be pretty straightforward with simple comparisons DatePicker converts to the same format.  we're generating lots of test data in test mode, so definitely test with that using the LOG_COMMANDS_FOR_TESTING.md guide to watch the debug logs. test mode adds "TEST" as the source field in SensorData so we might want to add that as a filter option too.








